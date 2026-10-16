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

    @Autowired
    private com.example.demo.service.PasswordService passwordService;

    @Autowired(required = false)
    private com.example.demo.service.SessionSecurityService sessionSecurityService;

    @Autowired(required = false)
    private com.example.demo.service.RedirectSecurityService redirectSecurityService;

    private String sanitizeRedirect(String url, String fallback) {
        if (redirectSecurityService != null) {
            return redirectSecurityService.sanitizeRedirectUrl(url, fallback);
        }
        return (url != null && url.startsWith("/") && !url.startsWith("//") && !url.contains("\\")) ? url : fallback;
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String showEmployeeLoginForm(@RequestParam(required = false) String returnUrl, 
                                       HttpSession session, 
                                       org.springframework.ui.Model model) {
        if (session.getAttribute("employee") != null) {
            // If already logged in and there's a return URL, go there safely
            String storedReturnUrl = (String) session.getAttribute("returnUrl");
            if (storedReturnUrl != null) {
                session.removeAttribute("returnUrl");
                return "redirect:" + sanitizeRedirect(storedReturnUrl, "/employee/dashboard");
            }
            if (returnUrl != null && !returnUrl.isEmpty()) {
                return "redirect:" + sanitizeRedirect(returnUrl, "/employee/dashboard");
            }
            return "redirect:/employee/dashboard";
        }
        // Store return URL in session if provided and safe
        if (returnUrl != null && !returnUrl.isEmpty()) {
            String safeReturn = sanitizeRedirect(returnUrl, null);
            if (safeReturn != null) {
                session.setAttribute("returnUrl", safeReturn);
                model.addAttribute("returnUrl", safeReturn);
            }
        }
        return "employee-login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String employeeLogin(@RequestParam String email,
                               @RequestParam String password,
                               @RequestParam String employeeId,
                               @RequestParam(required = false) String returnUrl,
                               jakarta.servlet.http.HttpServletRequest request,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        
        Optional<Employee> employeeOpt = employeeRepository.findByEmail(email != null ? email.trim() : "");
        
        if (employeeOpt.isPresent()) {
            Employee employee = employeeOpt.get();
            boolean idMatches = employee.getEmployeeId() != null && employee.getEmployeeId().equalsIgnoreCase(employeeId != null ? employeeId.trim() : "");
            boolean passwordMatches = passwordService.verifyAndUpgrade(password, employee.getPassword(), newHash -> {
                employee.setPassword(newHash);
                employeeRepository.save(employee);
            });

            if (idMatches && passwordMatches) {
                // Prevent session fixation by rotating session ID
                HttpSession activeSession;
                if (sessionSecurityService != null && request != null) {
                    activeSession = sessionSecurityService.rotateSession(request);
                } else if (request != null) {
                    try {
                        request.changeSessionId();
                    } catch (Exception ignored) {
                    }
                    activeSession = request.getSession(true);
                } else {
                    activeSession = session;
                }

                Employee sessionEmp = (sessionSecurityService != null) ? sessionSecurityService.sanitizeEmployeeForSession(employee) : employee;
                activeSession.setAttribute("employee", sessionEmp);
                activeSession.setAttribute("employeeName", employee.getName());
                activeSession.setAttribute("employeeId", employee.getEmployeeId());

                if (sessionSecurityService != null) {
                    sessionSecurityService.registerUserSession(employee.getEmail(), activeSession);
                }

                // Check for return URL in session first, then parameter (enforcing safe local redirect)
                String storedReturnUrl = (String) activeSession.getAttribute("returnUrl");
                if (storedReturnUrl != null && !storedReturnUrl.isEmpty()) {
                    activeSession.removeAttribute("returnUrl");
                    return "redirect:" + sanitizeRedirect(storedReturnUrl, "/employee/dashboard");
                }
                if (returnUrl != null && !returnUrl.isEmpty()) {
                    return "redirect:" + sanitizeRedirect(returnUrl, "/employee/dashboard");
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

    @RequestMapping(value = "/logout", method = {RequestMethod.GET, RequestMethod.POST})
    public String employeeLogout(HttpSession session, jakarta.servlet.http.HttpServletResponse response) {
        if (sessionSecurityService != null && response != null) {
            sessionSecurityService.expireSessionCookie(response);
        }
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {}
        }
        return "redirect:/employee/login";
    }

    // Overloaded for backward compatibility with tests/callers without response
    public String employeeLogout(HttpSession session) {
        return employeeLogout(session, null);
    }

    // Overloaded for backward compatibility with tests/callers without request
    public String employeeLogin(String email, String password, String employeeId, String returnUrl,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        return employeeLogin(email, password, employeeId, returnUrl, null, session, redirectAttributes);
    }
}


