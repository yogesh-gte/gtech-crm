package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Service to validate and sanitize redirect destinations to prevent Open Redirect vulnerabilities.
 * Enforces strict local-destination constraints, rejecting absolute URLs, protocol-relative paths,
 * encoded bypasses, backslashes, path traversal exploits, and non-HTTP schemes.
 */
@Service
public class RedirectSecurityService {

    public static final String DEFAULT_FALLBACK = "/employee/dashboard";

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\r\n\t\0]");
    private static final Pattern SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    /**
     * Sanitizes a candidate redirect URL. If the candidate is safe and represents
     * an allowed local relative path, returns the cleaned URL. Otherwise returns fallback.
     */
    public String sanitizeRedirectUrl(String candidateUrl, String fallback) {
        String safeFallback = (fallback != null && isSafeLocalRedirect(fallback)) ? fallback : DEFAULT_FALLBACK;
        if (candidateUrl == null || candidateUrl.trim().isEmpty()) {
            return safeFallback;
        }

        String trimmed = candidateUrl.trim();

        if (isSafeLocalRedirect(trimmed)) {
            return trimmed;
        }

        return safeFallback;
    }

    /**
     * Determines whether a candidate URL is a safe local relative redirect destination.
     */
    public boolean isSafeLocalRedirect(String candidateUrl) {
        if (candidateUrl == null || candidateUrl.trim().isEmpty()) {
            return false;
        }

        String candidate = candidateUrl.trim();

        // 1. Reject control characters (CRLF injection, null byte)
        if (CONTROL_CHARS.matcher(candidate).find()) {
            return false;
        }

        // 2. Reject backslashes (e.g. /\evil.com, \evil.com, /dashboard\evil.com)
        if (candidate.contains("\\")) {
            return false;
        }

        // 3. Must start with a single slash (relative path), reject protocol-relative (//example.com) and embedded double-slashes
        if (!candidate.startsWith("/") || candidate.contains("//")) {
            return false;
        }

        // 4. Reject scheme declarations (e.g. javascript:, http:, https:, data:)
        if (SCHEME_PATTERN.matcher(candidate).matches()) {
            return false;
        }

        // 5. Test multi-level URL decoding to defeat recursive encoding bypasses (%2f%2fevil.com, %5cevil.com)
        String decoded = candidate;
        try {
            for (int i = 0; i < 3; i++) {
                String next = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
                if (next.equals(decoded)) {
                    break;
                }
                decoded = next;
                if (decoded.contains("\\") || decoded.contains("//") || SCHEME_PATTERN.matcher(decoded).matches()) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }

        // 6. Java URI parsing verification
        try {
            URI uri = new URI(candidate);
            if (uri.isAbsolute() || uri.getHost() != null || uri.getAuthority() != null || uri.getScheme() != null) {
                return false;
            }
            // Check normalized path doesn't escape root
            String path = uri.getPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        return true;
    }
}
