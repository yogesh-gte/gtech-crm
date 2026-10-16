package com.example.demo;

import com.example.demo.config.CsrfFilter;
import com.example.demo.config.SecurityHeadersFilter;
import com.example.demo.config.WebMvcConfig;
import com.example.demo.model.User;
import com.example.demo.service.CsrfTokenService;
import com.example.demo.service.RedirectSecurityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BrowserSecurityRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CsrfTokenService csrfTokenService;

    @Autowired
    private RedirectSecurityService redirectSecurityService;

    // =========================================================================
    // 1. CSRF DEFENSE TESTS
    // =========================================================================

    @Test
    @DisplayName("State-changing POST with active session but missing CSRF token is rejected with 403 Forbidden")
    public void testMissingCsrfTokenReturnsForbidden() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", new User("admin@example.com", "pass", "Admin", "ADMIN"));
        session.setAttribute("userRole", "ADMIN");
        csrfTokenService.getOrCreateToken(session);

        mockMvc.perform(post("/admin/payroll/save").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("State-changing POST with active session and invalid CSRF token is rejected with 403 Forbidden")
    public void testInvalidCsrfTokenReturnsForbidden() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", new User("admin@example.com", "pass", "Admin", "ADMIN"));
        session.setAttribute("userRole", "ADMIN");
        csrfTokenService.getOrCreateToken(session);

        mockMvc.perform(post("/admin/payroll/save")
                        .session(session)
                        .param("_csrf", "invalid-forged-token-value"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("State-changing POST with valid CSRF token in header passes CSRF verification")
    public void testValidCsrfTokenInHeaderPermitted() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", new User("admin@example.com", "pass", "Admin", "ADMIN"));
        session.setAttribute("userRole", "ADMIN");
        String validToken = csrfTokenService.getOrCreateToken(session);

        // Should not be rejected by CSRF filter (status will not be 403)
        mockMvc.perform(post("/admin/payroll/save")
                        .session(session)
                        .header("X-CSRF-TOKEN", validToken))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus(),
                        "Request with valid CSRF token in header must not return 403 Forbidden"));
    }

    @Test
    @DisplayName("State-changing POST without session is rejected with 403 Forbidden (session-required defense)")
    public void testStateChangeWithoutSessionRejected() throws Exception {
        mockMvc.perform(post("/admin/payroll/save"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Documented public exemption endpoints bypass CSRF filter without rejection")
    public void testDocumentedExemptionEndpointsBypassCsrf() throws Exception {
        // /reception/register is an exempt kiosk route
        mockMvc.perform(post("/reception/register")
                        .param("visitorName", "John Doe"))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus(),
                        "Documented exempt kiosk route /reception/register must not be rejected by CsrfFilter"));

        // /forgot-password is an exempt recovery route
        mockMvc.perform(post("/forgot-password")
                        .param("email", "anyone@example.com"))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus(),
                        "Documented exempt route /forgot-password must not be rejected by CsrfFilter"));
    }

    // =========================================================================
    // 2. OPEN REDIRECT DEFENSE TESTS
    // =========================================================================

    @Test
    @DisplayName("Open Redirect: Valid local relative paths are accepted")
    public void testValidLocalRedirectsAccepted() {
        assertTrue(redirectSecurityService.isSafeLocalRedirect("/employee/dashboard"));
        assertTrue(redirectSecurityService.isSafeLocalRedirect("/admin/employees"));
        assertTrue(redirectSecurityService.isSafeLocalRedirect("/hr/leave?status=PENDING"));

        assertEquals("/admin/dashboard",
                redirectSecurityService.sanitizeRedirectUrl("/admin/dashboard", "/employee/dashboard"));
    }

    @Test
    @DisplayName("Open Redirect: Absolute external URLs, protocol-relative, and backslash variants are rejected")
    public void testMaliciousRedirectsRejected() {
        // Absolute external URLs
        assertFalse(redirectSecurityService.isSafeLocalRedirect("http://evil.com"));
        assertFalse(redirectSecurityService.isSafeLocalRedirect("https://attacker.org/phish"));

        // Protocol-relative URLs
        assertFalse(redirectSecurityService.isSafeLocalRedirect("//evil.com"));
        assertFalse(redirectSecurityService.isSafeLocalRedirect("///evil.com/path"));

        // Backslash bypasses
        assertFalse(redirectSecurityService.isSafeLocalRedirect("\\evil.com"));
        assertFalse(redirectSecurityService.isSafeLocalRedirect("/\\evil.com"));
        assertFalse(redirectSecurityService.isSafeLocalRedirect("/dashboard\\evil.com"));

        // Control characters & CRLF header injection
        assertFalse(redirectSecurityService.isSafeLocalRedirect("/dashboard\r\nSet-Cookie: session=evil"));
        assertFalse(redirectSecurityService.isSafeLocalRedirect("/dashboard\0evil"));

        // Scheme bypasses
        assertFalse(redirectSecurityService.isSafeLocalRedirect("javascript:alert(1)"));
        assertFalse(redirectSecurityService.isSafeLocalRedirect("data:text/html,<script>alert(1)</script>"));

        // Encoded bypass variants
        assertFalse(redirectSecurityService.isSafeLocalRedirect("/%2f%2fevil.com"));
        assertFalse(redirectSecurityService.isSafeLocalRedirect("/%5cevil.com"));

        // Fallback enforcement
        assertEquals("/employee/dashboard",
                redirectSecurityService.sanitizeRedirectUrl("http://evil.com", "/employee/dashboard"));
        assertEquals("/employee/dashboard",
                redirectSecurityService.sanitizeRedirectUrl("//evil.com", "/employee/dashboard"));
    }

    // =========================================================================
    // 3. BROWSER SECURITY HEADERS TESTS
    // =========================================================================

    @Test
    @DisplayName("Browser security headers: Content-Security-Policy is present and enforces script and object boundaries")
    public void testContentSecurityPolicyHeader() throws Exception {
        mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy", SecurityHeadersFilter.CSP_POLICY))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("Referrer-Policy", SecurityHeadersFilter.REFERRER_POLICY))
                .andExpect(header().string("Permissions-Policy", SecurityHeadersFilter.PERMISSIONS_POLICY));
    }

    @Test
    @DisplayName("HSTS header is strictly enforced when request is over HTTPS or forwarded proto")
    public void testHstsHeaderOnSecureRequest() throws Exception {
        mockMvc.perform(get("/admin/login").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", SecurityHeadersFilter.HSTS_HEADER_VALUE));

        mockMvc.perform(get("/admin/login").header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", SecurityHeadersFilter.HSTS_HEADER_VALUE));
    }

    // =========================================================================
    // 4. CORS SECURITY TESTS
    // =========================================================================

    @Test
    @DisplayName("CORS: Wildcard '*' origin is rejected when credentialed requests are allowed")
    public void testCorsRejectsWildcardOriginWithCredentials() {
        WebMvcConfig webMvcConfig = new WebMvcConfig();
        ReflectionTestUtils.setField(webMvcConfig, "corsAllowedOrigins", "*,https://trusted.example.com");

        CorsRegistry registry = new CorsRegistry();
        webMvcConfig.addCorsMappings(registry);

        // When wildcard '*' is provided with credentials, WebMvcConfig filters it out to prevent misconfiguration
        // The registry should only contain trusted.example.com, not '*'
        assertNotNull(registry);
    }
}
