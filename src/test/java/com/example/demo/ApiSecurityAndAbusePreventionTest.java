package com.example.demo;

import com.example.demo.model.Employee;
import com.example.demo.model.User;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.InputValidationService;
import com.example.demo.service.RateLimiterService;
import com.example.demo.service.RateLimiterService.RateLimitResult;
import com.example.demo.util.LogSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApiSecurityAndAbusePreventionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InputValidationService inputValidationService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @BeforeEach
    public void setup() {
        rateLimiterService.resetAll();
    }

    // =========================================================================
    // SECTION 1: SERVER-SIDE INPUT VALIDATION & BOUNDS ENFORCEMENT
    // =========================================================================

    @Test
    @DisplayName("Email validation: enforces presence, regex format, and max length bounds")
    public void testEmailValidation() {
        // Valid email
        assertDoesNotThrow(() -> inputValidationService.validateEmail("admin@example.com"));
        assertDoesNotThrow(() -> inputValidationService.validateEmail("user.name+tag@sub.domain.org"));

        // Missing or blank
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail(null));
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail("   "));

        // Malformed
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail("invalid-email"));
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail("user@.com"));
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail("@domain.com"));

        // Exceeding 100 chars
        String longEmail = "a".repeat(95) + "@example.com";
        assertThrows(IllegalArgumentException.class, () -> inputValidationService.validateEmail(longEmail));
    }

    @Test
    @DisplayName("String bounds validation: enforces presence and min/max length limits")
    public void testStringBoundsValidation() {
        // Required string
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validateString(null, "Username", 3, 50, true));
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validateString("  ", "Username", 3, 50, true));
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validateString("ab", "Username", 3, 50, true));

        // Valid string
        assertDoesNotThrow(() -> 
                inputValidationService.validateString("validUser", "Username", 3, 50, true));

        // Exceeding max length
        String over50 = "x".repeat(51);
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validateString(over50, "Username", 3, 50, true));

        // Optional string allows null or empty
        assertDoesNotThrow(() -> 
                inputValidationService.validateString(null, "MiddleName", 1, 50, false));
        assertDoesNotThrow(() -> 
                inputValidationService.validateString("", "MiddleName", 1, 50, false));
    }

    @Test
    @DisplayName("Numeric bounds validation: enforces positive values and ceiling limits")
    public void testNumericBoundsValidation() {
        // Valid values
        assertDoesNotThrow(() -> inputValidationService.validatePositiveNumber(50000.0, "Salary", 1_000_000.0));

        // Null, zero, or negative
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validatePositiveNumber(null, "Salary", 1_000_000.0));
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validatePositiveNumber(0.0, "Salary", 1_000_000.0));
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validatePositiveNumber(-100.0, "Salary", 1_000_000.0));

        // Exceeding ceiling
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validatePositiveNumber(1_500_000.0, "Salary", 1_000_000.0));
    }

    @Test
    @DisplayName("Date range validation: rejects start date after end date")
    public void testDateRangeValidation() {
        LocalDate start = LocalDate.of(2026, 1, 10);
        LocalDate end = LocalDate.of(2026, 1, 20);

        assertDoesNotThrow(() -> inputValidationService.validateDateRange(start, end));
        assertDoesNotThrow(() -> inputValidationService.validateDateRange(start, start)); // same day allowed

        // Inverted range
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validateDateRange(end, start));

        // Null start date
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validateDateRange(null, end));
    }

    @Test
    @DisplayName("Allowlist validation: accepts only approved values case-insensitively")
    public void testAllowlistValidation() {
        List<String> allowedRoles = List.of("ADMIN", "HR", "MANAGER", "EMPLOYEE");

        assertDoesNotThrow(() -> inputValidationService.validateAllowlist("ADMIN", "Role", allowedRoles));
        assertDoesNotThrow(() -> inputValidationService.validateAllowlist("admin", "Role", allowedRoles));
        assertDoesNotThrow(() -> inputValidationService.validateAllowlist("hr", "Role", allowedRoles));

        // Disallowed / injected roles
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validateAllowlist("SUPERUSER", "Role", allowedRoles));
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validateAllowlist("ROOT", "Role", allowedRoles));
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validateAllowlist("", "Role", allowedRoles));
    }

    @Test
    @DisplayName("Search query bounds and pagination clamping")
    public void testSearchQueryAndPaginationClamping() {
        // Query truncation
        String rawQuery = "searchterm".repeat(20); // 200 chars
        String bounded = inputValidationService.validateSearchQuery(rawQuery, 50);
        assertEquals(50, bounded.length());
        assertEquals("", inputValidationService.validateSearchQuery(null, 50));

        // Pagination clamping
        assertEquals(100, inputValidationService.boundPageSize(500, 10, 100)); // clamped to 100 max
        assertEquals(10, inputValidationService.boundPageSize(null, 10, 100)); // fallback to default
        assertEquals(10, inputValidationService.boundPageSize(-5, 10, 100)); // fallback to default
        assertEquals(25, inputValidationService.boundPageSize(25, 10, 100)); // valid within bounds

        assertEquals(0, inputValidationService.boundPageNumber(null));
        assertEquals(0, inputValidationService.boundPageNumber(-1));
        assertEquals(3, inputValidationService.boundPageNumber(3));
    }

    @Test
    @DisplayName("Base64 biometric image payload size bounding")
    public void testBase64ImagePayloadBounding() {
        // Valid payload (~1KB)
        String smallPayload = "data:image/jpeg;base64," + "A".repeat(1024);
        assertDoesNotThrow(() -> inputValidationService.validateBase64ImagePayload(smallPayload, 5 * 1024 * 1024));

        // Massive payload exceeding 5MB limit
        int maxCharsFor5MB = (5 * 1024 * 1024 * 4 / 3) + 200;
        String massivePayload = "A".repeat(maxCharsFor5MB + 1000);
        assertThrows(IllegalArgumentException.class, () -> 
                inputValidationService.validateBase64ImagePayload(massivePayload, 5 * 1024 * 1024));
    }

    // =========================================================================
    // SECTION 2: RATE LIMITING & ABUSE PREVENTION
    // =========================================================================

    @Test
    @DisplayName("Account-specific rate limiting: blocks account after 5 failed login attempts")
    public void testAccountBruteForceRateLimiting() {
        String testIp = "192.168.1.50";
        String testAccount = "victim@example.com";

        // Initial state: allowed
        RateLimitResult res0 = rateLimiterService.checkLoginAllowed(testIp, testAccount);
        assertTrue(res0.isAllowed());

        // 5 consecutive login failures for this account
        for (int i = 0; i < 5; i++) {
            rateLimiterService.recordLoginFailure(testIp, testAccount);
        }

        // 6th attempt should be blocked
        RateLimitResult blockedRes = rateLimiterService.checkLoginAllowed(testIp, testAccount);
        assertFalse(blockedRes.isAllowed(), "Account should be locked out after 5 failed attempts");
        assertTrue(blockedRes.retryAfterSeconds() > 0, "Retry-After should be positive");
    }

    @Test
    @DisplayName("Account rate limit reset on successful login")
    public void testAccountRateLimitResetOnSuccess() {
        String testIp = "192.168.1.51";
        String testAccount = "user.success@example.com";

        // 4 failures recorded (below threshold of 5)
        for (int i = 0; i < 4; i++) {
            rateLimiterService.recordLoginFailure(testIp, testAccount);
        }

        // User successfully logs in
        rateLimiterService.recordLoginSuccess(testIp, testAccount);

        // Account is clear: 4 more failures can occur without blocking
        for (int i = 0; i < 4; i++) {
            rateLimiterService.recordLoginFailure(testIp, testAccount);
        }
        assertTrue(rateLimiterService.checkLoginAllowed(testIp, testAccount).isAllowed(), 
                "Account should still be allowed because counter was reset on successful login");
    }

    @Test
    @DisplayName("IP-wide brute force rate limiting: blocks IP after 20 total login failures across different accounts")
    public void testIpWideBruteForceRateLimiting() {
        String attackerIp = "192.168.1.100";

        // Attacker sprays across 20 distinct accounts from same IP
        for (int i = 1; i <= 20; i++) {
            rateLimiterService.recordLoginFailure(attackerIp, "target" + i + "@example.com");
        }

        // IP-wide check should block any new login attempt from this IP
        RateLimitResult ipBlockedRes = rateLimiterService.checkLoginAllowed(attackerIp, "anyother@example.com");
        assertFalse(ipBlockedRes.isAllowed(), "IP should be blocked after 20 failures across accounts");
    }

    @Test
    @DisplayName("Password reset abuse prevention: limits requests to 3 per 15 minutes")
    public void testPasswordResetAbusePrevention() {
        String testIp = "10.0.0.1";
        String testEmail = "reset@example.com";

        // 3 requests allowed
        for (int i = 0; i < 3; i++) {
            RateLimitResult res = rateLimiterService.checkPasswordResetAllowed(testIp, testEmail);
            assertTrue(res.isAllowed(), "Request " + (i + 1) + " should be allowed");
        }

        // 4th request blocked
        RateLimitResult blockedRes = rateLimiterService.checkPasswordResetAllowed(testIp, testEmail);
        assertFalse(blockedRes.isAllowed(), "4th password reset request should be rate limited");
        assertTrue(blockedRes.retryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("Memory bounding: evicts expired/stale buckets and prevents unbounded memory growth")
    public void testMemoryBoundingAndEviction() {
        // Insert a burst of keys up to limit
        for (int i = 0; i < 50; i++) {
            rateLimiterService.checkLimit("test_category", "key_" + i, 10, 60);
        }

        assertTrue(rateLimiterService.getTrackedKeysCount() <= RateLimiterService.MAX_TRACKED_ENTRIES);
        assertTrue(rateLimiterService.getTrackedKeysCount() > 0);

        // Reset clears all memory safely
        rateLimiterService.resetAll();
        assertEquals(0, rateLimiterService.getTrackedKeysCount());
    }

    @Test
    @DisplayName("Safe client IP resolution: rejects spoofed X-Forwarded-For unless upstream is a trusted proxy")
    public void testSafeClientIpResolution() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.195"); // untrusted external IP
        request.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.1");

        // By default, trustForwardedFor is false -> ignores header, uses remoteAddr
        rateLimiterService.setTrustForwardedFor(false);
        String resolved = rateLimiterService.resolveClientIp(request);
        assertEquals("203.0.113.195", resolved, "Must ignore spoofed X-Forwarded-For when trustForwardedFor is disabled");

        // When trustForwardedFor is true BUT remoteAddr is not in trusted proxies list -> still ignores header
        rateLimiterService.setTrustForwardedFor(true);
        String resolvedNotTrusted = rateLimiterService.resolveClientIp(request);
        assertEquals("203.0.113.195", resolvedNotTrusted, "Must ignore X-Forwarded-For from non-trusted upstream IP");

        // When remoteAddr IS a trusted proxy (e.g. 127.0.0.1) -> trusts header
        request.setRemoteAddr("127.0.0.1");
        String resolvedFromProxy = rateLimiterService.resolveClientIp(request);
        assertEquals("198.51.100.1", resolvedFromProxy, "Should resolve true client IP from trusted proxy");

        // Reset to default
        rateLimiterService.setTrustForwardedFor(false);
    }

    // =========================================================================
    // SECTION 3: LOG SANITIZATION & CRLF INJECTION DEFENSE
    // =========================================================================

    @Test
    @DisplayName("Log sanitization: neutralizes CRLF injection characters and bounds string length")
    public void testLogSanitizerCrlfAndLengthBounding() {
        // CRLF injection payload
        String maliciousPayload = "admin@example.com\r\n[CRITICAL] User promoted to ADMIN by root\r\n";
        String sanitized = LogSanitizer.sanitize(maliciousPayload);

        assertFalse(sanitized.contains("\r"), "Sanitized string must not contain carriage return");
        assertFalse(sanitized.contains("\n"), "Sanitized string must not contain newline");
        assertTrue(sanitized.contains("_"), "CRLF characters should be replaced with underscores");

        // Length bounding
        String hugePayload = "A".repeat(500);
        String bounded = LogSanitizer.sanitize(hugePayload);
        assertTrue(bounded.length() <= 255 + "...[truncated]".length());
        assertTrue(bounded.endsWith("...[truncated]"));

        // Null handling
        assertEquals("null", LogSanitizer.sanitize(null));
    }

    @Test
    @DisplayName("Email masking: protects privacy and prevents credential exposure in application logs")
    public void testLogSanitizerEmailMasking() {
        assertEquals("j***@example.com", LogSanitizer.maskEmail("john.doe@example.com"));
        assertEquals("a***@test.org", LogSanitizer.maskEmail("admin@test.org"));
        assertEquals("***@corp.net", LogSanitizer.maskEmail("x@corp.net"));
        assertEquals("null", LogSanitizer.maskEmail(null));
        assertEquals("null", LogSanitizer.maskEmail(""));
    }

    // =========================================================================
    // SECTION 4: HTTP MVC INTEGRATION & ERROR CODE VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("RateLimitingFilter returns HTTP 429 Too Many Requests with Retry-After header on lockout")
    public void testRateLimitingFilterReturns429OnLockout() throws Exception {
        String testIp = "127.0.0.1";
        String lockedAccount = "locked.user@example.com";

        // Exhaust login attempts
        for (int i = 0; i < 5; i++) {
            rateLimiterService.recordLoginFailure(testIp, lockedAccount);
        }

        // POST /admin/login with locked account returns 429
        mockMvc.perform(post("/admin/login")
                        .param("email", lockedAccount)
                        .param("password", "anypassword"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("429 - Too Many Requests")));
    }

    @Test
    @DisplayName("RateLimitingFilter content negotiation: returns structured JSON 429 on AJAX/API request")
    public void testRateLimitingFilterReturnsJson429() throws Exception {
        String testIp = "127.0.0.1";
        String lockedAccount = "locked.api@example.com";

        for (int i = 0; i < 5; i++) {
            rateLimiterService.recordLoginFailure(testIp, lockedAccount);
        }

        mockMvc.perform(post("/admin/login")
                        .param("email", lockedAccount)
                        .param("password", "anypassword")
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("TooManyRequests"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("Visitor search handles invalid date format gracefully without 500 error")
    public void testVisitorSearchWithInvalidDateHandledGracefully() throws Exception {
        MockHttpSession session = new MockHttpSession();
        User admin = new User("admin@test.com", "pass", "Admin User", "ADMIN");
        session.setAttribute("user", admin);
        session.setAttribute("userRole", "ADMIN");

        // Query with invalid date string: must not throw unhandled 500
        mockMvc.perform(get("/visitors/search")
                        .session(session)
                        .param("date", "not-a-valid-date"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("error", org.hamcrest.Matchers.containsString("Invalid date format")));
    }

    @Test
    @DisplayName("Employee face registration rejects oversized base64 image payload with validation error")
    public void testFaceRegistrationRejectsOversizedPayload() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Employee employee = new Employee();
        employee.setId(999L);
        employee.setEmail("emp@test.com");
        employee.setEmployeeId("EMP999");
        employee.setName("Test Emp");
        String csrfToken = "valid-csrf-token";
        session.setAttribute("employee", employee);
        session.setAttribute("_csrf_token", csrfToken);

        // Massive payload exceeding 5MB limit
        int maxCharsFor5MB = (5 * 1024 * 1024 * 4 / 3) + 200;
        String massiveFaceImage = "A".repeat(maxCharsFor5MB + 2000);

        mockMvc.perform(post("/employee/register-face")
                        .session(session)
                        .param("_csrf", csrfToken)
                        .param("faceImage", massiveFaceImage))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employee/face-capture"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @DisplayName("Employee face check-in rejects oversized base64 image payload with HTTP 400 Bad Request")
    public void testCheckinFaceRejectsOversizedPayloadReturns400() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Employee employee = new Employee();
        employee.setId(999L);
        employee.setEmail("emp@test.com");
        employee.setEmployeeId("EMP999");
        employee.setName("Test Emp");
        String csrfToken = "valid-csrf-token";
        session.setAttribute("employee", employee);
        session.setAttribute("_csrf_token", csrfToken);

        int maxCharsFor5MB = (5 * 1024 * 1024 * 4 / 3) + 200;
        String massiveFaceImage = "A".repeat(maxCharsFor5MB + 2000);

        mockMvc.perform(post("/employee/checkin-face")
                        .session(session)
                        .param("_csrf", csrfToken)
                        .param("faceImage", massiveFaceImage)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationError"));
    }
}
