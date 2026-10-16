package com.example.demo.util;

/**
 * Utility for sanitizing untrusted inputs before logging,
 * preventing CRLF (Carriage Return / Line Feed) log injection attacks
 * and masking sensitive data identifiers.
 */
public final class LogSanitizer {

    private static final int MAX_LOG_LENGTH = 255;

    private LogSanitizer() {
        // Utility class
    }

    /**
     * Strips CR ('\r') and LF ('\n') characters from untrusted strings to prevent
     * log forging / CRLF injection, and bounds the string length.
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "null";
        }

        // Replace carriage return and newline characters with underscores
        String cleaned = input.replace('\r', '_').replace('\n', '_');

        // Prevent log flooding via massive payloads
        if (cleaned.length() > MAX_LOG_LENGTH) {
            return cleaned.substring(0, MAX_LOG_LENGTH) + "...[truncated]";
        }

        return cleaned;
    }

    /**
     * Masks an email address for privacy-conscious logging (e.g. j***@example.com).
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "null";
        }
        String cleaned = sanitize(email);
        int atIdx = cleaned.indexOf('@');
        if (atIdx <= 1) {
            return "***" + (atIdx >= 0 ? cleaned.substring(atIdx) : "");
        }
        return cleaned.charAt(0) + "***" + cleaned.substring(atIdx);
    }
}
