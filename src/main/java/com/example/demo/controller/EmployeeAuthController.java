package com.example.demo.controller;

import com.example.demo.model.Employee;
import com.example.demo.repository.EmployeeRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/employee")
public class EmployeeAuthController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String showEmployeeLoginForm(@RequestParam(required = false) String returnUrl, 
                                       HttpSession session, 
                                       org.springframework.ui.Model model) {
        if (session.getAttribute("employee") != null) {
            // If already logged in and there's a return URL, go there
            String storedReturnUrl = (String) session.getAttribute("returnUrl");
            if (storedReturnUrl != null) {
                session.removeAttribute("returnUrl");
                return "redirect:" + storedReturnUrl;
            }
            if (returnUrl != null && !returnUrl.isEmpty()) {
                return "redirect:" + returnUrl;
            }
            return "redirect:/employee/dashboard";
        }
        // Store return URL in session if provided
        if (returnUrl != null && !returnUrl.isEmpty()) {
            session.setAttribute("returnUrl", returnUrl);
            model.addAttribute("returnUrl", returnUrl);
        }
        return "employee-login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String employeeLogin(@RequestParam String email,
                               @RequestParam String password,
                               @RequestParam String employeeId,
                               @RequestParam(required = false) String returnUrl,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        
        Optional<Employee> employeeOpt = employeeRepository.findByEmail(email);
        
        if (employeeOpt.isPresent()) {
            Employee employee = employeeOpt.get();
            // Verify employee ID + password match
            if (employee.getEmployeeId().equals(employeeId) &&
                    employee.getPassword() != null &&
                    employee.getPassword().equals(password)) {
                // Employee exists in database (added by admin), allow login
                session.setAttribute("employee", employee);
                session.setAttribute("employeeName", employee.getName());
                session.setAttribute("employeeId", employee.getEmployeeId());
                
                // Check for return URL in session first, then parameter
                String storedReturnUrl = (String) session.getAttribute("returnUrl");
                if (storedReturnUrl != null && !storedReturnUrl.isEmpty()) {
                    session.removeAttribute("returnUrl");
                    return "redirect:" + storedReturnUrl;
                }
                if (returnUrl != null && !returnUrl.isEmpty()) {
                    return "redirect:" + returnUrl;
                }
                return "redirect:/employee/dashboard";
            } else {
                redirectAttributes.addFlashAttribute("error", "Invalid email / password / employee ID!");
                return "redirect:/employee/login";
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Employee not found! Please contact admin to add your account.");
            return "redirect:/employee/login";
        }
    }

    @RequestMapping(value = "/logout", method = RequestMethod.GET)
    public String employeeLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/employee/login";
    }
}


