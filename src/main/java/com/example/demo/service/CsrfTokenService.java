package com.example.demo.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Service managing cryptographically secure, session-backed CSRF tokens.
 * Generates tokens, rotates them upon authentication/session change,
 * extracts tokens from headers/parameters, and validates with constant-time equality.
 */
@Service
public class CsrfTokenService {

    public static final String CSRF_SESSION_ATTRIBUTE = "_csrf_token";
    public static final String CSRF_PARAM_NAME = "_csrf";
    public static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";
    public static final String CSRF_HEADER_NAME_ALT = "X-XSRF-TOKEN";
    public static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a new 256-bit cryptographically secure hex-encoded CSRF token.
     */
    public String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Retrieves the existing CSRF token for the session, or generates and stores a new one.
     */
    public String getOrCreateToken(HttpSession session) {
        if (session == null) {
            return generateToken();
        }
        synchronized (session.getId().intern()) {
            String token = (String) session.getAttribute(CSRF_SESSION_ATTRIBUTE);
            if (token == null || token.trim().isEmpty()) {
                token = generateToken();
                session.setAttribute(CSRF_SESSION_ATTRIBUTE, token);
            }
            return token;
        }
    }

    /**
     * Rotates the CSRF token for the session (e.g. after login/privilege changes).
     */
    public String rotateToken(HttpSession session) {
        if (session == null) {
            return generateToken();
        }
        synchronized (session.getId().intern()) {
            String newToken = generateToken();
            session.setAttribute(CSRF_SESSION_ATTRIBUTE, newToken);
            return newToken;
        }
    }

    /**
     * Extracts the candidate CSRF token from the incoming HTTP request.
     * Checks headers (X-CSRF-TOKEN, X-XSRF-TOKEN) first, then request parameter (_csrf).
     */
    public String extractToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String token = request.getHeader(CSRF_HEADER_NAME);
        if (token == null || token.trim().isEmpty()) {
            token = request.getHeader(CSRF_HEADER_NAME_ALT);
        }
        if (token == null || token.trim().isEmpty()) {
            token = request.getParameter(CSRF_PARAM_NAME);
        }
        if ((token == null || token.trim().isEmpty()) && request.getQueryString() != null) {
            token = extractParamFromQuery(request.getQueryString(), CSRF_PARAM_NAME);
        }
        return (token != null && !token.trim().isEmpty()) ? token.trim() : null;
    }

    private String extractParamFromQuery(String query, String paramName) {
        if (query == null || query.isBlank()) return null;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && pair.substring(0, idx).trim().equals(paramName)) {
                try {
                    return java.net.URLDecoder.decode(pair.substring(idx + 1).trim(), StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Validates candidate token against the session-stored token using constant-time equality.
     */
    public boolean validateToken(String candidateToken, HttpSession session) {
        if (candidateToken == null || session == null) {
            return false;
        }
        String sessionToken = (String) session.getAttribute(CSRF_SESSION_ATTRIBUTE);
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                candidateToken.getBytes(StandardCharsets.UTF_8),
                sessionToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
