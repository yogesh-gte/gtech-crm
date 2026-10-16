package com.example.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that applies hardened browser security headers to all HTTP responses.
 * Enforces Content-Security-Policy, anti-clickjacking frame controls, MIME sniffing protection,
 * referrer leak minimization, device permission boundaries, and conditional HSTS.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    public static final String CSP_POLICY =
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://fonts.googleapis.com; " +
            "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com https://cdn.jsdelivr.net data:; " +
            "img-src 'self' data: blob: https://images.unsplash.com; " +
            "connect-src 'self' data: blob:; " +
            "media-src 'self' blob:; " +
            "object-src 'none'; " +
            "frame-ancestors 'self'; " +
            "base-uri 'self'; " +
            "form-action 'self'";

    public static final String PERMISSIONS_POLICY = "camera=(self), microphone=(), geolocation=()";
    public static final String REFERRER_POLICY = "strict-origin-when-cross-origin";
    public static final String HSTS_HEADER_VALUE = "max-age=31536000; includeSubDomains";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Content-Security-Policy (Enforced, not report-only)
        response.setHeader("Content-Security-Policy", CSP_POLICY);

        // 2. MIME sniffing protection
        response.setHeader("X-Content-Type-Options", "nosniff");

        // 3. Framing restrictions (Clickjacking mitigation)
        response.setHeader("X-Frame-Options", "SAMEORIGIN");

        // 4. Referrer Policy
        response.setHeader("Referrer-Policy", REFERRER_POLICY);

        // 5. Permissions-Policy (Permits camera for QR/Face check-in, denies microphone and geolocation)
        response.setHeader("Permissions-Policy", PERMISSIONS_POLICY);

        // 6. HTTP Strict Transport Security (HSTS) - Enabled when request is HTTPS or terminated at trusted proxy
        boolean isHttps = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        if (isHttps) {
            response.setHeader("Strict-Transport-Security", HSTS_HEADER_VALUE);
        }

        filterChain.doFilter(request, response);
    }
}
