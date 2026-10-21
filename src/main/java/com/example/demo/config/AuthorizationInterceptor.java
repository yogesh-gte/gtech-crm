package com.example.demo.config;

import com.example.demo.service.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Centralized HandlerInterceptor enforcing deny-by-default role-based authorization
 * on all sensitive application routes.
 */
@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    @Autowired
    private AuthorizationService authorizationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }

        // 1. Explicitly Allow Public Routes
        if (isPublicRoute(uri)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        boolean isAuthenticated = authorizationService.isAuthenticated(session);

        // 2. Enforce Authentication Deny-By-Default
        if (!isAuthenticated) {
            handleUnauthorized(request, response, uri);
            return false;
        }

        // 3. Enforce Role-Based Access Controls
        if (!isAuthorizedForRoute(session, uri, request)) {
            handleForbidden(request, response, uri);
            return false;
        }

        return true;
    }

    private boolean isPublicRoute(String uri) {
        // Static resources
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                || uri.startsWith("/static/") || "/favicon.ico".equals(uri)) {
            return true;
        }

        // Landing and error pages
        if ("/".equals(uri) || "/index".equals(uri) || "/error".equals(uri) || "/submit".equals(uri)) {
            return true;
        }

        // Login & Logout endpoints
        if (uri.startsWith("/login") || uri.startsWith("/admin/login") || uri.startsWith("/hr/login")
                || uri.startsWith("/manager/login") || uri.startsWith("/employee/login")
                || uri.startsWith("/logout") || uri.startsWith("/hr/logout")
                || uri.startsWith("/manager/logout") || uri.startsWith("/employee/logout")) {
            return true;
        }

        // Password reset & Account recovery endpoints
        if (uri.startsWith("/forgot-password") || uri.startsWith("/reset-password") || "/api/check-email".equals(uri)) {
            return true;
        }

        // Initial bootstrap admin registration
        if (uri.startsWith("/register/admin")) {
            return true;
        }

        // Public reception & visitor self-registration
        if (uri.startsWith("/reception") || "/visitors/register".equals(uri)) {
            return true;
        }

        // Mobile QR token-authenticated attendance endpoints
        if (uri.startsWith("/attendance/mobile") || uri.startsWith("/attendance/qr")
                || uri.startsWith("/employee/qrcode/scan") || uri.startsWith("/employee/qrcode/decode-image")) {
            return true;
        }

        return false;
    }

    private boolean isAuthorizedForRoute(HttpSession session, String uri, HttpServletRequest request) {
        // A. Organization Management (Strictly ADMIN)
        if (uri.startsWith("/admin/organization")) {
            return authorizationService.isAdmin(session);
        }

        // B. Admin Registration of HR/Manager (Strictly ADMIN)
        if (uri.startsWith("/admin/register")) {
            return authorizationService.isAdmin(session);
        }

        // C. Employee Management (/admin/employees and /hr/employees) (ADMIN or HR)
        if (uri.startsWith("/admin/employees") || uri.startsWith("/hr/employees")) {
            return authorizationService.hasAnyRole(session, AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_HR);
        }

        // D. Admin APIs
        if (uri.startsWith("/admin/api/employees")) {
            // Staff members can query employee directory
            return authorizationService.isStaff(session);
        }
        if (uri.startsWith("/admin/api/payroll")) {
            return authorizationService.hasAnyRole(session, AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_HR);
        }

        // E. General Admin Routes (Strictly ADMIN)
        if (uri.startsWith("/admin")) {
            return authorizationService.isAdmin(session);
        }

        // F. HR Routes (HR or ADMIN)
        if (uri.startsWith("/hr")) {
            return authorizationService.hasAnyRole(session, AuthorizationService.ROLE_HR, AuthorizationService.ROLE_ADMIN);
        }

        // G. Manager Routes (MANAGER or ADMIN)
        if (uri.startsWith("/manager")) {
            return authorizationService.hasAnyRole(session, AuthorizationService.ROLE_MANAGER, AuthorizationService.ROLE_ADMIN);
        }

        // H. Performance Export (Strictly ADMIN or HR)
        if (uri.startsWith("/reports/export/performance")) {
            return authorizationService.hasAnyRole(session, AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_HR);
        }

        // I. Company-Wide Reports & Exports (Staff: ADMIN, HR, MANAGER)
        if (uri.startsWith("/reports")) {
            return authorizationService.isStaff(session);
        }

        // J. Employee Administration Lists & Logs (/employee/list, /employee/logs) (Staff)
        if (uri.startsWith("/employee/list") || uri.startsWith("/employee/logs")) {
            return authorizationService.isStaff(session);
        }

        // K. General Employee Routes (EMPLOYEE principal, or ADMIN for emergency oversight)
        if (uri.startsWith("/employee")) {
            return authorizationService.isEmployee(session) || authorizationService.isAdmin(session);
        }

        // L. Visitor Deletion (Strictly ADMIN)
        if (uri.startsWith("/visitors/delete")) {
            return authorizationService.isAdmin(session);
        }

        // M. Visitor Management (/visitors/dashboard, /visitors/history, /visitors/search) (Staff)
        if (uri.startsWith("/visitors")) {
            return authorizationService.isStaff(session);
        }

        // N. Sprint Lifecycle & Modifications (ADMIN or MANAGER)
        if (uri.startsWith("/sprints/start") || uri.startsWith("/sprints/close")
                || uri.startsWith("/sprints/add-issue") || uri.startsWith("/sprints/remove-issue")
                || uri.startsWith("/sprints/edit")) {
            return authorizationService.hasAnyRole(session, AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_MANAGER);
        }
        if (uri.startsWith("/sprints/add") || uri.startsWith("/sprints/save")) {
            return authorizationService.isStaff(session);
        }

        // O. Meeting Overview (/meetings/list) (Staff)
        if (uri.startsWith("/meetings/list")) {
            return authorizationService.isStaff(session);
        }

        // P. Generic Authenticated Routes (/issues/**, /sprints/**, /meetings/**, /dashboard, /timelog, /qr-scanner)
        return authorizationService.isAuthenticated(session);
    }

    private void handleUnauthorized(HttpServletRequest request, HttpServletResponse response, String uri) throws IOException {
        if (isApiRequest(request, uri)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
            return;
        }

        // Browser HTML redirection based on route segment
        String redirectUrl = "/login";
        if (uri.startsWith("/admin")) {
            redirectUrl = "/admin/login";
        } else if (uri.startsWith("/hr")) {
            redirectUrl = "/hr/login";
        } else if (uri.startsWith("/manager")) {
            redirectUrl = "/manager/login";
        } else if (uri.startsWith("/employee")) {
            redirectUrl = "/employee/login";
        }

        response.sendRedirect(request.getContextPath() + redirectUrl);
    }

    private void handleForbidden(HttpServletRequest request, HttpServletResponse response, String uri) throws IOException {
        if (isApiRequest(request, uri)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied: insufficient permissions\"}");
            return;
        }

        // For browser requests, respond with HTTP 403 Forbidden
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Insufficient permissions for this resource.");
    }

    private boolean isApiRequest(HttpServletRequest request, String uri) {
        if (uri.contains("/api/") || uri.endsWith(".json")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json") && !accept.contains("text/html")) {
            return true;
        }
        String xRequestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(xRequestedWith)) {
            return true;
        }
        return false;
    }
}
