package com.example.demo.controller;

import com.example.demo.model.Employee;
import com.example.demo.model.User;
import com.example.demo.model.Visitor;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VisitorRepository;
import com.example.demo.service.EmailService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/employee/approvals")
public class VisitorApprovalController {

    @Autowired
    private VisitorRepository visitorRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private com.example.demo.repository.EmployeeRepository employeeRepository;

    @RequestMapping(method = RequestMethod.GET)
    public String pendingApprovals(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        // Get ALL registered visitors for this employee (not just pending)
        // Show visitors who want to meet this employee
        List<Visitor> allVisitors = visitorRepository.findAll().stream()
            .filter(v -> employee.getEmail().equals(v.getEmployeeEmail()))
            .sorted((v1, v2) -> {
                // Sort by registered time, newest first
                if (v1.getRegisteredTime() != null && v2.getRegisteredTime() != null) {
                    return v2.getRegisteredTime().compareTo(v1.getRegisteredTime());
                }
                return 0;
            })
            .toList();
        
        // Separate by status for display
        List<Visitor> pendingVisitors = allVisitors.stream()
            .filter(v -> "PENDING".equals(v.getApprovalStatus()))
            .toList();
        
        List<Visitor> approvedVisitors = allVisitors.stream()
            .filter(v -> "APPROVED".equals(v.getApprovalStatus()))
            .toList();
        
        List<Visitor> otherStatusVisitors = allVisitors.stream()
            .filter(v -> v.getApprovalStatus() != null && 
                        !"PENDING".equals(v.getApprovalStatus()) && 
                        !"APPROVED".equals(v.getApprovalStatus()))
            .toList();

        model.addAttribute("employee", employee);
        model.addAttribute("allVisitors", allVisitors);
        model.addAttribute("pendingVisitors", pendingVisitors);
        model.addAttribute("approvedVisitors", approvedVisitors);
        model.addAttribute("otherStatusVisitors", otherStatusVisitors);
        return "employee-approvals";
    }

