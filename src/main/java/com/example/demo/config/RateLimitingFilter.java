package com.example.demo.config;

import com.example.demo.service.RateLimiterService;
import com.example.demo.service.RateLimiterService.RateLimitResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter enforcing HTTP rate limiting and abuse prevention on sensitive endpoints.
 * Returns HTTP 429 Too Many Requests with Retry-After header upon violation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitingFilter extends OncePerRequestFilter {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = getNormalizedUri(request);
        String method = request.getMethod();

        // 1. Skip static resources entirely from rate tracking
        if (isStaticResource(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = rateLimiterService.resolveClientIp(request);

        // 2. Sensitive Route Checks
        RateLimitResult limitResult = null;

        if ("POST".equalsIgnoreCase(method)) {
            if (isLoginEndpoint(uri)) {
                String account = request.getParameter("email");
                if (account == null || account.isBlank()) {
                    account = request.getParameter("employeeId");
                }
                limitResult = rateLimiterService.checkLoginAllowed(clientIp, account);
            } else if (isPasswordResetEndpoint(uri)) {
                String email = request.getParameter("email");
                limitResult = rateLimiterService.checkPasswordResetAllowed(clientIp, email);
            } else if (isRegistrationEndpoint(uri)) {
                limitResult = rateLimiterService.checkRegistrationAllowed(clientIp);
            } else if (isHeavyResourceEndpoint(uri)) {
                limitResult = rateLimiterService.checkHeavyResourceAllowed(clientIp, uri);
            }
        } else if ("GET".equalsIgnoreCase(method) && uri.startsWith("/reports/export")) {
            limitResult = rateLimiterService.checkHeavyResourceAllowed(clientIp, uri);
        }

        // 3. Fallback to general request baseline rate limiter
        if (limitResult == null || limitResult.isAllowed()) {
            limitResult = rateLimiterService.checkGeneralAllowed(clientIp);
        }

        // 4. Handle Rate Limit Violations
        if (limitResult != null && !limitResult.isAllowed()) {
            handleRateLimitExceeded(request, response, limitResult.retryAfterSeconds());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void handleRateLimitExceeded(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        boolean isJson = (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || request.getRequestURI().startsWith("/api/");

        if (isJson) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String json = String.format(
                    "{\"success\":false,\"error\":\"TooManyRequests\",\"message\":\"Rate limit exceeded. Please try again in %d seconds.\",\"retryAfter\":%d,\"status\":429}",
                    retryAfterSeconds, retryAfterSeconds);
            response.getWriter().write(json);
        } else {
            response.setContentType("text/html;charset=UTF-8");
            String html = String.format(
                    "<!DOCTYPE html><html><head><title>429 Too Many Requests</title></head>"
                            + "<body style=\"font-family:sans-serif;text-align:center;padding:50px;\">"
                            + "<h2>429 - Too Many Requests</h2>"
                            + "<p>You have made too many requests. Please wait <strong>%d seconds</strong> before trying again.</p>"
                            + "<a href=\"/\">Return to Home</a>"
                            + "</body></html>",
                    retryAfterSeconds);
            response.getWriter().write(html);
        }
    }

    private boolean isLoginEndpoint(String uri) {
        return "/admin/login".equals(uri) || "/login".equals(uri)
                || "/employee/login".equals(uri) || "/hr/login".equals(uri);
    }

    private boolean isPasswordResetEndpoint(String uri) {
        return "/forgot-password".equals(uri) || "/reset-password".equals(uri);
    }

    private boolean isRegistrationEndpoint(String uri) {
        return "/register/admin".equals(uri) || "/admin/register/hr".equals(uri)
                || "/admin/register/manager".equals(uri) || "/visitors/register".equals(uri)
                || "/reception/register".equals(uri);
    }

    private boolean isHeavyResourceEndpoint(String uri) {
        return uri.endsWith("-face") || uri.endsWith("/register-face")
                || uri.endsWith("/upload") || uri.endsWith("/attachment");
    }

    private boolean isStaticResource(String uri) {
        return uri.startsWith("/css/") || uri.startsWith("/js/")
                || uri.startsWith("/images/") || uri.startsWith("/static/")
                || "/favicon.ico".equals(uri);
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
