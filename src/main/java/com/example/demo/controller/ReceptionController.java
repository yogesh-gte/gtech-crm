package com.example.demo.controller;

import com.example.demo.model.Employee;
import com.example.demo.model.Visitor;
import com.example.demo.repository.EmployeeRepository;
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
@RequestMapping("/reception")
public class ReceptionController {

    @Autowired
    private VisitorRepository visitorRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmailService emailService;

    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)
    public String receptionDashboard(Model model, HttpSession session) {
        // Allow access to admin, employee, or public (reception can be accessed without login)
        // But for better security, we can check if it's admin or employee
        if (session.getAttribute("user") == null && session.getAttribute("employee") == null) {
            // Allow public access for reception, but you can restrict if needed
        }

        // Only pass the visitor object for the registration form
        // All visitor information is managed in Admin Dashboard
        model.addAttribute("visitor", new Visitor());

        return "reception-dashboard";
    }

    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public String registerVisitor(@ModelAttribute Visitor visitor, RedirectAttributes redirectAttributes) {
        // Set registered time
        visitor.setRegisteredTime(LocalDateTime.now());
        visitor.setStatus("Registered");
        
        // Set approval status to PENDING if employee email is provided
        if (visitor.getEmployeeEmail() != null && !visitor.getEmployeeEmail().isEmpty()) {
            visitor.setApprovalStatus("PENDING");
            
            // Find employee and send notification email with registered time
            Optional<Employee> employeeOpt = employeeRepository.findByEmail(visitor.getEmployeeEmail());
            if (employeeOpt.isPresent()) {
                // Save visitor first to get the ID
                visitorRepository.save(visitor);
                emailService.sendVisitorRegistrationNotification(
                    visitor.getEmployeeEmail(),
                    visitor.getName(),
                    visitor.getCompany(),
                    visitor.getPurpose(),
                    visitor.getPersonToMeet(),
                    visitor.getRegisteredTime(),
                    visitor.getId(),
                    visitor.getEmail()
                );
            } else {
                visitorRepository.save(visitor);
            }
        } else {
            // No approval needed, but still not checked in
            visitor.setApprovalStatus("APPROVED");
            visitorRepository.save(visitor);
        }
        redirectAttributes.addFlashAttribute("success", 
            visitor.getApprovalStatus().equals("PENDING") ? 
            "Visitor registered successfully! Notification sent to employee. Visitor can check-in after approval." : 
            "Visitor registered successfully! Visitor can now check-in using their ID proof.");
        return "redirect:/reception/dashboard";
    }

    @RequestMapping(value = "/checkin", method = RequestMethod.POST)
    public String checkInVisitor(@RequestParam(required = false) String aadharNumber,
                                 @RequestParam(required = false) String panCardNumber,
                                 @RequestParam(required = false) String idProof, // For backward compatibility
                                 RedirectAttributes redirectAttributes) {
        // Validate that at least one ID is provided
        if ((aadharNumber == null || aadharNumber.trim().isEmpty()) && 
            (panCardNumber == null || panCardNumber.trim().isEmpty()) && 
            (idProof == null || idProof.trim().isEmpty())) {
            redirectAttributes.addFlashAttribute("error", "Please provide either Aadhar number or PAN card number.");
            return "redirect:/reception/dashboard";
        }
        
        // Normalize inputs into local variables (to make them effectively final for lambda)
        final String normalizedAadhar = (aadharNumber != null && !aadharNumber.trim().isEmpty()) ? aadharNumber.trim() : null;
        final String normalizedPan = (panCardNumber != null && !panCardNumber.trim().isEmpty()) ? panCardNumber.trim().toUpperCase() : null;
        final String normalizedIdProof = (idProof != null && !idProof.trim().isEmpty()) ? idProof.trim() : null;
        
        // Find visitor by Aadhar, PAN, or legacy ID proof
        Optional<Visitor> visitorOpt = visitorRepository.findAll().stream()
            .filter(v -> {
                // Check Aadhar number
                if (normalizedAadhar != null && !normalizedAadhar.isEmpty()) {
                    if (v.getAadharNumber() != null && v.getAadharNumber().equals(normalizedAadhar)) {
                        return true;
                    }
                }
                // Check PAN card number
                if (normalizedPan != null && !normalizedPan.isEmpty()) {
                    if (v.getPanCardNumber() != null && v.getPanCardNumber().equalsIgnoreCase(normalizedPan)) {
                        return true;
                    }
                }
                // Check legacy ID proof (for backward compatibility)
                if (normalizedIdProof != null && !normalizedIdProof.isEmpty()) {
                    if (v.getIdProof() != null && v.getIdProof().equals(normalizedIdProof)) {
                        return true;
                    }
                }
                return false;
            })
            .findFirst();
        
        if (visitorOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Visitor not found with the provided ID proof. Please check the Aadhar or PAN card number and try again.");
            return "redirect:/reception/dashboard";
        }
        
        Visitor visitor = visitorOpt.get();
        
        // Check if already checked in
        if ("Checked In".equals(visitor.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Visitor is already checked in!");
            return "redirect:/reception/dashboard";
        }
        
        // Check approval status - only block if DENIED or REJECTED
        // Allow check-in for PENDING (auto-approve), APPROVED, BUSY, or null
        String approvalStatus = visitor.getApprovalStatus();
        System.out.println("DEBUG: Visitor check-in attempt - Aadhar: " + (normalizedAadhar != null ? normalizedAadhar : "N/A") + 
                          ", PAN: " + (normalizedPan != null ? normalizedPan : "N/A") + 
                          ", Legacy ID: " + (normalizedIdProof != null ? normalizedIdProof : "N/A"));
        System.out.println("DEBUG: Visitor approval status: " + approvalStatus);
        System.out.println("DEBUG: Visitor status: " + visitor.getStatus());
        
        if (approvalStatus != null && !approvalStatus.trim().isEmpty()) {
            String trimmedStatus = approvalStatus.trim().toUpperCase();
            if ("DENIED".equals(trimmedStatus) || "REJECTED".equals(trimmedStatus)) {
                System.out.println("DEBUG: Blocking check-in - status is " + trimmedStatus);
                redirectAttributes.addFlashAttribute("error", "Visitor request has been denied/rejected. Cannot check in.");
                return "redirect:/reception/dashboard";
            }
            // If status is PENDING, auto-approve on check-in
            if ("PENDING".equals(trimmedStatus)) {
                System.out.println("DEBUG: Auto-approving visitor on check-in - status was PENDING");
                visitor.setApprovalStatus("APPROVED");
                visitor.setApprovalTime(LocalDateTime.now());
                visitor.setApprovedBy("System (Auto-approved on check-in)");
            }
            System.out.println("DEBUG: Allowing check-in - status is " + trimmedStatus);
        } else {
            // If approvalStatus is null or empty, auto-approve on check-in
            System.out.println("DEBUG: Auto-approving visitor on check-in - no approval status");
            visitor.setApprovalStatus("APPROVED");
            visitor.setApprovalTime(LocalDateTime.now());
            visitor.setApprovedBy("System (Auto-approved on check-in)");
        }
        
        // Check in visitor
        LocalDateTime checkInTime = LocalDateTime.now();
        visitor.setCheckInTime(checkInTime);
        visitor.setStatus("Checked In");
        visitorRepository.save(visitor);
        
        // Send arrival notification to employee if applicable
        if (visitor.getEmployeeEmail() != null && !visitor.getEmployeeEmail().isEmpty()) {
            try {
                emailService.sendVisitorArrivalNotification(
                    visitor.getEmployeeEmail(),
                    visitor.getName(),
                    visitor.getCompany(),
                    visitor.getPurpose(),
                    visitor.getPersonToMeet(),
                    checkInTime
                );
                System.out.println("Visitor arrival notification sent to employee: " + visitor.getEmployeeEmail());
            } catch (Exception e) {
                System.err.println("Error sending arrival notification: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        redirectAttributes.addFlashAttribute("success", "Visitor " + visitor.getName() + " checked in successfully!");
        return "redirect:/reception/dashboard";
    }

    @RequestMapping(value = "/checkout/{id}", method = RequestMethod.POST)
    public String checkOutVisitor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Visitor visitor = visitorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Visitor not found"));
        visitor.setCheckOutTime(LocalDateTime.now());
        visitor.setStatus("Checked Out");
        visitorRepository.save(visitor);
        redirectAttributes.addFlashAttribute("success", "Visitor checked out successfully!");
        return "redirect:/reception/dashboard";
    }

    @RequestMapping(value = "/search", method = RequestMethod.GET)
    public String searchVisitors(@RequestParam(required = false) String name,
                                @RequestParam(required = false) String date,
                                @RequestParam(required = false) String employeeName,
                                Model model) {
        List<Visitor> results;

        if (name != null && !name.isEmpty()) {
            results = visitorRepository.findAll().stream()
                .filter(v -> v.getName().toLowerCase().contains(name.toLowerCase()))
                .toList();
        } else if (date != null && !date.isEmpty()) {
            java.time.LocalDate searchDate = java.time.LocalDate.parse(date);
            results = visitorRepository.findAll().stream()
                .filter(v -> v.getCheckInTime() != null &&
                           v.getCheckInTime().toLocalDate().equals(searchDate))
                .toList();
        } else if (employeeName != null && !employeeName.isEmpty()) {
            results = visitorRepository.findAll().stream()
                .filter(v -> v.getPersonToMeet() != null &&
                           v.getPersonToMeet().toLowerCase().contains(employeeName.toLowerCase()))
                .toList();
        } else {
            results = visitorRepository.findAllOrderByCheckInTimeDesc();
        }

        model.addAttribute("visitors", results);
        model.addAttribute("searchName", name);
        model.addAttribute("searchDate", date);
        model.addAttribute("searchEmployee", employeeName);

        return "reception-visitor-search";
    }
}


