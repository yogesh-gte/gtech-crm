package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Service providing secure password hashing, verification, transparent legacy auto-upgrade,
 * and cryptographically secure reset token management.
 */
@Service
public class PasswordService {

    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 128;

    // Standard modular crypt format pattern for BCrypt hashes ($2a$, $2b$, or $2y$, 2-digit cost, 53 chars)
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public PasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Hashes a raw password using BCrypt.
     */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password exceeds maximum allowed length of " + MAX_PASSWORD_LENGTH + " characters");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Checks if a raw password matches an existing BCrypt hash.
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        if (rawPassword.length() > MAX_PASSWORD_LENGTH) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException e) {
            // Malformed hash
            return false;
        }
    }

    /**
     * Returns true if the stored value matches a standard BCrypt hash pattern.
     */
    public boolean isBCryptHash(String storedPassword) {
        if (storedPassword == null) {
            return false;
        }
        return BCRYPT_PATTERN.matcher(storedPassword).matches();
    }

    /**
     * Verifies the raw password against stored password.
     * If the stored password is in legacy plaintext format and matches,
     * it automatically invokes upgradeCallback with the newly computed BCrypt hash.
     *
     * @param rawPassword the password entered by the user
     * @param storedPassword the password hash or legacy plaintext from DB
     * @param upgradeCallback consumer that saves the new BCrypt hash to DB
     * @return true if password is valid, false otherwise
     */
    public boolean verifyAndUpgrade(String rawPassword, String storedPassword, Consumer<String> upgradeCallback) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (rawPassword.length() > MAX_PASSWORD_LENGTH) {
            return false;
        }

        if (isBCryptHash(storedPassword)) {
            return matches(rawPassword, storedPassword);
        }

        // Legacy plaintext password path: perform constant-time comparison to prevent timing attacks
        byte[] a = storedPassword.getBytes(StandardCharsets.UTF_8);
        byte[] b = rawPassword.getBytes(StandardCharsets.UTF_8);
        boolean matches = MessageDigest.isEqual(a, b);

        if (matches) {
            // Upgrade legacy password to BCrypt transparently
            String newHash = encode(rawPassword);
            if (upgradeCallback != null) {
                try {
                    upgradeCallback.accept(newHash);
                } catch (Exception e) {
                    // Log or handle gracefully; authentication still succeeds
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Generates a cryptographically secure 256-bit (32-byte) random token formatted as a 64-character hex string.
     */
    public String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Computes the SHA-256 hash of a reset token for secure DB storage.
     */
    public String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isEmpty()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Validates whether a proposed password meets the length constraints.
     */
    public boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH && password.length() <= MAX_PASSWORD_LENGTH;
    }
}
