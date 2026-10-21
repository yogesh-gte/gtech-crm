package com.example.demo.config;

import com.example.demo.service.CsrfTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Filter that enforces synchronized session-backed CSRF protection on state-changing HTTP requests.
 * Exposes CSRF tokens in request attributes and readable cookies for JavaScript clients,
 * and validates tokens with constant-time equality on POST/PUT/DELETE/PATCH operations.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CsrfFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Autowired
    private CsrfTokenService csrfTokenService;

    /**
     * Data carrier mimicking standard Spring Security CsrfToken for template compatibility.
     */
    public record CsrfToken(String token, String parameterName, String headerName) {
        public String getToken() { return token; }
        public String getParameterName() { return parameterName; }
        public String getHeaderName() { return headerName; }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = getNormalizedUri(request);
        String method = request.getMethod();

        // 1. If an active session exists (or request needs token), expose token in request attributes and cookie.
        // Login pages are rendered before any session exists, so create one up front; otherwise the form's
        // hidden _csrf field is empty and the first login POST is rejected.
        boolean isLoginPageRender = SAFE_METHODS.contains(method.toUpperCase()) && isLoginRoute(uri);
        HttpSession session = request.getSession(isLoginPageRender);
        if (session != null) {
            String token = csrfTokenService.getOrCreateToken(session);
            request.setAttribute("_csrf_token", token);
            request.setAttribute("_csrf_parameter_name", CsrfTokenService.CSRF_PARAM_NAME);
            request.setAttribute("_csrf_header_name", CsrfTokenService.CSRF_HEADER_NAME);
            request.setAttribute("_csrf", new CsrfToken(token, CsrfTokenService.CSRF_PARAM_NAME, CsrfTokenService.CSRF_HEADER_NAME));

            // Set XSRF-TOKEN cookie readable by JavaScript for fetch/AJAX
            boolean isHttps = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
            ResponseCookie xsrfCookie = ResponseCookie.from(CsrfTokenService.CSRF_COOKIE_NAME, token)
                    .path("/")
                    .sameSite("Lax")
                    .secure(isHttps)
                    .httpOnly(false)
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, xsrfCookie.toString());
        }

        // 2. Safe HTTP methods (GET, HEAD, OPTIONS, TRACE) are exempt from CSRF mutation checks
        if (SAFE_METHODS.contains(method.toUpperCase())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. State-changing methods (POST, PUT, DELETE, PATCH): Check documented exemptions
        if (isExemptRoute(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 4. Login routes: If session exists with pre-generated token, validate token.
        // If unauthenticated client submits credentials without a prior session, allow login to establish session.
        if (isLoginRoute(uri)) {
            if (session != null && session.getAttribute(CsrfTokenService.CSRF_SESSION_ATTRIBUTE) != null) {
                String candidateToken = csrfTokenService.extractToken(request);
                if (!csrfTokenService.validateToken(candidateToken, session)) {
                    rejectCsrf(request, response, "Invalid or missing CSRF token for login");
                    return;
                }
            }
            filterChain.doFilter(request, response);
            return;
        }

        // 5. For all other state-changing requests, require valid session and matching CSRF token
        if (session == null) {
            rejectCsrf(request, response, "Active session required for state-changing request");
            return;
        }

        String candidateToken = csrfTokenService.extractToken(request);
        if (!csrfTokenService.validateToken(candidateToken, session)) {
            rejectCsrf(request, response, "Invalid or missing CSRF token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void rejectCsrf(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        if (isApiRequest(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"" + message + "\"}");
            return;
        }

        response.sendError(HttpServletResponse.SC_FORBIDDEN, message);
    }

    private boolean isExemptRoute(String uri) {
        // Documented Exemption 1: Email-based one-click visitor approvals (sent to employee email)
        if (uri.startsWith("/email/approve/") || uri.startsWith("/email/deny/")
                || uri.startsWith("/email/reject/") || uri.startsWith("/email/busy/")) {
            return true;
        }

        // Documented Exemption 2: Mobile / QR Attendance endpoints (token-authenticated, no ambient cookies)
        if (uri.startsWith("/attendance/mobile") || uri.startsWith("/attendance/qr")
                || "/employee/qrcode/scan".equals(uri)) {
            return true;
        }

        // Documented Exemption 3: Reception visitor registration kiosks (public terminal before session)
        if (uri.startsWith("/reception/register") || "/visitors/register".equals(uri)) {
            return true;
        }

        // Documented Exemption 4: Bootstrap initial admin registration
        if (uri.startsWith("/register/admin")) {
            return true;
        }

        // Documented Exemption 5: Password recovery submission (unauthenticated, token-verified)
        if (uri.startsWith("/forgot-password") || uri.startsWith("/reset-password")) {
            return true;
        }

        return false;
    }

    private boolean isLoginRoute(String uri) {
        return "/login".equals(uri) || "/admin/login".equals(uri) || "/hr/login".equals(uri)
                || "/manager/login".equals(uri) || "/employee/login".equals(uri);
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains("/api/") || uri.endsWith(".json")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json") && !accept.contains("text/html")) {
            return true;
        }
        String xRequestedWith = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equalsIgnoreCase(xRequestedWith);
    }

    private String getNormalizedUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri;
    }
}
