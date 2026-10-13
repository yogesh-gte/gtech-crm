package com.example.demo.controller;

import com.example.demo.model.Visitor;
import com.example.demo.repository.VisitorRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/visitors")
public class VisitorController {

    @Autowired
    private VisitorRepository visitorRepository;
    
    @Autowired
    private com.example.demo.service.EmailService emailService;

    @RequestMapping(value = "/register", method = RequestMethod.GET)
    public String showRegistrationForm(Model model) {
        model.addAttribute("visitor", new Visitor());
        return "visitor-register";
    }

    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public String registerVisitor(@ModelAttribute Visitor visitor, RedirectAttributes redirectAttributes) {
        visitor.setCheckInTime(LocalDateTime.now());
        visitor.setStatus("Checked In");
        visitorRepository.save(visitor);
        redirectAttributes.addFlashAttribute("success", "Visitor registered successfully!");
        return "redirect:/visitors/dashboard";
    }

    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)
    public String showDashboard(Model model, HttpSession session) {
        // Check if user is logged in
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }
        List<Visitor> allVisitors = visitorRepository.findAllOrderByCheckInTimeDesc();
        List<Visitor> checkedInVisitors = visitorRepository.findByStatus("Checked In");
        List<Visitor> checkedOutVisitors = visitorRepository.findByStatus("Checked Out");
        
        model.addAttribute("allVisitors", allVisitors);
        model.addAttribute("checkedInVisitors", checkedInVisitors);
        model.addAttribute("checkedOutVisitors", checkedOutVisitors);
        model.addAttribute("totalVisitors", allVisitors.size());
        model.addAttribute("currentlyIn", checkedInVisitors.size());
        model.addAttribute("totalCheckedOut", checkedOutVisitors.size());
        
        return "visitor-dashboard";
    }

    @RequestMapping(value = "/checkin", method = RequestMethod.POST)
    public String checkInVisitor(@RequestParam(required = false) String aadharNumber,
                                 @RequestParam(required = false) String panCardNumber,
                                 @RequestParam(required = false) String idProof, // For backward compatibility
                                 RedirectAttributes redirectAttributes, HttpSession session) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }
        
        // Validate that at least one ID is provided
        if ((aadharNumber == null || aadharNumber.trim().isEmpty()) && 
            (panCardNumber == null || panCardNumber.trim().isEmpty()) && 
            (idProof == null || idProof.trim().isEmpty())) {
            redirectAttributes.addFlashAttribute("error", "Please provide either Aadhar number or PAN card number.");
            return "redirect:/visitors/dashboard";
        }
        
        // Normalize inputs into local variables (to make them effectively final for lambda)
        final String normalizedAadhar = (aadharNumber != null && !aadharNumber.trim().isEmpty()) ? aadharNumber.trim() : null;
        final String normalizedPan = (panCardNumber != null && !panCardNumber.trim().isEmpty()) ? panCardNumber.trim().toUpperCase() : null;
        final String normalizedIdProof = (idProof != null && !idProof.trim().isEmpty()) ? idProof.trim() : null;
        
        // Find visitor by Aadhar, PAN, or legacy ID proof
        java.util.Optional<Visitor> visitorOpt = visitorRepository.findAll().stream()
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
            redirectAttributes.addFlashAttribute("error", "Visitor not found with the provided ID proof. Please check your Aadhar or PAN card number and try again.");
            return "redirect:/visitors/dashboard";
        }
        
        Visitor visitor = visitorOpt.get();
        
        // Check if already checked in
        if ("Checked In".equals(visitor.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "Visitor is already checked in!");
            return "redirect:/visitors/dashboard";
        }
        
        // Check approval status - only block if DENIED or REJECTED
        // Allow check-in for PENDING (auto-approve), APPROVED, BUSY, or null
        String approvalStatus = visitor.getApprovalStatus();
        System.out.println("DEBUG: Visitor check-in attempt - ID Proof: " + idProof);
        System.out.println("DEBUG: Visitor approval status: " + approvalStatus);
        System.out.println("DEBUG: Visitor status: " + visitor.getStatus());
        
        if (approvalStatus != null && !approvalStatus.trim().isEmpty()) {
            String trimmedStatus = approvalStatus.trim().toUpperCase();
            if ("DENIED".equals(trimmedStatus) || "REJECTED".equals(trimmedStatus)) {
                System.out.println("DEBUG: Blocking check-in - status is " + trimmedStatus);
                redirectAttributes.addFlashAttribute("error", "Visitor request has been denied/rejected. Cannot check in.");
                return "redirect:/visitors/dashboard";
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
        return "redirect:/visitors/dashboard";
    }

    @RequestMapping(value = "/checkout/{id}", method = RequestMethod.POST)
    public String checkOutVisitor(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }
        Visitor visitor = visitorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Visitor not found"));
        visitor.setCheckOutTime(LocalDateTime.now());
        visitor.setStatus("Checked Out");
        visitorRepository.save(visitor);
        redirectAttributes.addFlashAttribute("success", "Visitor checked out successfully!");
        return "redirect:/visitors/dashboard";
    }

    @RequestMapping(value = "/delete/{id}", method = RequestMethod.GET)
    public String deleteVisitor(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }
        visitorRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Visitor record deleted successfully!");
        return "redirect:/visitors/dashboard";
    }

    @RequestMapping(value = "/search", method = RequestMethod.GET)
    public String searchVisitors(@RequestParam(required = false) String name,
                                @RequestParam(required = false) String date,
                                @RequestParam(required = false) String employeeName,
                                Model model, HttpSession session) {
        if (session.getAttribute("user") == null && session.getAttribute("employee") == null) {
            return "redirect:/login";
        }
        
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
        
        return "visitor-search";
    }

    @RequestMapping(value = "/history", method = RequestMethod.GET)
    public String visitorHistory(Model model, HttpSession session) {
        if (session.getAttribute("user") == null && session.getAttribute("employee") == null) {
            return "redirect:/login";
        }
        
        List<Visitor> allVisitors = visitorRepository.findAllOrderByCheckInTimeDesc();
        model.addAttribute("visitors", allVisitors);
        
        return "visitor-history";
    }
}


