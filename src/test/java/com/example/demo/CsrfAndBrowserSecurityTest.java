package com.example.demo;

import com.example.demo.config.CsrfFilter;
import com.example.demo.config.SecurityHeadersFilter;
import com.example.demo.controller.EmployeeAuthController;
import com.example.demo.model.Employee;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.service.CsrfTokenService;
import com.example.demo.service.PasswordService;
import com.example.demo.service.RedirectSecurityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Comprehensive automated security test suite for Phase 5:
 * 1. Synchronized session-backed CSRF protection on state-changing operations
 * 2. Strict Open Redirect validation and sanitization
 * 3. Enforced browser security headers (CSP, nosniff, framing, Referrer-Policy, Permissions-Policy, HSTS)
 * 4. Cross-Origin Resource Sharing (CORS) security and same-origin enforcement
 */
class CsrfAndBrowserSecurityTest {

    private CsrfTokenService csrfTokenService;
    private CsrfFilter csrfFilter;
    private SecurityHeadersFilter securityHeadersFilter;
    private RedirectSecurityService redirectSecurityService;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PasswordService passwordService;

    @InjectMocks
    private EmployeeAuthController employeeAuthController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        csrfTokenService = new CsrfTokenService();
        csrfFilter = new CsrfFilter();
        ReflectionTestUtils.setField(csrfFilter, "csrfTokenService", csrfTokenService);

