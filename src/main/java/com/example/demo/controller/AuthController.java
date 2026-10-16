package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.example.demo.service.EmailService emailService;

    @Autowired
    private com.example.demo.service.PasswordService passwordService;

    @Autowired(required = false)
    private com.example.demo.service.SessionSecurityService sessionSecurityService;

    @RequestMapping(value = "/admin/login", method = RequestMethod.GET)
    public String showLoginForm(Model model, HttpSession session) {
        return "login";
    }

    @RequestMapping(value = "/admin/login", method = RequestMethod.POST)
    public String login(@RequestParam String email,
            @RequestParam String password,
            jakarta.servlet.http.HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            boolean passwordMatches = passwordService.verifyAndUpgrade(password, user.getPassword(), newHash -> {
                user.setPassword(newHash);
                userRepository.save(user);
            });

            if (passwordMatches) {
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

                User sessionUser = (sessionSecurityService != null) ? sessionSecurityService.sanitizeUserForSession(user) : user;
                activeSession.setAttribute("user", sessionUser);
                activeSession.setAttribute("userName", user.getName());
                activeSession.setAttribute("userRole", user.getRole());

                if (sessionSecurityService != null) {
                    sessionSecurityService.registerUserSession(user.getEmail(), activeSession);
                }

                // Redirect based on role
                if ("ADMIN".equals(user.getRole())) {
                    redirectAttributes.addFlashAttribute("loginSuccess", "Welcome back, " + user.getName() + "!");
                    return "redirect:/admin/dashboard";
                } else if ("HR".equals(user.getRole())) {
                    return "redirect:/hr/dashboard";
                } else if ("MANAGER".equals(user.getRole())) {
                    return "redirect:/manager/dashboard";
                } else {
                    return "redirect:/dashboard";
                }
            } else {
                redirectAttributes.addFlashAttribute("error", "Invalid email or password!");
                return "redirect:/admin/login";
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Invalid email or password!");
            return "redirect:/admin/login";
        }
    }

    public String login(String email, String password, HttpSession session, RedirectAttributes redirectAttributes) {
        return login(email, password, null, session, redirectAttributes);
    }




    @RequestMapping(value = "/register/admin", method = RequestMethod.GET)
    public String showRegisterForm(Model model, RedirectAttributes redirectAttributes) {
        // Check if any admin already exists
        boolean adminExists = userRepository.findByRole("ADMIN").stream().findFirst().isPresent();
        
        if (adminExists) {
            return "admin-register-disabled";
        }
        
        return "admin-register";
    }

    @RequestMapping(value = "/register/admin", method = RequestMethod.POST)
    public String registerAdmin(@RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            RedirectAttributes redirectAttributes) {

        // Check if any admin already exists
        boolean adminExists = userRepository.findByRole("ADMIN").stream().findFirst().isPresent();
        
        if (adminExists) {
            redirectAttributes.addFlashAttribute("error", "Admin registration is disabled. Admin already exists.");
            return "redirect:/admin/login";
        }

        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Full name is required!");
            return "redirect:/register/admin";
        }
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            redirectAttributes.addFlashAttribute("error", "A valid email address is required!");
            return "redirect:/register/admin";
        }
        if (password == null || password.trim().length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters long!");
            return "redirect:/register/admin";
        }
        if (password.length() > com.example.demo.service.PasswordService.MAX_PASSWORD_LENGTH) {
            redirectAttributes.addFlashAttribute("error", "Password exceeds maximum allowed length!");
            return "redirect:/register/admin";
        }

        if (userRepository.findByEmail(email.trim()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Email already registered!");
            return "redirect:/register/admin";
        }

        User user = new User(email.trim().toLowerCase(), passwordService.encode(password), name.trim(), "ADMIN");
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("message", "Registration successful! Please login with your credentials.");
        return "redirect:/admin/login";
    }

    @RequestMapping(value = "/api/check-email", method = RequestMethod.GET)
    @ResponseBody
    public java.util.Map<String, Boolean> checkEmail(@RequestParam String email) {
        boolean exists = userRepository.findByEmail(email).isPresent();
        java.util.Map<String, Boolean> response = new java.util.HashMap<>();
        response.put("exists", exists);
        return response;
    }

    @RequestMapping(value = "/forgot-password", method = RequestMethod.GET)
    public String showForgotPasswordForm() {
        return "forgot-password";
    }

    @RequestMapping(value = "/forgot-password", method = RequestMethod.POST)
    public String processForgotPassword(@RequestParam String email,
            RedirectAttributes redirectAttributes,
            jakarta.servlet.http.HttpServletRequest request) {

        if (email != null && !email.trim().isEmpty()) {
            Optional<User> userOptional = userRepository.findByEmail(email.trim().toLowerCase());

            if (userOptional.isPresent()) {
                User user = userOptional.get();
                String rawToken = passwordService.generateResetToken();
                String hashedToken = passwordService.hashToken(rawToken);
                user.setResetToken(hashedToken);
                user.setResetTokenExpiry(java.time.LocalDateTime.now().plusMinutes(15));
                userRepository.save(user);

                String appUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                        + request.getContextPath();
                String resetUrl = appUrl + "/reset-password?token=" + rawToken;

                emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
            }
        }

        // Return uniform response to prevent account enumeration
        redirectAttributes.addFlashAttribute("message",
                "If an account exists for that email, we have sent a password reset link.");
        return "redirect:/admin/login";
    }

    @RequestMapping(value = "/reset-password", method = RequestMethod.GET)
    public String showResetPasswordForm(@RequestParam String token, Model model,
            RedirectAttributes redirectAttributes) {
        if (token == null || token.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Invalid or expired reset token!");
            return "redirect:/admin/login";
        }

        String hashedToken = passwordService.hashToken(token);
        Optional<User> user = userRepository.findAll().stream()
                .filter(u -> u.getResetToken() != null && 
                        (u.getResetToken().equals(hashedToken) || u.getResetToken().equals(token)))
                .findFirst();

        if (user.isPresent() && user.get().getResetTokenExpiry() != null
                && user.get().getResetTokenExpiry().isAfter(java.time.LocalDateTime.now())) {
            model.addAttribute("token", token);
            return "reset-password";
        } else {
            redirectAttributes.addFlashAttribute("error", "Invalid or expired reset token!");
            return "redirect:/admin/login";
        }
    }

    @RequestMapping(value = "/reset-password", method = RequestMethod.POST)
    public String processResetPassword(@RequestParam String token,
            @RequestParam String password,
            HttpSession session,
            jakarta.servlet.http.HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        if (password == null || password.trim().length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters long!");
            return "redirect:/reset-password?token=" + token;
        }
        if (password.length() > com.example.demo.service.PasswordService.MAX_PASSWORD_LENGTH) {
            redirectAttributes.addFlashAttribute("error", "Password exceeds maximum allowed length!");
            return "redirect:/reset-password?token=" + token;
        }

        if (token == null || token.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Invalid or expired reset token!");
            return "redirect:/admin/login";
        }

        String hashedToken = passwordService.hashToken(token);
        Optional<User> userOptional = userRepository.findAll().stream()
                .filter(u -> u.getResetToken() != null && 
                        (u.getResetToken().equals(hashedToken) || u.getResetToken().equals(token)))
                .findFirst();

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getResetTokenExpiry() != null && user.getResetTokenExpiry().isAfter(java.time.LocalDateTime.now())) {
                user.setPassword(passwordService.encode(password));
                user.setResetToken(null);
                user.setResetTokenExpiry(null);
                userRepository.save(user);

                // Invalidate all active sessions for this user upon password reset
                if (sessionSecurityService != null) {
                    sessionSecurityService.invalidateSessionsForUser(user.getEmail());
                }
                if (session != null) {
                    try {
                        session.invalidate();
                    } catch (IllegalStateException ignored) {}
                }
                if (sessionSecurityService != null && response != null) {
                    sessionSecurityService.expireSessionCookie(response);
                }

                redirectAttributes.addFlashAttribute("message", "You have successfully reset your password.");
                return "redirect:/admin/login";
            }
        }

        redirectAttributes.addFlashAttribute("error", "Invalid or expired reset token!");
        return "redirect:/admin/login";
    }

    public String processResetPassword(String token, String password, RedirectAttributes redirectAttributes) {
        return processResetPassword(token, password, null, null, redirectAttributes);
    }

    @RequestMapping(value = "/logout", method = {RequestMethod.GET, RequestMethod.POST})
    public String logout(HttpSession session, jakarta.servlet.http.HttpServletResponse response) {
        if (sessionSecurityService != null && response != null) {
            sessionSecurityService.expireSessionCookie(response);
        }
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {}
        }
        return "redirect:/admin/login";
    }

    public String logout(HttpSession session) {
        return logout(session, null);
    }
}


