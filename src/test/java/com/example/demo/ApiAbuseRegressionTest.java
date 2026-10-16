package com.example.demo;

import com.example.demo.service.InputValidationService;
import com.example.demo.service.RateLimiterService;
import com.example.demo.util.LogSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApiAbuseRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InputValidationService inputValidationService;

    @Autowired
    private RateLimiterService rateLimiterService;

    private static final String TEST_IP = "192.168.1.100";
    private static final String TEST_ACCOUNT = "abuse.target@example.com";

    @BeforeEach
    public void setup() {
        rateLimiterService.resetAll();
    }

    @AfterEach
    public void tearDown() {
        rateLimiterService.resetAll();
    }

    // =========================================================================
    // 1. INPUT VALIDATION & BOUNDS ENFORCEMENT TESTS
    // =========================================================================

    @Test
    @DisplayName("Input Validation: Email format and maximum length are strictly enforced")
    public void testEmailValidationAndBounds() {
        // Valid email
        assertDoesNotThrow(() -> inputValidationService.validateEmail("employee@logsphere.internal"));

        // Missing / empty email
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail(null));
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail("   "));

        // Malformed emails
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail("plainaddress"));
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail("@missinguser.com"));
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail("missingdomain@.com"));

        // Oversized email (> 100 chars)
        String oversizedEmail = "a".repeat(95) + "@example.com";
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail(oversizedEmail));
    }

    @Test
    @DisplayName("Input Validation: String boundaries and allowlists reject unexpected / injection values")
    public void testStringAndAllowlistValidation() {
        // Required string presence & bounds
        assertDoesNotThrow(() -> inputValidationService.validateString("John Doe", "name", 2, 50, true));
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateString("", "name", 2, 50, true));
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateString("J", "name", 2, 50, true));
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateString("A".repeat(51), "name", 2, 50, true));

        // Allowlist validation
        List<String> allowedRoles = List.of("ADMIN", "HR", "MANAGER", "EMPLOYEE");
        assertDoesNotThrow(() -> inputValidationService.validateAllowlist("HR", "role", allowedRoles));

        // Injection / disallowed role attempts rejected
        assertThrows(IllegalArgumentException.class,
                () -> inputValidationService.validateAllowlist("SUPERUSER", "role", allowedRoles));
        assertThrows(IllegalArgumentException.class,
                () -> inputValidationService.validateAllowlist("ADMIN' OR '1'='1", "role", allowedRoles));
        assertThrows(IllegalArgumentException.class,
                () -> inputValidationService.validateAllowlist("<script>alert(1)</script>", "role", allowedRoles));
    }

    @Test
    @DisplayName("Input Validation: Numeric bounds and date range logic")
    public void testNumericAndDateValidation() {
        // Positive number within max bound
        assertDoesNotThrow(() -> inputValidationService.validatePositiveNumber(5000.0, "salary", 1_000_000.0));
        assertThrows(IllegalArgumentException.class,
                () -> inputValidationService.validatePositiveNumber(-100.0, "salary", 1_000_000.0));
        assertThrows(IllegalArgumentException.class,
                () -> inputValidationService.validatePositiveNumber(0.0, "salary", 1_000_000.0));
        assertThrows(IllegalArgumentException.class,
                () -> inputValidationService.validatePositiveNumber(2_000_000.0, "salary", 1_000_000.0));

        // Date range
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        assertDoesNotThrow(() -> inputValidationService.validateDateRange(start, end));

        // End before start throws exception
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateDateRange(end, start));
    }

    // =========================================================================
    // 2. RATE LIMITING & ABUSE PREVENTION TESTS
    // =========================================================================

    @Test
    @DisplayName("Rate Limiting: 5 failed login attempts in 15 minutes trigger account lockout with retry-after")
    public void testLoginRateLimitingEnforcesLockout() {
        // First 5 attempts permitted
        for (int i = 1; i <= 5; i++) {
            RateLimiterService.RateLimitResult res = rateLimiterService.checkLoginAllowed(TEST_IP, TEST_ACCOUNT);
            assertTrue(res.isAllowed(), "Attempt " + i + " must be allowed");
            rateLimiterService.recordLoginFailure(TEST_IP, TEST_ACCOUNT);
        }

        // 6th attempt is throttled
        RateLimiterService.RateLimitResult throttled = rateLimiterService.checkLoginAllowed(TEST_IP, TEST_ACCOUNT);
        assertFalse(throttled.isAllowed(), "6th attempt must be throttled");
        assertTrue(throttled.retryAfterSeconds() > 0, "Retry-After seconds must be positive");
    }

    @Test
    @DisplayName("Rate Limiting: Successful authentication resets failure counter immediately")
    public void testLoginSuccessResetsRateLimiter() {
        // Record 3 failures
        for (int i = 0; i < 3; i++) {
            rateLimiterService.recordLoginFailure(TEST_IP, TEST_ACCOUNT);
        }

        // Successful login
        rateLimiterService.recordLoginSuccess(TEST_IP, TEST_ACCOUNT);

        // Verification: Allowed with full attempts available
        RateLimiterService.RateLimitResult res = rateLimiterService.checkLoginAllowed(TEST_IP, TEST_ACCOUNT);
        assertTrue(res.isAllowed());
    }

    @Test
    @DisplayName("Rate Limiting: Sensitive password reset endpoint enforces strict limit (3 req / 15m)")
    public void testPasswordResetRateLimiting() {
        String resetIp = "10.0.0.5";
        String resetEmail = "reset.test@example.com";

        // 3 allowed attempts
        for (int i = 1; i <= 3; i++) {
            RateLimiterService.RateLimitResult res = rateLimiterService.checkPasswordResetAllowed(resetIp, resetEmail);
            assertTrue(res.isAllowed(), "Password reset attempt " + i + " should be allowed");
        }

        // 4th attempt rejected
        RateLimiterService.RateLimitResult blocked = rateLimiterService.checkPasswordResetAllowed(resetIp, resetEmail);
        assertFalse(blocked.isAllowed(), "4th password reset attempt must be blocked");
        assertTrue(blocked.retryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("Client IP extraction safely ignores untrusted X-Forwarded-For headers when unconfigured")
    public void testUntrustedForwardedForHeaderIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 198.51.100.1");

        String resolvedIp = rateLimiterService.resolveClientIp(request);
        assertEquals("192.168.1.50", resolvedIp,
                "Untrusted X-Forwarded-For header must be ignored to prevent IP spoofing rate-limit bypass");
    }

    @Test
    @DisplayName("LogSanitizer neutralizes CRLF log forging attacks and masks email addresses")
    public void testLogSanitizerCrlfAndEmailMasking() {
        // CRLF injection payload
        String maliciousLogPayload = "admin@example.com\r\n[2026-10-16] SUCCESS: Privileges granted to attacker\r\n";
        String sanitizedLog = LogSanitizer.sanitize(maliciousLogPayload);

        assertFalse(sanitizedLog.contains("\r"), "Sanitized log must not contain carriage returns");
        assertFalse(sanitizedLog.contains("\n"), "Sanitized log must not contain newlines");
        assertTrue(sanitizedLog.contains("_"), "Newlines/CR should be replaced with safe underscores");

        // Email masking for PII privacy
        String masked1 = LogSanitizer.maskEmail("kishore@example.com");
        assertEquals("k***@example.com", masked1);

        String masked2 = LogSanitizer.maskEmail("hr@logsphere.com");
        assertEquals("h***@logsphere.com", masked2);
    }
}