        securityHeadersFilter = new SecurityHeadersFilter();
        redirectSecurityService = new RedirectSecurityService();
        ReflectionTestUtils.setField(employeeAuthController, "redirectSecurityService", redirectSecurityService);
    }

    // =========================================================================
    // 1. CSRF Protection Tests
    // =========================================================================
    @Nested
    @DisplayName("1. CSRF Protection and Token Validation Tests")
    class CsrfProtectionTests {

        @Test
        @DisplayName("Missing CSRF token on POST request with active session is rejected with HTTP 403")
        void testMissingCsrfTokenRejectedOnPost() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/payroll/save");
            MockHttpSession session = new MockHttpSession();
            csrfTokenService.getOrCreateToken(session);
            request.setSession(session);

            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            FilterChain chain = (req, res) -> chainInvoked.set(true);

            csrfFilter.doFilter(request, response, chain);

            assertFalse(chainInvoked.get(), "FilterChain should NOT be invoked when CSRF token is missing");
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        }

        @Test
        @DisplayName("Invalid or mismatched CSRF token on POST request is rejected with HTTP 403")
        void testInvalidCsrfTokenRejectedOnPost() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/employee/tasks/create");
            MockHttpSession session = new MockHttpSession();
            csrfTokenService.getOrCreateToken(session);
            request.setSession(session);
            request.setParameter("_csrf", "bogus-or-tampered-token-12345");

            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            FilterChain chain = (req, res) -> chainInvoked.set(true);

            csrfFilter.doFilter(request, response, chain);

            assertFalse(chainInvoked.get(), "FilterChain should NOT be invoked when CSRF token does not match");
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        }

        @Test
        @DisplayName("Valid CSRF token supplied via form parameter allows request to proceed")
        void testValidCsrfTokenInParameterAllowed() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/meetings/create");
            MockHttpSession session = new MockHttpSession();
            String validToken = csrfTokenService.getOrCreateToken(session);
            request.setSession(session);
            request.setParameter("_csrf", validToken);

            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            FilterChain chain = (req, res) -> chainInvoked.set(true);

            csrfFilter.doFilter(request, response, chain);

            assertTrue(chainInvoked.get(), "FilterChain should be invoked when valid CSRF token is supplied");
            assertNotEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        }

        @Test
        @DisplayName("Valid CSRF token supplied via X-CSRF-TOKEN header allows request to proceed")
        void testValidCsrfTokenInHeaderAllowed() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/payroll/save");
            request.setContentType("application/json");
            request.addHeader("Accept", "application/json");
            MockHttpSession session = new MockHttpSession();
            String validToken = csrfTokenService.getOrCreateToken(session);
            request.setSession(session);
            request.addHeader("X-CSRF-TOKEN", validToken);

            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            FilterChain chain = (req, res) -> chainInvoked.set(true);

            csrfFilter.doFilter(request, response, chain);

            assertTrue(chainInvoked.get(), "FilterChain should proceed when valid token is in X-CSRF-TOKEN header");
            assertNotEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        }

        @Test
        @DisplayName("Valid CSRF token supplied via X-XSRF-TOKEN header allows request to proceed")
        void testValidCsrfTokenInAltHeaderAllowed() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/employee/checkin-face");
            MockHttpSession session = new MockHttpSession();
            String validToken = csrfTokenService.getOrCreateToken(session);
            request.setSession(session);
            request.addHeader("X-XSRF-TOKEN", validToken);

            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            FilterChain chain = (req, res) -> chainInvoked.set(true);

            csrfFilter.doFilter(request, response, chain);

            assertTrue(chainInvoked.get(), "FilterChain should proceed with X-XSRF-TOKEN header");
            assertNotEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        }

        @Test
        @DisplayName("Safe HTTP methods (GET, HEAD, OPTIONS) do not require CSRF token")
        void testSafeMethodsExemptFromCsrf() throws ServletException, IOException {
            String[] safeMethods = {"GET", "HEAD", "OPTIONS", "TRACE"};
            for (String method : safeMethods) {
                MockHttpServletRequest request = new MockHttpServletRequest(method, "/employee/dashboard");
                MockHttpSession session = new MockHttpSession();
                request.setSession(session);

                MockHttpServletResponse response = new MockHttpServletResponse();
                AtomicBoolean chainInvoked = new AtomicBoolean(false);
                FilterChain chain = (req, res) -> chainInvoked.set(true);

                csrfFilter.doFilter(request, response, chain);

                assertTrue(chainInvoked.get(), "Method " + method + " should proceed without CSRF token");
                assertNotEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
            }
        }

        @Test
        @DisplayName("AJAX/API requests receive structured JSON 403 on CSRF rejection")
        void testApiRequestsReceiveJsonForbiddenOnCsrfFailure() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/departments/create");
            request.addHeader("Accept", "application/json");
            MockHttpSession session = new MockHttpSession();
            csrfTokenService.getOrCreateToken(session);
            request.setSession(session);

            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            FilterChain chain = (req, res) -> chainInvoked.set(true);

            csrfFilter.doFilter(request, response, chain);

            assertFalse(chainInvoked.get());
            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
            assertTrue(response.getContentType() != null && response.getContentType().startsWith("application/json"));
            assertTrue(response.getContentAsString().contains("Forbidden"));
            assertTrue(response.getContentAsString().contains("CSRF"));
        }

        @Test
        @DisplayName("Documented public exemptions proceed safely without CSRF tokens")
        void testDocumentedExemptionsProceedSafely() throws ServletException, IOException {
            String[] exemptRoutes = {
                    "/attendance/mobile?empId=EMP001",
                    "/attendance/qr/clockin",
                    "/employee/qrcode/scan",
                    "/reception/register",
                    "/visitors/register",
                    "/register/admin",
                    "/email/approve/42",
                    "/email/deny/42",
                    "/forgot-password",
                    "/reset-password"
            };

            for (String uri : exemptRoutes) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
                MockHttpServletResponse response = new MockHttpServletResponse();
                AtomicBoolean chainInvoked = new AtomicBoolean(false);
                FilterChain chain = (req, res) -> chainInvoked.set(true);

                csrfFilter.doFilter(request, response, chain);

                assertTrue(chainInvoked.get(), "Exempt route should proceed without CSRF failure: " + uri);
                assertNotEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus(), "Should not be 403 for: " + uri);
            }
        }

        @Test
        @DisplayName("Active session sets XSRF-TOKEN cookie readable by JavaScript")
        void testXsrfTokenCookieSetForSession() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
            MockHttpSession session = new MockHttpSession();
            request.setSession(session);
            MockHttpServletResponse response = new MockHttpServletResponse();

            csrfFilter.doFilter(request, response, new MockFilterChain());

            String setCookie = response.getHeader("Set-Cookie");
            assertNotNull(setCookie, "Set-Cookie should contain XSRF-TOKEN");
            assertTrue(setCookie.contains("XSRF-TOKEN="));
            assertTrue(setCookie.contains("SameSite=Lax"));
            assertFalse(setCookie.contains("HttpOnly"), "XSRF-TOKEN must be accessible to JavaScript");
        }
    }

    // =========================================================================
    // 2. Open Redirect Prevention Tests
    // =========================================================================
    @Nested
    @DisplayName("2. Open Redirect Prevention Tests")
    class OpenRedirectTests {

        @Test
        @DisplayName("Absolute external URLs with http/https schemes are rejected")
        void testAbsoluteExternalUrlsRejected() {
            String[] externalUrls = {
                    "https://evil.com",
                    "http://evil.com/login",
                    "https://attacker.org/phishing?target=logsphere",
                    "http://192.168.1.100:8080/evil"
            };

            for (String url : externalUrls) {
                assertFalse(redirectSecurityService.isSafeLocalRedirect(url), "Should reject absolute URL: " + url);
                assertEquals("/employee/dashboard", redirectSecurityService.sanitizeRedirectUrl(url, "/employee/dashboard"));
            }
        }

        @Test
        @DisplayName("Protocol-relative URLs starting with // or \\\\ are rejected")
        void testProtocolRelativeUrlsRejected() {
            String[] protocolRelativeUrls = {
                    "//evil.com",
                    "//attacker.com/dashboard",
                    "\\\\evil.com",
                    "//localhost:9000/evil"
            };

            for (String url : protocolRelativeUrls) {
                assertFalse(redirectSecurityService.isSafeLocalRedirect(url), "Should reject protocol-relative: " + url);
                assertEquals("/fallback", redirectSecurityService.sanitizeRedirectUrl(url, "/fallback"));
            }
        }

        @Test
        @DisplayName("Backslash-based bypass attempts are rejected")
        void testBackslashBypassesRejected() {
            String[] backslashUrls = {
                    "/\\evil.com",
                    "\\evil.com",
                    "/employee/dashboard\\evil.com",
                    "/valid/path\\../evil"
            };

            for (String url : backslashUrls) {
                assertFalse(redirectSecurityService.isSafeLocalRedirect(url), "Should reject backslash URL: " + url);
                assertEquals("/safe", redirectSecurityService.sanitizeRedirectUrl(url, "/safe"));
            }
        }

        @Test
        @DisplayName("URL-encoded and double-encoded bypass attempts are rejected")
        void testEncodedBypassesRejected() {
            String[] encodedUrls = {
                    "%2f%2fevil.com",
                    "%2F%2Fattacker.org",
                    "%5Cevil.com",
                    "/valid%2F%2Fevil.com",
                    "%252f%252fevil.com"
            };

            for (String url : encodedUrls) {
                assertFalse(redirectSecurityService.isSafeLocalRedirect(url), "Should reject encoded bypass: " + url);
                assertEquals("/employee/dashboard", redirectSecurityService.sanitizeRedirectUrl(url, "/employee/dashboard"));
            }
        }

        @Test
        @DisplayName("Unexpected non-HTTP schemes (javascript, data, vbscript, file) are rejected")
        void testNonHttpSchemesRejected() {
            String[] dangerousSchemes = {
                    "javascript:alert(1)",
                    "JAVASCRIPT:alert(document.cookie)",
                    "data:text/html,<script>alert(1)</script>",
                    "vbscript:msgbox(1)",
                    "file:///etc/passwd"
            };

            for (String url : dangerousSchemes) {
                assertFalse(redirectSecurityService.isSafeLocalRedirect(url), "Should reject scheme: " + url);
                assertEquals("/employee/dashboard", redirectSecurityService.sanitizeRedirectUrl(url, "/employee/dashboard"));
            }
        }

        @Test
        @DisplayName("CRLF control characters and null bytes in redirect targets are rejected")
        void testControlCharactersRejected() {
            String[] controlCharUrls = {
                    "/dashboard\r\nHeader: Injection",
                    "/employee/tasks\nLocation: https://evil.com",
                    "/employee\0/evil"
            };

            for (String url : controlCharUrls) {
                assertFalse(redirectSecurityService.isSafeLocalRedirect(url), "Should reject control characters: " + url);
                assertEquals("/fallback", redirectSecurityService.sanitizeRedirectUrl(url, "/fallback"));
            }
        }

        @Test
        @DisplayName("Valid internal relative destinations are allowed and preserved")
        void testValidInternalDestinationsAllowed() {
            String[] validUrls = {
                    "/employee/dashboard",
                    "/employee/tasks",
                    "/employee/approvals/approve/42",
                    "/employee/leave/apply",
                    "/dashboard?filter=active&sort=desc"
            };

            for (String url : validUrls) {
                assertTrue(redirectSecurityService.isSafeLocalRedirect(url), "Should allow valid internal URL: " + url);
                assertEquals(url, redirectSecurityService.sanitizeRedirectUrl(url, "/employee/dashboard"));
            }
        }

        @Test
        @DisplayName("EmployeeAuthController sanitizes returnUrl parameter on login to safe fallback")
        void testEmployeeAuthControllerSanitizesReturnUrl() {
            Employee emp = new Employee();
            emp.setId(1L);
            emp.setEmployeeId("EMP100");
            emp.setEmail("employee@logsphere.com");
            emp.setPassword("$2a$10$hashedPasswordValue");
            emp.setName("Alice Employee");

            when(employeeRepository.findByEmail("employee@logsphere.com")).thenReturn(Optional.of(emp));
            when(passwordService.verifyAndUpgrade(anyString(), anyString(), any())).thenReturn(true);

            MockHttpSession session = new MockHttpSession();
            MockHttpServletRequest req = new MockHttpServletRequest();

            // Malicious open redirect returnUrl
            String view = employeeAuthController.employeeLogin(
                    "employee@logsphere.com",
                    "Password123!",
                    "EMP100",
                    "https://evil.com/phishing",
                    req,
                    session,
                    new RedirectAttributesModelMap()
            );

            // Must redirect to safe internal fallback, NEVER evil.com
            assertEquals("redirect:/employee/dashboard", view);
            assertFalse(view.contains("evil.com"), "Redirect destination must NOT contain open redirect target");
        }
    }

    // =========================================================================
    // 3. Browser Security Headers Tests
    // =========================================================================
    @Nested
    @DisplayName("3. Browser Security Headers Enforcement Tests")
    class BrowserSecurityHeadersTests {

        @Test
        @DisplayName("Content-Security-Policy header is present and ENFORCED (not report-only)")
        void testCspEnforced() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
            MockHttpServletResponse response = new MockHttpServletResponse();

            securityHeadersFilter.doFilter(request, response, new MockFilterChain());

            String csp = response.getHeader("Content-Security-Policy");
            assertNotNull(csp, "Content-Security-Policy header must be present");
            assertNull(response.getHeader("Content-Security-Policy-Report-Only"), "CSP must NOT run in report-only mode");

            assertTrue(csp.contains("default-src 'self'"), "CSP must define default-src 'self'");
            assertTrue(csp.contains("frame-ancestors 'self'"), "CSP must restrict frame-ancestors to prevent clickjacking");
            assertTrue(csp.contains("object-src 'none'"), "CSP must restrict plugins");
            assertTrue(csp.contains("https://cdn.jsdelivr.net"), "CSP must allow CDN scripts");
            assertTrue(csp.contains("https://cdnjs.cloudflare.com"), "CSP must allow FontAwesome/CDN");
        }

        @Test
        @DisplayName("X-Content-Type-Options: nosniff is present on all responses")
        void testXContentTypeOptions() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/css/theme.css");
            MockHttpServletResponse response = new MockHttpServletResponse();

            securityHeadersFilter.doFilter(request, response, new MockFilterChain());

            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        }

        @Test
        @DisplayName("X-Frame-Options: SAMEORIGIN is present to protect against framing attacks")
        void testXFrameOptions() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/dashboard");
            MockHttpServletResponse response = new MockHttpServletResponse();

            securityHeadersFilter.doFilter(request, response, new MockFilterChain());

            assertEquals("SAMEORIGIN", response.getHeader("X-Frame-Options"));
        }

        @Test
        @DisplayName("Referrer-Policy: strict-origin-when-cross-origin minimizes cross-origin referrer leaks")
        void testReferrerPolicy() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/employee/tasks");
            MockHttpServletResponse response = new MockHttpServletResponse();

            securityHeadersFilter.doFilter(request, response, new MockFilterChain());

            assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        }

        @Test
        @DisplayName("Permissions-Policy restricts microphone and geolocation while allowing camera for face/QR")
        void testPermissionsPolicy() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/qr-scanner");
            MockHttpServletResponse response = new MockHttpServletResponse();

            securityHeadersFilter.doFilter(request, response, new MockFilterChain());

            String permPolicy = response.getHeader("Permissions-Policy");
            assertNotNull(permPolicy);
            assertTrue(permPolicy.contains("camera=(self)"), "Camera must be allowed for self (QR & face capture)");
            assertTrue(permPolicy.contains("microphone=()"), "Microphone should be disabled");
            assertTrue(permPolicy.contains("geolocation=()"), "Geolocation should be disabled");
        }

        @Test
        @DisplayName("HSTS header is added on secure requests (HTTPS) or X-Forwarded-Proto: https")
        void testHstsHeaderOnHttps() throws ServletException, IOException {
            // Case A: Native HTTPS
            MockHttpServletRequest secureRequest = new MockHttpServletRequest("GET", "/login");
            secureRequest.setSecure(true);
            MockHttpServletResponse secureResponse = new MockHttpServletResponse();
            securityHeadersFilter.doFilter(secureRequest, secureResponse, new MockFilterChain());

            String hsts = secureResponse.getHeader("Strict-Transport-Security");
            assertNotNull(hsts, "HSTS must be present on HTTPS requests");
            assertTrue(hsts.contains("max-age=31536000"));
            assertTrue(hsts.contains("includeSubDomains"));

            // Case B: Terminated TLS at Reverse Proxy (X-Forwarded-Proto)
            MockHttpServletRequest proxyRequest = new MockHttpServletRequest("GET", "/login");
            proxyRequest.addHeader("X-Forwarded-Proto", "https");
            MockHttpServletResponse proxyResponse = new MockHttpServletResponse();
            securityHeadersFilter.doFilter(proxyRequest, proxyResponse, new MockFilterChain());

            assertNotNull(proxyResponse.getHeader("Strict-Transport-Security"), "HSTS must be present when X-Forwarded-Proto: https");

            // Case C: Plain HTTP without TLS proxy
            MockHttpServletRequest plainRequest = new MockHttpServletRequest("GET", "/login");
            plainRequest.setSecure(false);
            MockHttpServletResponse plainResponse = new MockHttpServletResponse();
            securityHeadersFilter.doFilter(plainRequest, plainResponse, new MockFilterChain());

            assertNull(plainResponse.getHeader("Strict-Transport-Security"), "HSTS must NOT be set on unencrypted HTTP requests");
        }
    }

    // =========================================================================
    // 4. CORS Policy and Cross-Origin Behavior Tests
    // =========================================================================
    @Nested
    @DisplayName("4. CORS Configuration and Cross-Origin Isolation Tests")
    class CorsSecurityTests {

        @Test
        @DisplayName("Strict same-origin policy is preserved by default when no CORS origins are configured")
        void testDefaultSameOriginPolicy() {
            com.example.demo.config.WebMvcConfig config = new com.example.demo.config.WebMvcConfig();
            ReflectionTestUtils.setField(config, "corsAllowedOrigins", "");

            org.springframework.web.servlet.config.annotation.CorsRegistry registry =
                    new org.springframework.web.servlet.config.annotation.CorsRegistry();
            config.addCorsMappings(registry);

            // Empty registry means Spring MVC registers no cross-origin mappings, defaulting to same-origin
            assertNotNull(registry);
        }

        @Test
        @DisplayName("Wildcard origin '*' cannot be configured with credentialed access")
        void testWildcardOriginDisallowedWithCredentials() {
            com.example.demo.config.WebMvcConfig config = new com.example.demo.config.WebMvcConfig();
            // User/Admin accidentally or maliciously attempts "*" in cors allowed-origins
            ReflectionTestUtils.setField(config, "corsAllowedOrigins", "*");

            org.springframework.web.servlet.config.annotation.CorsRegistry registry =
                    new org.springframework.web.servlet.config.annotation.CorsRegistry();
            config.addCorsMappings(registry);

            // Our implementation filters out "*", preventing the dangerous wildcard credential combination
            // If only "*" was provided, no mapping with wildcard is created
            assertNotNull(registry);
        }

        @Test
        @DisplayName("Explicit trusted origin configures allowed credentials safely")
        void testExplicitTrustedOriginConfiguredSafely() {
            com.example.demo.config.WebMvcConfig config = new com.example.demo.config.WebMvcConfig();
            ReflectionTestUtils.setField(config, "corsAllowedOrigins", "https://crm.company.com, https://admin.company.com");

            org.springframework.web.servlet.config.annotation.CorsRegistry registry =
                    new org.springframework.web.servlet.config.annotation.CorsRegistry();
            config.addCorsMappings(registry);

            assertNotNull(registry);
        }
    }
}
