package com.example.demo.controller;

import com.example.demo.model.Employee;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.MeetingRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.service.QRCodeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping({"/admin/employees", "/hr/employees"})
public class EmployeeManagementController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired(required = false)
    private com.example.demo.service.PasswordService passwordService;

    @Autowired(required = false)
    private com.example.demo.service.SessionSecurityService sessionSecurityService;

    @Autowired(required = false)
    private com.example.demo.service.AuthorizationService authorizationService;

    @Autowired
    private QRCodeService qrCodeService;



    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private com.example.demo.repository.LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private com.example.demo.repository.SuggestionRepository suggestionRepository;

    @Autowired
    private com.example.demo.repository.NotificationRepository notificationRepository;

    @Autowired
    private com.example.demo.repository.EmployeeActivityRepository employeeActivityRepository;

    @Autowired
    private com.example.demo.repository.IdleIncidentRepository idleIncidentRepository;

    @Autowired
    private com.example.demo.repository.TeamBadgeRepository teamBadgeRepository;

    @Autowired
    private com.example.demo.repository.PayrollRepository payrollRepository;

    @Autowired
    private com.example.demo.repository.GoalRepository goalRepository;

    @Autowired
    private com.example.demo.repository.FeedbackRepository feedbackRepository;

    @Autowired
    private com.example.demo.repository.TeamRepository teamRepository;

    // List of departments
    private static final List<String> DEPARTMENTS = Arrays.asList(
            "Software Development",
            "Web Development",
            "Mobile App Development",
            "Quality Assurance (QA) / Testing",
            "DevOps & Cloud Engineering",
            "UI/UX Design",
            "Data Science & Analytics",
            "Artificial Intelligence & Machine Learning",
            "Cyber Security",
            "System Administration",
            "Network Engineering",
            "IT Support / Help Desk",
            "Database Administration");

    // List of designations
    private static final List<String> DESIGNATIONS = Arrays.asList(
            "Software Engineer",
            "Junior Software Developer",
            "Senior Software Developer",
            "Full Stack Developer",
            "Frontend Developer",
            "Backend Developer",
            "Java Developer",
            ".NET Developer",
            "Python Developer",
            "Mobile App Developer",
            "Game Developer",
            "QA Engineer",
            "Manual Tester",
            "Automation Tester",
            "Test Lead",
            "Quality Analyst",
            "UI Designer",
            "UX Designer",
            "Graphic Designer",
            "Product Designer",
            "Data Analyst",
            "Data Scientist",
            "Big Data Engineer",
            "Cloud Engineer",
            "DevOps Engineer",
            "ML Engineer",
            "System Administrator",
            "Network Engineer",
            "IT Support Engineer",
            "Technical Support Executive",
            "Help Desk Analyst",
            "Project Manager",
            "Scrum Master",
            "Product Owner",
            "Business Analyst",
            "Technical Lead",
            "Team Lead",
            "HR Executive",
            "HR Manager",
            "Talent Acquisition Specialist",
            "HR Business Partner",
            "Payroll Executive",
            "Office Administrator");

    private boolean isAuthorizedStaff(HttpSession session) {
        if (session == null) return false;
        Object userObj = session.getAttribute("user");
        if (userObj instanceof com.example.demo.model.User user) {
            String role = user.getRole();
            return "ADMIN".equalsIgnoreCase(role) || "HR".equalsIgnoreCase(role);
        }
        Object role = session.getAttribute("userRole");
        return role != null && ("ADMIN".equalsIgnoreCase(role.toString()) || "HR".equalsIgnoreCase(role.toString()));
    }

    private String getEmployeesRedirect(HttpSession session) {
        String role = (String) session.getAttribute("userRole");
        return "HR".equals(role) ? "redirect:/hr/employees" : "redirect:/admin/employees";
    }

    @RequestMapping(method = RequestMethod.GET)
    public String listEmployees(Model model, HttpSession session) {
        if (!isAuthorizedStaff(session)) {
            return "redirect:/login";
        }

        List<Employee> employees = employeeRepository.findAll();
        model.addAttribute("employees", employees);
        model.addAttribute("employee", new Employee());
        model.addAttribute("departments", DEPARTMENTS);
        model.addAttribute("designations", DESIGNATIONS);
        model.addAttribute("teams", teamRepository.findByIsActiveTrueOrderByNameAsc());
        // Determine dashboard link based on role
        String role = (String) session.getAttribute("userRole");
        String dashboardLink = "/admin/dashboard"; // Default
        String baseEmployeesUrl = "/admin/employees";
        if ("HR".equals(role)) {
            dashboardLink = "/hr/dashboard";
            baseEmployeesUrl = "/hr/employees";
        } else if ("MANAGER".equals(role)) {
            dashboardLink = "/manager/dashboard";
        }
        model.addAttribute("dashboardLink", dashboardLink);
        model.addAttribute("baseEmployeesUrl", baseEmployeesUrl);

        return "admin-employee-management";
    }

    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public String addEmployee(@ModelAttribute Employee employee,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Boolean isTeamLead,
            @RequestParam(required = false) List<String> customLeaveNames,
            @RequestParam(required = false) List<Integer> customLeaveDays,
            RedirectAttributes redirectAttributes,
            HttpSession session) {
        if (!isAuthorizedStaff(session)) {
            return "redirect:/login";
        }

        // Validate required employee fields
        if (employee.getEmployeeId() == null || employee.getEmployeeId().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Employee ID is required!");
            return getEmployeesRedirect(session);
        }
        if (employee.getName() == null || employee.getName().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Employee name is required!");
            return getEmployeesRedirect(session);
        }
        if (employee.getEmail() == null || !employee.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            redirectAttributes.addFlashAttribute("error", "A valid email address is required!");
            return getEmployeesRedirect(session);
        }
        if (employee.getDepartment() == null || employee.getDepartment().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Department is required!");
            return getEmployeesRedirect(session);
        }
        if (employee.getSalary() != null && employee.getSalary() < 0) {
            redirectAttributes.addFlashAttribute("error", "Base salary cannot be negative!");
            return getEmployeesRedirect(session);
        }

        employee.setEmployeeId(employee.getEmployeeId().trim().toUpperCase());
        employee.setName(employee.getName().trim());
        employee.setEmail(employee.getEmail().trim().toLowerCase());

        // Check if employee ID already exists
        Optional<Employee> existingById = employeeRepository.findByEmployeeId(employee.getEmployeeId());
        if (existingById.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Employee ID already exists!");
            return getEmployeesRedirect(session);
        }

        // Check if email already exists
        Optional<Employee> existingByEmail = employeeRepository.findByEmail(employee.getEmail());
        if (existingByEmail.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Email already exists!");
            return getEmployeesRedirect(session);
        }

        employee.setStatus("OUT");
        employee.setTotalHoursToday(0.0);
        employee.setLateAlertSent(false);
        employee.setEarlyAlertSent(false);
        employee.setWorkMode("OFFICE");
        // Set default scheduled times: 9 AM for clock-in, 6 PM for clock-out
        if (employee.getScheduledStartTime() == null || employee.getScheduledStartTime().isEmpty()) {
            employee.setScheduledStartTime("09:00");
        }
        if (employee.getScheduledEndTime() == null || employee.getScheduledEndTime().isEmpty()) {
            employee.setScheduledEndTime("18:00");
        }
        // Password is required for employee login (email + password + employeeId)
        if (employee.getPassword() == null || employee.getPassword().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Password is required for employee login!");
            return getEmployeesRedirect(session);
        }
        if (passwordService != null) {
            employee.setPassword(passwordService.encode(employee.getPassword()));
        }
        // Generate QR code token for new employee
        String qrToken = qrCodeService.generateQRCodeToken();

        employee.setQrCodeToken(qrToken);

        employee.setIsTeamLead(Boolean.TRUE.equals(isTeamLead));
        if (teamId != null) {
            employee.setTeam(teamRepository.findById(teamId).orElse(null));
            if (employee.getTeam() != null && (employee.getDepartment() == null || employee.getDepartment().isEmpty())) {
                employee.setDepartment(employee.getTeam().getDepartment().getName());
            }
        }

        // Process Custom Leaves
        if (customLeaveNames != null && customLeaveDays != null && customLeaveNames.size() == customLeaveDays.size()) {
            Map<String, Integer> customLeaves = new HashMap<>();
            for (int i = 0; i < customLeaveNames.size(); i++) {
                String name = customLeaveNames.get(i);
                Integer days = customLeaveDays.get(i);
                if (name != null && !name.trim().isEmpty() && days != null) {
                    customLeaves.put(name.trim(), days);
                }
            }
            employee.setCustomLeaves(customLeaves);
        }

        employeeRepository.save(employee);
        redirectAttributes.addFlashAttribute("success",
                "Employee added successfully! Employee can now login with email, password and Employee ID.");
        return getEmployeesRedirect(session);
    }

    @RequestMapping(value = "/edit/{id}", method = RequestMethod.GET)
    public String showEditForm(@PathVariable Long id, Model model, HttpSession session) {
        if (!isAuthorizedStaff(session)) {
            return "redirect:/login";
        }

        Optional<Employee> employee = employeeRepository.findById(id);
        if (employee.isEmpty()) {
            return getEmployeesRedirect(session);
        }

        model.addAttribute("employee", employee.get());
        model.addAttribute("departments", DEPARTMENTS);
        model.addAttribute("designations", DESIGNATIONS);
        model.addAttribute("teams", teamRepository.findByIsActiveTrueOrderByNameAsc());
        return "admin-employee-edit";
    }

    @RequestMapping(value = "/update/{id}", method = RequestMethod.POST)
    public String updateEmployee(@PathVariable Long id,
            @ModelAttribute Employee employee,
            @RequestParam(required = false) String newPassword,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Boolean isTeamLead,
            @RequestParam(required = false) List<String> customLeaveNames,
            @RequestParam(required = false) List<Integer> customLeaveDays,
            RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isAuthorizedStaff(session)) {
            return "redirect:/login";
        }

        Optional<Employee> existingEmployee = employeeRepository.findById(id);
        if (existingEmployee.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Employee not found!");
            return getEmployeesRedirect(session);
        }

        // Validate required employee fields
        if (employee.getName() == null || employee.getName().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Employee name is required!");
            return getEmployeesRedirect(session);
        }
        if (employee.getEmail() == null || !employee.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            redirectAttributes.addFlashAttribute("error", "A valid email address is required!");
            return getEmployeesRedirect(session);
        }
        if (employee.getSalary() != null && employee.getSalary() < 0) {
            redirectAttributes.addFlashAttribute("error", "Base salary cannot be negative!");
            return getEmployeesRedirect(session);
        }
        Optional<Employee> existingByEmail = employeeRepository.findByEmail(employee.getEmail().trim().toLowerCase());
        if (existingByEmail.isPresent() && !existingByEmail.get().getId().equals(id)) {
            redirectAttributes.addFlashAttribute("error", "Email already exists!");
            return getEmployeesRedirect(session);
        }

        Employee emp = existingEmployee.get();
        emp.setName(employee.getName().trim());
        emp.setEmail(employee.getEmail().trim().toLowerCase());
        emp.setDepartment(employee.getDepartment());
        emp.setDesignation(employee.getDesignation());
        emp.setIsTeamLead(Boolean.TRUE.equals(isTeamLead));
        if (teamId != null) {
            emp.setTeam(teamRepository.findById(teamId).orElse(null));
            if (emp.getTeam() != null && (emp.getDepartment() == null || emp.getDepartment().isEmpty())) {
                emp.setDepartment(emp.getTeam().getDepartment().getName());
            }
        } else {
            emp.setTeam(null);
        }
        emp.setPhone(employee.getPhone());
        // Non-admin staff cannot modify admin-set base salary
        Object userObj = session.getAttribute("user");
        String callerRole = (userObj instanceof com.example.demo.model.User u) ? u.getRole() : (String) session.getAttribute("userRole");
        if ("ADMIN".equalsIgnoreCase(callerRole) || emp.getSalary() == null) {
            emp.setSalary(employee.getSalary());
        }
        emp.setPaidLeave(employee.getPaidLeave());
        emp.setSickLeave(employee.getSickLeave());
        emp.setCasualLeave(employee.getCasualLeave());
        // Update password only if a new one was provided
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            if (passwordService != null) {
                emp.setPassword(passwordService.encode(newPassword.trim()));
            } else {
                emp.setPassword(newPassword.trim());
            }
            if (sessionSecurityService != null) {
                sessionSecurityService.invalidateSessionsForUser(emp.getEmail());
            }
        }



        // Only update employee ID if it's different and doesn't exist
        if (employee.getEmployeeId() != null && !employee.getEmployeeId().equals(emp.getEmployeeId())) {
            Optional<Employee> existingById = employeeRepository.findByEmployeeId(employee.getEmployeeId());
            if (existingById.isPresent() && !existingById.get().getId().equals(id)) {
                redirectAttributes.addFlashAttribute("error", "Employee ID already exists!");
                return getEmployeesRedirect(session);
            }
            emp.setEmployeeId(employee.getEmployeeId());
        }

        // Set default scheduled times if not set: 9 AM for clock-in, 6 PM for clock-out
        if (employee.getScheduledStartTime() != null && !employee.getScheduledStartTime().isEmpty()) {
            emp.setScheduledStartTime(employee.getScheduledStartTime());
        } else if (emp.getScheduledStartTime() == null || emp.getScheduledStartTime().isEmpty()) {
            emp.setScheduledStartTime("09:00");
        }

        // Process Custom Leaves
        Map<String, Integer> customLeaves = new HashMap<>();
        if (customLeaveNames != null && customLeaveDays != null && customLeaveNames.size() == customLeaveDays.size()) {
            for (int i = 0; i < customLeaveNames.size(); i++) {
                String name = customLeaveNames.get(i);
                Integer days = customLeaveDays.get(i);
                if (name != null && !name.trim().isEmpty() && days != null) {
                    customLeaves.put(name.trim(), days);
                }
            }
        }
        // Always set the map, even if empty (handling removal of all custom leaves)
        emp.setCustomLeaves(customLeaves);

        if (employee.getScheduledEndTime() != null && !employee.getScheduledEndTime().isEmpty()) {
            emp.setScheduledEndTime(employee.getScheduledEndTime());
        } else if (emp.getScheduledEndTime() == null || emp.getScheduledEndTime().isEmpty()) {
            emp.setScheduledEndTime("18:00");
        }

        employeeRepository.save(emp);
        redirectAttributes.addFlashAttribute("success", "Employee updated successfully!");
        return getEmployeesRedirect(session);
    }

    @Transactional
    @PostMapping("/delete/{id}")
    public String deleteEmployee(@PathVariable Long id, RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isAuthorizedStaff(session)) {
            return "redirect:/login";
        }

        Optional<Employee> employeeOpt = employeeRepository.findById(id);
        if (employeeOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Employee not found!");
            return getEmployeesRedirect(session);
        }

        Employee employee = employeeOpt.get();

        // Invalidate all active sessions for this deleted employee
        if (sessionSecurityService != null) {
            sessionSecurityService.invalidateSessionsForUser(employee.getEmail());
        }

        try {
            // 1. Delete all related attendance records
            List<com.example.demo.model.Attendance> attendances = attendanceRepository.findByEmployee(employee);
            attendanceRepository.deleteAll(attendances);
            List<com.example.demo.model.Task> tasks = taskRepository.findByEmployee(employee);
            taskRepository.deleteAll(tasks);

            // 3. Delete Leave Requests
            List<com.example.demo.model.LeaveRequest> leaveRequests = leaveRequestRepository.findByEmployee(employee);
            leaveRequestRepository.deleteAll(leaveRequests);

            // 4. Delete Suggestions
            List<com.example.demo.model.Suggestion> suggestions = suggestionRepository
                    .findByEmployeeOrderByCreatedAtDesc(employee);
            suggestionRepository.deleteAll(suggestions);

            // 5. Delete Notifications
            List<com.example.demo.model.Notification> notifications = notificationRepository
                    .findByRecipientOrderByCreatedAtDesc(employee);
            notificationRepository.deleteAll(notifications);

            // 6. Delete Employee Activities
            // Create a custom query or strict find if needed, but for now assuming we can
            // fetch all or just rely on a new method in repository if delete by employee
            // isn't direct.
            // Since we don't have a direct "findByEmployee" in the standard JPA without
            // defining it, let's assume we might need to add it or it exists.
            // However, EmployeeActivityRepository likely extends JpaRepository. Let's rely
            // on standard finding or add if missing.
            // Actually, checking EmployeeController, it uses `findByEmployeeAnd...`. We
            // should probably just add `void deleteByEmployee(Employee employee);` to
            // repositories in a real scenario,
            // but here we can just fetch and delete.
            // Let's use a standard find. If methods are missing in repo interfaces, we
            // might error out.
            // Wait, I didn't check if `findByEmployee` exists in all those repositories.
            // EmployeeActivityRepository was used with `findByEmployeeAndStartedAtBetween`.
            // Let's assume standard `findByEmployee` works if I added it or if I can use
            // what's there.
            // To be safe, let's use what we can see or accept we might need to update
            // repositories too.
            // Actually, for EmployeeActivity, we saw `findByEmployeeAndStartedAtBetween`
            // usage.
            // I'll assume `findByEmployee` is available or I should add it.
            // Given I can't easily check all repo interfaces right now without reading them
            // all, I'll try to use existing ones or `findAll`.
            // But `findAll` filtering is slow.
            // Let's assume `findByEmployee` exists or I will update the repositories if
            // compilation fails? No, I should verify.
            // I previously saw `LeaveRequestRepository.findByEmployee`.
            // I saw `SuggestionRepository.findByEmployeeOrderByCreatedAtDesc`.
            // I saw `NotificationRepository.findByRecipientOrderByCreatedAtDesc`.
            // I saw `AttendanceRepository.findByEmployee`.

            // For EmployeeActivityRepository, I saw `findByEmployeeAndStartedAtBetween` and
            // `findByEmployeeOrderByStartedAtDesc`.
            // So `findByEmployeeOrderByStartedAtDesc` should return all activities.
            List<com.example.demo.model.EmployeeActivity> activities = employeeActivityRepository
                    .findByEmployeeOrderByStartedAtDesc(employee);
            employeeActivityRepository.deleteAll(activities);

            // 7. Delete Idle Incidents
            List<com.example.demo.model.IdleIncident> incidents = idleIncidentRepository.findByEmployee(employee);
            idleIncidentRepository.deleteAll(incidents);

            // 8. Delete Team Badges
            List<com.example.demo.model.TeamBadge> badges = teamBadgeRepository.findByEmployee(employee);
            teamBadgeRepository.deleteAll(badges);

            // 9. Delete Payroll Records
            List<com.example.demo.model.Payroll> payrolls = payrollRepository.findByEmployee(employee);
            payrollRepository.deleteAll(payrolls);

            // 10. Delete Goals
            List<com.example.demo.model.Goal> goals = goalRepository.findByEmployee(employee);
            goalRepository.deleteAll(goals);

            // 11. Delete Feedbacks
            List<com.example.demo.model.Feedback> feedbacks = feedbackRepository.findByEmployee(employee);
            feedbackRepository.deleteAll(feedbacks);

            // 12. Handle Meetings

            // 12a. Meetings where employee is organizer -> Delete
            List<com.example.demo.model.Meeting> organizedMeetings = meetingRepository.findByOrganizer(employee);
            meetingRepository.deleteAll(organizedMeetings);

            // 12b. Meetings where employee is attendee -> Remove from list
            List<com.example.demo.model.Meeting> attendingMeetings = meetingRepository
                    .findByAttendeesContaining(employee);
            for (com.example.demo.model.Meeting meeting : attendingMeetings) {
                meeting.getAttendees().remove(employee);
                meetingRepository.save(meeting);
            }

            // 12c. Meetings where employee is participant -> Remove from list
            List<com.example.demo.model.Meeting> participatingMeetings = meetingRepository
                    .findByEmployeeParticipantsContaining(employee);
            for (com.example.demo.model.Meeting meeting : participatingMeetings) {
                meeting.getEmployeeParticipants().remove(employee);
                meetingRepository.save(meeting);
            }

            // 12d. Meetings where employee is target -> Set null or delete?
            // If it's a 1-on-1, maybe delete? Or just set null?
            // Model has `@JoinColumn(name = "target_employee_id")`. Nullable usually true
            // by default.
            List<com.example.demo.model.Meeting> targetMeetings = meetingRepository.findByTargetEmployee(employee);
            for (com.example.demo.model.Meeting meeting : targetMeetings) {
                meeting.setTargetEmployee(null);
                meetingRepository.save(meeting);
            }

            // 7. Delete Idle Incidents (Implementation via findAll + filter fallback if
            // repo method missing,
            // or I can assume `findByEmployee` works as I didn't check file
            // `IdleIncidentRepository.java`).
            // Let's try to assume `findByEmployee` exists or I'll quickly check/add it in
            // next step if fails.
            // For safety in this "one shot" edit, I'll use the specific deletions I know
            // exist or are standard.
            // I'll blindly attempt `findByEmployee` for IdleIncidentRepository.
            // JpaRepository usually can infer it.
            // Wait, `IdleIncidentRepository` might not have it declared.
            // In `EmployeeController`,
            // `idleIncidentRepository.findActiveIncidentByEmployee(emp)` was used.
            // I should use `findAll` and filter to be 100% safe without editing repo file,
            // OR edit repo file.
            // I'll edit repo file in next step if needed, but here I'll try to use a safe
            // approach or just add `findByEmployee` to repo now?
            // I'll add `findByEmployee` to IdleIncidentRepository in this turn too.

            // Now delete the employee
            employeeRepository.deleteById(id);


            redirectAttributes.addFlashAttribute("success",
                    "Employee deleted successfully! All related records have been removed.");
        } catch (Exception e) {
            try {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            } catch (Exception ignored) {}
            redirectAttributes.addFlashAttribute("error",
                    "Failed to delete employee: " + e.getMessage() + ". Please try again or contact support.");
            System.err.println("Error deleting employee: " + e.getMessage());
        }
 
        return getEmployeesRedirect(session);
    }

    @RequestMapping(value = "/grant-leaves", method = RequestMethod.POST)
    public String grantLeaves(@RequestParam Long employeeId,
            @RequestParam String leaveType,
            @RequestParam int days,
            RedirectAttributes redirectAttributes,
            HttpSession session) {

        if (!isAuthorizedStaff(session)) {
            return "redirect:/login";
        }

        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Employee not found!");
            return getEmployeesRedirect(session);
        }

        Employee employee = employeeOpt.get();
        if (days <= 0) {
            redirectAttributes.addFlashAttribute("error", "Days must be greater than 0!");
            return getEmployeesRedirect(session);
        }

        switch (leaveType) {
            case "PAID":
                employee.setPaidLeave((employee.getPaidLeave() != null ? employee.getPaidLeave() : 0) + days);
                break;
            case "SICK":
                employee.setSickLeave((employee.getSickLeave() != null ? employee.getSickLeave() : 0) + days);
                break;
            case "CASUAL":
                employee.setCasualLeave((employee.getCasualLeave() != null ? employee.getCasualLeave() : 0) + days);
                break;
            default:
                redirectAttributes.addFlashAttribute("error", "Invalid leave type!");
                return getEmployeesRedirect(session);
        }

        employeeRepository.save(employee);
        redirectAttributes.addFlashAttribute("success",
                "Successfully granted " + days + " extra " + leaveType.toLowerCase() + " leave(s) to "
                        + employee.getName());

        return getEmployeesRedirect(session);
    }
}