    @RequestMapping(value = "/approve/{id}", method = RequestMethod.GET)
    public String approveVisitorGet(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            // Store the return URL in session
            session.setAttribute("returnUrl", "/employee/approvals/approve/" + id);
            return "redirect:/employee/login?returnUrl=/employee/approvals/approve/" + id;
        }
        return approveVisitor(id, session, redirectAttributes);
    }

    @RequestMapping(value = "/approve/{id}", method = RequestMethod.POST)
    public String approveVisitor(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            session.setAttribute("returnUrl", "/employee/approvals/approve/" + id);
            return "redirect:/employee/login?returnUrl=/employee/approvals/approve/" + id;
        }

        Optional<Visitor> visitorOpt = visitorRepository.findById(id);
        if (visitorOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Visitor not found!");
            return "redirect:/employee/approvals";
        }

        Visitor visitor = visitorOpt.get();
        if (!employee.getEmail().equals(visitor.getEmployeeEmail())) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to approve this visitor!");
            return "redirect:/employee/approvals";
        }

        visitor.setApprovalStatus("APPROVED");
        visitor.setApprovedBy(employee.getName());
        visitor.setApprovalTime(LocalDateTime.now());
        // Don't check in automatically - visitor needs to check-in using ID proof
        visitor.setStatus("Registered");
        visitorRepository.save(visitor);
        
        // Debug: Verify the approval status was saved
        System.out.println("DEBUG: Visitor approved - ID: " + visitor.getId());
        System.out.println("DEBUG: Approval status set to: " + visitor.getApprovalStatus());
        System.out.println("DEBUG: Approved by: " + visitor.getApprovedBy());
        System.out.println("DEBUG: Visitor ID Proof: " + visitor.getIdProof());
        
        // Reload from database to verify
        Visitor savedVisitor = visitorRepository.findById(visitor.getId()).orElse(null);
        if (savedVisitor != null) {
            System.out.println("DEBUG: Saved visitor approval status: " + savedVisitor.getApprovalStatus());
        }

        // Send approval notification to visitor
        try {
            emailService.sendVisitorApprovalConfirmation(
                visitor.getEmail(),
                visitor.getName(),
                employee.getName(),
                visitor.getRegisteredTime()
            );
            System.out.println("Visitor approval confirmation sent to: " + visitor.getEmail());
        } catch (Exception e) {
            System.err.println("Error sending visitor approval confirmation: " + e.getMessage());
            e.printStackTrace();
        }

        // Send approval notification to all admin users - AUTOMATIC
        System.out.println("=== Starting admin notification process ===");
        try {
            List<User> allUsers = userRepository.findAll();
            System.out.println("Total users in database: " + allUsers.size());
            
            List<User> adminUsers = allUsers.stream()
                .filter(u -> {
                    boolean isAdmin = u.getRole() != null && "ADMIN".equals(u.getRole());
                    if (isAdmin) {
                        System.out.println("Found admin user: " + u.getName() + " (" + u.getEmail() + ")");
                    }
                    return isAdmin;
                })
                .toList();
            
            System.out.println("Total admin users found: " + adminUsers.size());
            
            if (adminUsers.isEmpty()) {
                System.out.println("ERROR: No admin users found in database! Cannot send admin notification.");
                System.out.println("Please ensure at least one user with role 'ADMIN' exists in the database.");
            } else {
                for (User admin : adminUsers) {
                    if (admin.getEmail() != null && !admin.getEmail().isEmpty()) {
                        System.out.println("Sending approval notification to admin: " + admin.getEmail());
                        try {
                            emailService.sendVisitorApprovalNotificationToAdmin(
                                admin.getEmail(),
                                admin.getName(),
                                visitor.getName(),
                                visitor.getCompany(),
                                visitor.getPurpose(),
                                visitor.getPersonToMeet(),
                                employee.getName(),
                                visitor.getRegisteredTime(),
                                visitor.getApprovalTime()
                            );
                            System.out.println("✓ Admin approval notification sent successfully to: " + admin.getEmail());
                        } catch (Exception e) {
                            System.err.println("✗ Failed to send email to admin " + admin.getEmail() + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("WARNING: Admin user '" + admin.getName() + "' has no email address! Skipping...");
                    }
                }
            }
            System.out.println("=== Admin notification process completed ===");
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR in admin notification process: " + e.getMessage());
            e.printStackTrace();
        }

        redirectAttributes.addFlashAttribute("success", "Visitor approved! They can now check-in using their ID proof at the reception. Notifications sent to visitor and admin.");
        return "redirect:/employee/approvals";
    }

    @RequestMapping(value = "/deny/{id}", method = RequestMethod.GET)
    public String denyVisitorGet(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            session.setAttribute("returnUrl", "/employee/approvals/deny/" + id);
            return "redirect:/employee/login?returnUrl=/employee/approvals/deny/" + id;
        }
        return denyVisitor(id, session, redirectAttributes);
    }

    @RequestMapping(value = "/deny/{id}", method = RequestMethod.POST)
    public String denyVisitor(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            session.setAttribute("returnUrl", "/employee/approvals/deny/" + id);
            return "redirect:/employee/login?returnUrl=/employee/approvals/deny/" + id;
        }

        Optional<Visitor> visitorOpt = visitorRepository.findById(id);
        if (visitorOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Visitor not found!");
            return "redirect:/employee/approvals";
        }

        Visitor visitor = visitorOpt.get();
        if (!employee.getEmail().equals(visitor.getEmployeeEmail())) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to deny this visitor!");
            return "redirect:/employee/approvals";
        }

        visitor.setApprovalStatus("DENIED");
        visitor.setApprovedBy(employee.getName());
        visitor.setApprovalTime(LocalDateTime.now());
        visitorRepository.save(visitor);

        // Send denial notification to visitor
        emailService.sendVisitorDenialNotification(
            visitor.getEmail(),
            visitor.getName(),
            employee.getName()
        );

        redirectAttributes.addFlashAttribute("success", "Visitor request denied. Notification sent to visitor.");
        return "redirect:/employee/approvals";
    }

    @RequestMapping(value = "/reject/{id}", method = RequestMethod.GET)
    public String rejectVisitorGet(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            session.setAttribute("returnUrl", "/employee/approvals/reject/" + id);
            return "redirect:/employee/login?returnUrl=/employee/approvals/reject/" + id;
        }
        return rejectVisitor(id, session, redirectAttributes);
    }

    @RequestMapping(value = "/reject/{id}", method = RequestMethod.POST)
    public String rejectVisitor(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            session.setAttribute("returnUrl", "/employee/approvals/reject/" + id);
            return "redirect:/employee/login?returnUrl=/employee/approvals/reject/" + id;
        }

        Optional<Visitor> visitorOpt = visitorRepository.findById(id);
        if (visitorOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Visitor not found!");
            return "redirect:/employee/approvals";
        }

        Visitor visitor = visitorOpt.get();
        if (!employee.getEmail().equals(visitor.getEmployeeEmail())) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to reject this visitor!");
            return "redirect:/employee/approvals";
        }

        visitor.setApprovalStatus("REJECTED");
        visitor.setApprovedBy(employee.getName());
        visitor.setApprovalTime(LocalDateTime.now());
        visitorRepository.save(visitor);

        // Send rejection notification to visitor
        emailService.sendVisitorRejectionNotification(
            visitor.getEmail(),
            visitor.getName(),
            employee.getName()
        );

        redirectAttributes.addFlashAttribute("success", "Visitor request rejected. Notification sent to visitor.");
        return "redirect:/employee/approvals";
    }

    @RequestMapping(value = "/busy/{id}", method = RequestMethod.GET)
    public String busyVisitorGet(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            session.setAttribute("returnUrl", "/employee/approvals/busy/" + id);
            return "redirect:/employee/login?returnUrl=/employee/approvals/busy/" + id;
        }
        return busyVisitor(id, session, redirectAttributes);
    }

    @RequestMapping(value = "/busy/{id}", method = RequestMethod.POST)
    public String busyVisitor(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            session.setAttribute("returnUrl", "/employee/approvals/busy/" + id);
            return "redirect:/employee/login?returnUrl=/employee/approvals/busy/" + id;
        }

        Optional<Visitor> visitorOpt = visitorRepository.findById(id);
        if (visitorOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Visitor not found!");
            return "redirect:/employee/approvals";
        }

        Visitor visitor = visitorOpt.get();
        if (!employee.getEmail().equals(visitor.getEmployeeEmail())) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to mark this visitor as busy!");
            return "redirect:/employee/approvals";
        }

        visitor.setApprovalStatus("BUSY");
        visitor.setApprovedBy(employee.getName());
        visitor.setApprovalTime(LocalDateTime.now());
        visitorRepository.save(visitor);

        // Send busy notification to visitor
        emailService.sendVisitorBusyNotification(
            visitor.getEmail(),
            visitor.getName(),
            employee.getName()
        );

        redirectAttributes.addFlashAttribute("success", "Visitor marked as busy. Notification sent to visitor.");
        return "redirect:/employee/approvals";
    }
    
    // Email-based approval endpoints (work without login, verify via employee email)
    @RequestMapping(value = "/email/approve/{id}", method = RequestMethod.GET)
    public String emailApproveVisitor(@PathVariable Long id, 
                                     @RequestParam String email,
                                     RedirectAttributes redirectAttributes) {
        return processEmailApproval(id, email, "APPROVED", redirectAttributes);
    }
    
    @RequestMapping(value = "/email/deny/{id}", method = RequestMethod.GET)
    public String emailDenyVisitor(@PathVariable Long id, 
                                  @RequestParam String email,
                                  RedirectAttributes redirectAttributes) {
        return processEmailApproval(id, email, "DENIED", redirectAttributes);
    }
    
    @RequestMapping(value = "/email/reject/{id}", method = RequestMethod.GET)
    public String emailRejectVisitor(@PathVariable Long id, 
                                   @RequestParam String email,
                                   RedirectAttributes redirectAttributes) {
        return processEmailApproval(id, email, "REJECTED", redirectAttributes);
    }
    
    @RequestMapping(value = "/email/busy/{id}", method = RequestMethod.GET)
    public String emailBusyVisitor(@PathVariable Long id, 
                                  @RequestParam String email,
                                  RedirectAttributes redirectAttributes) {
        return processEmailApproval(id, email, "BUSY", redirectAttributes);
    }
    
    private String processEmailApproval(Long id, String employeeEmail, String status, RedirectAttributes redirectAttributes) {
        Optional<Visitor> visitorOpt = visitorRepository.findById(id);
        if (visitorOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Visitor not found!");
            return "redirect:/";
        }
        
        Visitor visitor = visitorOpt.get();
        
        // Verify employee email matches
        if (!employeeEmail.equals(visitor.getEmployeeEmail())) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized: Email does not match visitor's assigned employee.");
            return "redirect:/";
        }
        
        // Get employee name
        String employeeName = employeeRepository.findByEmail(employeeEmail)
            .map(Employee::getName)
            .orElse(employeeEmail);
        
        // Update visitor status
        visitor.setApprovalStatus(status);
        visitor.setApprovedBy(employeeName);
        visitor.setApprovalTime(LocalDateTime.now());
        visitorRepository.save(visitor);
        
        System.out.println("DEBUG: Email-based approval - Visitor ID: " + id + ", Status: " + status + ", Employee: " + employeeEmail);
        
        // Send notifications based on status
        try {
            if ("APPROVED".equals(status)) {
                // Send approval notification to visitor
                emailService.sendVisitorApprovalConfirmation(
                    visitor.getEmail(),
                    visitor.getName(),
                    employeeName,
                    visitor.getRegisteredTime()
                );
                
                // Send approval notification to all admin users
                List<User> adminUsers = userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null && "ADMIN".equals(u.getRole()))
                    .filter(u -> u.getEmail() != null && !u.getEmail().isEmpty())
                    .toList();
                
                for (User admin : adminUsers) {
                    emailService.sendVisitorApprovalNotificationToAdmin(
                        admin.getEmail(),
                        admin.getName(),
                        visitor.getName(),
                        visitor.getCompany(),
                        visitor.getPurpose(),
                        visitor.getPersonToMeet(),
                        employeeName,
                        visitor.getRegisteredTime(),
                        visitor.getApprovalTime()
                    );
                }
            } else if ("DENIED".equals(status)) {
                emailService.sendVisitorDenialNotification(
                    visitor.getEmail(),
                    visitor.getName(),
                    employeeName
                );
            } else if ("REJECTED".equals(status)) {
                emailService.sendVisitorRejectionNotification(
                    visitor.getEmail(),
                    visitor.getName(),
                    employeeName
                );
            } else if ("BUSY".equals(status)) {
                emailService.sendVisitorBusyNotification(
                    visitor.getEmail(),
                    visitor.getName(),
                    employeeName
                );
            }
        } catch (Exception e) {
            System.err.println("Error sending notifications: " + e.getMessage());
            e.printStackTrace();
        }
        
        String statusMessage = status.equals("APPROVED") ? 
            "Visitor approved! They can now check-in using their ID proof at the reception." :
            "Visitor request " + status.toLowerCase() + ". Notification sent.";
        
        redirectAttributes.addFlashAttribute("success", statusMessage);
        return "redirect:/?approved=true";
    }
}

