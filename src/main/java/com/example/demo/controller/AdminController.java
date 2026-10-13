package com.example.demo.controller;

import com.example.demo.model.Meeting;
import com.example.demo.model.Visitor;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.MeetingRepository;
import com.example.demo.repository.VisitorRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private VisitorRepository visitorRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private com.example.demo.repository.IdleIncidentRepository idleIncidentRepository;

    @Autowired
    private com.example.demo.repository.PayrollRepository payrollRepository;

    @Autowired
    private com.example.demo.repository.IssueRepository issueRepository;

    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)
    public String adminDashboard(Model model, HttpSession session) {
        // Check if user is logged in
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        // Get visitor statistics
        List<Visitor> allVisitors = visitorRepository.findAllOrderByCheckInTimeDesc();
        List<Visitor> checkedInVisitors = visitorRepository.findByStatus("Checked In");
        List<Visitor> checkedOutVisitors = visitorRepository.findByStatus("Checked Out");

        // Get employee statistics
        List<com.example.demo.model.Employee> presentEmployees = employeeRepository.findCurrentlyPresentEmployees();
        List<com.example.demo.model.Employee> allEmployees = employeeRepository.findAll();
        long totalEmployees = allEmployees.size();
        long presentCount = presentEmployees.size();
        long absentCount = totalEmployees - presentCount;

        // Get meeting statistics
        List<Meeting> ongoingMeetings = meetingRepository.findOngoingMeetings();
        List<Meeting> allMeetings = meetingRepository.findAll();

        model.addAttribute("userName", session.getAttribute("userName"));

        // Visitor stats
        model.addAttribute("allVisitors", allVisitors);
        model.addAttribute("checkedInVisitors", checkedInVisitors);
        model.addAttribute("checkedOutVisitors", checkedOutVisitors);
        model.addAttribute("totalVisitors", allVisitors.size());
        model.addAttribute("currentlyIn", checkedInVisitors.size());
        model.addAttribute("totalCheckedOut", checkedOutVisitors.size());

        // Employee stats
        model.addAttribute("presentEmployees", presentEmployees);
        model.addAttribute("allEmployees", allEmployees);
        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("presentCount", presentCount);
        model.addAttribute("absentCount", absentCount);

        // Meeting stats
        model.addAttribute("ongoingMeetings", ongoingMeetings);
        model.addAttribute("totalMeetings", allMeetings.size());
        model.addAttribute("ongoingCount", ongoingMeetings.size());

        // Get idle employees
        List<com.example.demo.model.IdleIncident> activeIdleIncidents = idleIncidentRepository.findActiveIncidents();
        model.addAttribute("idleIncidents", activeIdleIncidents);
        model.addAttribute("idleCount", activeIdleIncidents.size());

        // Issue stats (integration)
        List<com.example.demo.model.Issue> allIssues = issueRepository.findAllWithDetails();
        long openIssuesCount = allIssues.stream().filter(i -> !"DONE".equals(i.getStatus()) && !"CLOSED".equals(i.getStatus())).count();
        model.addAttribute("totalIssues", allIssues.size());
        model.addAttribute("openIssuesCount", openIssuesCount);

        return "admin-dashboard";
    }

    @RequestMapping(value = "/payroll", method = RequestMethod.GET)
    public String adminPayroll(Model model, HttpSession session) {
        // Check if user is logged in
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        List<com.example.demo.model.Payroll> allPayrolls = payrollRepository.findAll();
        List<com.example.demo.model.Payroll> processedPayrolls = payrollRepository
                .findByPayrollStatus(com.example.demo.model.Payroll.PayrollStatus.APPROVED);

        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("payrolls", allPayrolls); // Using allPayrolls for the list as per typical admin view, but
                                                     // user image shows specific rows. The user image shows "Payroll
                                                     // History", implying all.
        model.addAttribute("processedCount", processedPayrolls.size());

        return "admin-payroll";
    }

    @RequestMapping(value = "/api/employees", method = RequestMethod.GET)
    @org.springframework.web.bind.annotation.ResponseBody
    public List<com.example.demo.model.Employee> getAllEmployees() {
        // Return only active employees
        return employeeRepository.findAll()
                .stream()
                .filter(emp -> emp.getIsActive() == null || emp.getIsActive())
                .collect(java.util.stream.Collectors.toList());
    }

    @RequestMapping(value = "/api/employees/{id}", method = RequestMethod.GET)
    @org.springframework.web.bind.annotation.ResponseBody
    public com.example.demo.model.Employee getEmployeeDetails(
            @org.springframework.web.bind.annotation.PathVariable Long id) {
        return employeeRepository.findById(id).orElse(null);
    }

    @RequestMapping(value = "/api/payroll/base-salary/{employeeId}", method = RequestMethod.GET)
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> getBaseSalaryFromPayroll(
            @org.springframework.web.bind.annotation.PathVariable Long employeeId,
            HttpSession session) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        // Check if user is logged in (admin should have access)
        if (session.getAttribute("user") == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return response;
        }

        try {
            java.util.Optional<com.example.demo.model.Employee> employeeOpt = employeeRepository.findById(employeeId);
            if (!employeeOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "Employee not found");
                return response;
            }

            com.example.demo.model.Employee employee = employeeOpt.get();

            // Prefer the salary stored on the employee profile (set by Admin)
            Double employeeSalary = employee.getSalary();
            if (employeeSalary != null && employeeSalary > 0) {
                response.put("success", true);
                response.put("baseSalary", employeeSalary);
                response.put("employeeName", employee.getName());
                response.put("employeeId", employee.getEmployeeId());
                return response;
            }

            // Fallback: use the most recent payroll record if available
            List<com.example.demo.model.Payroll> payrolls = payrollRepository
                    .findByEmployeeOrderByYearDescMonthDesc(employee);

            if (payrolls != null && !payrolls.isEmpty()) {
                // Get the most recent payroll
                com.example.demo.model.Payroll latestPayroll = payrolls.get(0);
                response.put("success", true);
                response.put("baseSalary", latestPayroll.getGrossSalary()); // Using Gross as Base for now
                response.put("employeeName", employee.getName());
                response.put("employeeId", employee.getEmployeeId());
            } else {
                response.put("success", false);
                response.put("message", "No payroll record found and employee has no base salary set");
                response.put("baseSalary", 0);
            }
        } catch (Exception e) {
            System.err.println("Error fetching base salary: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }

        return response;
    }

    @RequestMapping(value = "/payroll/save", method = RequestMethod.POST)
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, Object> savePayroll(
            @org.springframework.web.bind.annotation.RequestParam Long employeeId,
            @org.springframework.web.bind.annotation.RequestParam Double baseSalary) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        try {
            com.example.demo.model.Employee employee = employeeRepository.findById(employeeId).orElse(null);
            if (employee == null) {
                response.put("success", false);
                response.put("message", "Employee not found");
                return response;
            }

            // Persist the admin-set base salary on the employee profile so HR can only read
            // it
            employee.setSalary(baseSalary);
            employeeRepository.save(employee);

            com.example.demo.model.Payroll payroll = new com.example.demo.model.Payroll();
            payroll.setEmployee(employee);
            payroll.setGrossSalary(baseSalary);
            payroll.setNetPay(baseSalary); // Initially net = gross
            payroll.setTotalDeductions(0.0);
            payroll.setLopDeduction(0.0);

            // Set default pay period (Current Month)
            java.time.LocalDate now = java.time.LocalDate.now();
            payroll.setMonth(now.getMonthValue());
            payroll.setYear(now.getYear());

            payroll.setPayrollStatus(com.example.demo.model.Payroll.PayrollStatus.GENERATED);

            payrollRepository.save(payroll);

            response.put("success", true);
            response.put("message", "Payroll saved successfully");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error saving payroll: " + e.getMessage());
        }
        return response;
    }

    @Autowired
    private com.example.demo.repository.UserRepository userRepository;

    @RequestMapping(value = "/register/hr", method = RequestMethod.GET)
    public String showHrRegistrationForm(Model model, HttpSession session) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", new com.example.demo.model.User());
        model.addAttribute("role", "HR");
        return "admin-register-hr";
    }

    @RequestMapping(value = "/register/hr", method = RequestMethod.POST)
    public String registerHr(@org.springframework.web.bind.annotation.ModelAttribute com.example.demo.model.User user,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes,
            HttpSession session) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        // Check if email already exists
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Email already exists!");
            return "redirect:/admin/register/hr";
        }

        user.setRole("HR");
        // In a real app, hash the password here
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("success", "HR registered successfully!");
        return "redirect:/admin/dashboard";
    }

    @RequestMapping(value = "/register/manager", method = RequestMethod.GET)
    public String showManagerRegistrationForm(Model model, HttpSession session) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", new com.example.demo.model.User());
        model.addAttribute("role", "MANAGER");
        return "admin-register-manager";
    }

    @RequestMapping(value = "/register/manager", method = RequestMethod.POST)
    public String registerManager(
            @org.springframework.web.bind.annotation.ModelAttribute com.example.demo.model.User user,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes,
            HttpSession session) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        // Check if email already exists
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Email already exists!");
            return "redirect:/admin/register/manager";
        }

        user.setRole("MANAGER");
        // In a real app, hash the password here
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("success", "Manager registered successfully!");
        return "redirect:/admin/dashboard";
    }
}
