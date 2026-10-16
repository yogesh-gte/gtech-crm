package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Centralized service providing server-side input validation, bounds checking,
 * allowlist enforcement, and mass-assignment defense.
 */
@Service
public class InputValidationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\-\\s()]{7,25}$");
    private static final int DEFAULT_MAX_SEARCH_LENGTH = 100;
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Validates that an email is present, correctly formatted, and bounded in length.
     */
    public void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required.");
        }
        String trimmed = email.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Email address must not exceed 100 characters.");
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Please provide a valid email address.");
        }
    }

    /**
     * Validates string length and presence.
     */
    public void validateString(String input, String fieldName, int minLength, int maxLength, boolean required) {
        if (input == null || input.trim().isEmpty()) {
            if (required) {
                throw new IllegalArgumentException(fieldName + " is required.");
            }
            return;
        }
        String trimmed = input.trim();
        if (trimmed.length() < minLength) {
            throw new IllegalArgumentException(fieldName + " must be at least " + minLength + " characters.");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters.");
        }
    }

    /**
     * Validates positive number within upper and lower bounds.
     */
    public void validatePositiveNumber(Double value, String fieldName, double maxValue) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero.");
        }
        if (value > maxValue) {
            throw new IllegalArgumentException(fieldName + " exceeds the maximum allowed value (" + maxValue + ").");
        }
    }

    /**
     * Validates that end date is on or after start date.
     */
    public void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("Start date is required.");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be earlier than start date.");
        }
    }

    /**
     * Validates that a string value belongs to an approved allowlist.
     */
    public void validateAllowlist(String value, String fieldName, Collection<String> allowlist) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        boolean matched = allowlist.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(value.trim()));
        if (!matched) {
            throw new IllegalArgumentException("Invalid value for " + fieldName + ". Allowed values: " + allowlist);
        }
    }

    /**
     * Sanitizes and bounds search queries.
     */
    public String validateSearchQuery(String query, int maxLength) {
        if (query == null) {
            return "";
        }
        String trimmed = query.trim();
        int max = maxLength > 0 ? maxLength : DEFAULT_MAX_SEARCH_LENGTH;
        if (trimmed.length() > max) {
            return trimmed.substring(0, max);
        }
        return trimmed;
    }

    /**
     * Validates and bounds pagination parameters.
     */
    public int boundPageSize(Integer size, int defaultSize, int maxSize) {
        if (size == null || size <= 0) {
            return defaultSize;
        }
        return Math.min(size, maxSize > 0 ? maxSize : MAX_PAGE_SIZE);
    }

    public int boundPageNumber(Integer page) {
        if (page == null || page < 0) {
            return 0;
        }
        return page;
    }

    /**
     * Validates phone number format if provided.
     */
    public void validatePhone(String phone) {
        if (phone != null && !phone.trim().isEmpty()) {
            String trimmed = phone.trim();
            if (!PHONE_PATTERN.matcher(trimmed).matches()) {
                throw new IllegalArgumentException("Phone number must contain between 7 and 25 valid digits and formatting characters.");
            }
        }
    }

    /**
     * Validates base64 image strings (e.g. face recognition biometric image) to prevent memory exhaustion.
     */
    public void validateBase64ImagePayload(String base64Image, int maxDecodedBytes) {
        if (base64Image == null || base64Image.trim().isEmpty()) {
            throw new IllegalArgumentException("Face image payload is required.");
        }
        // Base64 encoding expands raw bytes by ~33%. An image of maxDecodedBytes produces ~ (maxBytes * 4 / 3) chars
        int maxCharLength = (maxDecodedBytes * 4 / 3) + 200; // 200 chars for data URL header prefix
        if (base64Image.length() > maxCharLength) {
            throw new IllegalArgumentException("Image payload exceeds maximum allowed size (" + (maxDecodedBytes / (1024 * 1024)) + " MB).");
        }
    }
}
