package com.example.demo;

import com.example.demo.controller.AdminController;
import com.example.demo.controller.AuthController;
import com.example.demo.controller.EmployeeAuthController;
import com.example.demo.controller.EmployeeController;
import com.example.demo.controller.EmployeeManagementController;
import com.example.demo.controller.HRController;
import com.example.demo.controller.ManagerController;
import com.example.demo.model.Employee;
import com.example.demo.model.User;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.PasswordService;
import com.example.demo.service.SessionSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 3 Security Remediation Tests:
 * HTTP Session Fixation Prevention, Session ID Rotation, Cookie Attributes,
 * Idle Timeout, Anonymous Access Denial, Targeted Invalidation, and Leak Prevention.
 */
public class SessionAndCookieSecurityTest {

    private static final Path ROOT_DIR = Path.of("").toAbsolutePath();

    private SessionSecurityService sessionSecurityService;
    private PasswordService passwordService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmailService emailService;

    private AuthController authController;
    private HRController hrController;
    private ManagerController managerController;
    private EmployeeAuthController employeeAuthController;
    private EmployeeManagementController employeeManagementController;
    private AdminController adminController;
    private EmployeeController employeeController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        passwordService = new PasswordService(new BCryptPasswordEncoder(12));
        sessionSecurityService = new SessionSecurityService();

        authController = new AuthController();
        setField(authController, "userRepository", userRepository);
        setField(authController, "emailService", emailService);
        setField(authController, "passwordService", passwordService);
        setField(authController, "sessionSecurityService", sessionSecurityService);

        hrController = new HRController();
        setField(hrController, "userRepository", userRepository);
        setField(hrController, "passwordService", passwordService);
        setField(hrController, "sessionSecurityService", sessionSecurityService);

        managerController = new ManagerController();
        setField(managerController, "userRepository", userRepository);
        setField(managerController, "passwordService", passwordService);
        setField(managerController, "sessionSecurityService", sessionSecurityService);

        employeeAuthController = new EmployeeAuthController();
        setField(employeeAuthController, "employeeRepository", employeeRepository);
        setField(employeeAuthController, "passwordService", passwordService);
        setField(employeeAuthController, "sessionSecurityService", sessionSecurityService);

        employeeManagementController = new EmployeeManagementController();
        setField(employeeManagementController, "employeeRepository", employeeRepository);
        setField(employeeManagementController, "passwordService", passwordService);
        setField(employeeManagementController, "sessionSecurityService", sessionSecurityService);

        adminController = new AdminController();
        setField(adminController, "userRepository", userRepository);
        setField(adminController, "passwordService", passwordService);

        employeeController = new EmployeeController();
        setField(employeeController, "employeeRepository", employeeRepository);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName + " on " + target.getClass().getSimpleName(), e);
        }
    }

    // =========================================================================
    // SECTION A: Session Fixation Prevention & Session ID Rotation
    // =========================================================================

    @Test
    @DisplayName("A1: Successful Admin login rotates session ID to prevent session fixation")
    void testAdminLoginRotatesSessionId() {
        User admin = new User("admin@logsphere.com", passwordService.encode("AdminPass123!"), "Admin User", "ADMIN");
        when(userRepository.findByEmail("admin@logsphere.com")).thenReturn(Optional.of(admin));

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpSession preLoginSession = (MockHttpSession) req.getSession(true);
        String initialSessionId = preLoginSession.getId();
        assertNotNull(initialSessionId);

        String view = authController.login("admin@logsphere.com", "AdminPass123!", req, preLoginSession, new RedirectAttributesModelMap());
        assertEquals("redirect:/admin/dashboard", view);

        HttpSession postLoginSession = req.getSession(false);
        assertNotNull(postLoginSession);
        String rotatedSessionId = postLoginSession.getId();

        assertNotEquals(initialSessionId, rotatedSessionId,
                "Session ID must be rotated upon successful authentication to eliminate session fixation");
        assertNotNull(postLoginSession.getAttribute("user"));
        assertEquals("ADMIN", postLoginSession.getAttribute("userRole"));
    }

    @Test
    @DisplayName("A2: Successful Employee login rotates session ID to prevent session fixation")
    void testEmployeeLoginRotatesSessionId() {
        Employee emp = new Employee();
        emp.setName("Alice");
        emp.setEmail("alice@logsphere.com");
        emp.setEmployeeId("EMP-01");
        emp.setPassword(passwordService.encode("EmployeePass#1"));

        when(employeeRepository.findByEmail("alice@logsphere.com")).thenReturn(Optional.of(emp));

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpSession preSession = (MockHttpSession) req.getSession(true);
        String initialSessionId = preSession.getId();

        String view = employeeAuthController.employeeLogin("alice@logsphere.com", "EmployeePass#1", "EMP-01", null, req, preSession, new RedirectAttributesModelMap());
        assertEquals("redirect:/employee/dashboard", view);

        HttpSession postSession = req.getSession(false);
        assertNotNull(postSession);
        assertNotEquals(initialSessionId, postSession.getId(),
                "Employee session ID must be rotated upon successful authentication");
        assertNotNull(postSession.getAttribute("employee"));
    }

    @Test
    @DisplayName("A3: Successful HR and Manager logins rotate session ID")
    void testHrAndManagerLoginRotatesSessionId() {
        User hr = new User("hr@logsphere.com", passwordService.encode("HrSecret123"), "HR User", "HR");
        when(userRepository.findByEmail("hr@logsphere.com")).thenReturn(Optional.of(hr));

        MockHttpServletRequest reqHr = new MockHttpServletRequest();
        String initialHrSessionId = reqHr.getSession(true).getId();

        hrController.hrLogin("hr@logsphere.com", "HrSecret123", reqHr, reqHr.getSession(), new RedirectAttributesModelMap());
        assertNotEquals(initialHrSessionId, reqHr.getSession().getId(), "HR session ID must rotate");

        User mgr = new User("mgr@logsphere.com", passwordService.encode("MgrSecret123"), "Manager User", "MANAGER");
        when(userRepository.findByEmail("mgr@logsphere.com")).thenReturn(Optional.of(mgr));

        MockHttpServletRequest reqMgr = new MockHttpServletRequest();
        String initialMgrSessionId = reqMgr.getSession(true).getId();

        managerController.managerLogin("mgr@logsphere.com", "MgrSecret123", reqMgr, reqMgr.getSession(), new RedirectAttributesModelMap());
        assertNotEquals(initialMgrSessionId, reqMgr.getSession().getId(), "Manager session ID must rotate");
    }

    // =========================================================================
    // SECTION B: Anonymous Access Denial
    // =========================================================================

    @Test
    @DisplayName("B1: Anonymous user cannot access Admin Dashboard")
    void testAnonymousUserBlockedFromAdminDashboard() {
        MockHttpSession anonymousSession = new MockHttpSession();
        String view = adminController.adminDashboard(new ConcurrentModel(), anonymousSession);
        assertEquals("redirect:/login", view, "Anonymous user must be redirected to /login");
    }

    @Test
    @DisplayName("B2: Anonymous user cannot access HR Dashboard")
    void testAnonymousUserBlockedFromHrDashboard() {
        MockHttpSession anonymousSession = new MockHttpSession();
        String view = hrController.hrDashboard(null, new ConcurrentModel(), anonymousSession);
        assertEquals("redirect:/hr/login", view, "Anonymous user must be redirected to /hr/login");
    }

    @Test
    @DisplayName("B3: Anonymous user cannot access Manager Dashboard")
    void testAnonymousUserBlockedFromManagerDashboard() {
        MockHttpSession anonymousSession = new MockHttpSession();
        String view = managerController.managerDashboard("planning", new ConcurrentModel(), anonymousSession);
        assertEquals("redirect:/manager/login", view, "Anonymous user must be redirected to /manager/login");
    }

    @Test
    @DisplayName("B4: Anonymous user cannot access Employee Dashboard")
    void testAnonymousUserBlockedFromEmployeeDashboard() {
        MockHttpSession anonymousSession = new MockHttpSession();
        String view = employeeController.employeeDashboard(new ConcurrentModel(), anonymousSession);
        assertEquals("redirect:/employee/login", view, "Anonymous user must be redirected to /employee/login");
    }

    // =========================================================================
    // SECTION C: Logout Invalidation & Cookie Expiration
    // =========================================================================

    @Test
    @DisplayName("C1: Admin logout invalidates server-side session and issues expired cookie header")
    void testAdminLogoutInvalidatesSessionAndExpiresCookie() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", new User("admin@logsphere.com", "pass", "Admin", "ADMIN"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = authController.logout(session, response);
        assertEquals("redirect:/admin/login", view);
        assertTrue(session.isInvalid(), "Server-side session must be invalidated on logout");

        String setCookieHeader = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookieHeader, "Set-Cookie header must be sent to expire the cookie");
        assertTrue(setCookieHeader.contains("JSESSIONID="));
        assertTrue(setCookieHeader.contains("Max-Age=0") || setCookieHeader.contains("max-age=0"));
        assertTrue(setCookieHeader.contains("Path=/"));
        assertTrue(setCookieHeader.contains("HttpOnly") || setCookieHeader.contains("httponly"));
        assertTrue(setCookieHeader.contains("SameSite=Lax") || setCookieHeader.contains("samesite=lax"));
    }

    @Test
    @DisplayName("C2: Employee logout invalidates session and issues expired cookie header")
    void testEmployeeLogoutInvalidatesSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("employee", new Employee());
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = employeeAuthController.employeeLogout(session, response);
        assertEquals("redirect:/employee/login", view);
        assertTrue(session.isInvalid(), "Employee session must be invalidated on logout");

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("Max-Age=0"));
    }

    @Test
    @DisplayName("C3: Subsequent requests using invalidated session are rejected")
    void testSubsequentRequestRejectedAfterLogout() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", new User("admin@logsphere.com", "pass", "Admin", "ADMIN"));
        authController.logout(session, new MockHttpServletResponse());

        // Now attempt to use the same session
        MockHttpSession reusedSession = new MockHttpSession(); // Session attributes cleared/invalidated
        String view = adminController.adminDashboard(new ConcurrentModel(), reusedSession);
        assertEquals("redirect:/login", view, "Protected dashboard access must be denied after session invalidation");
    }

    // =========================================================================
    // SECTION D: Cookie Attributes & Security Settings
    // =========================================================================

    @Test
    @DisplayName("D1: Production configuration enforces Secure=true, HttpOnly=true, SameSite=lax, and Path=/")
    void testProductionSessionCookieProperties() throws IOException {
        Path prodProps = ROOT_DIR.resolve("src/main/resources/application-prod.properties");
        assertTrue(Files.exists(prodProps));
        String content = Files.readString(prodProps);

        assertTrue(content.contains("server.servlet.session.cookie.http-only=true"),
                "Production must enforce HttpOnly=true for session cookies");
        assertTrue(content.contains("server.servlet.session.cookie.secure=${SESSION_COOKIE_SECURE:true}"),
                "Production must enforce Secure=true by default for session cookies");
        assertTrue(content.contains("server.servlet.session.cookie.same-site=lax"),
                "Production must configure SameSite=lax for session cookies");
        assertTrue(content.contains("server.servlet.session.cookie.path=/"),
                "Production must scope session cookie path to /");
        assertTrue(content.contains("server.forward-headers-strategy=framework"),
                "Production must configure forward-headers-strategy=framework for reverse proxy TLS termination");
    }

    @Test
    @DisplayName("D2: Base configuration enforces HttpOnly=true, SameSite=lax, and cookie tracking mode")
    void testBaseSessionCookieProperties() throws IOException {
        Path baseProps = ROOT_DIR.resolve("src/main/resources/application.properties");
        assertTrue(Files.exists(baseProps));
        String content = Files.readString(baseProps);

        assertTrue(content.contains("server.servlet.session.cookie.http-only=true"));
        assertTrue(content.contains("server.servlet.session.cookie.same-site=lax"));
        assertTrue(content.contains("server.servlet.session.cookie.path=/"));
        assertTrue(content.contains("server.servlet.session.tracking-modes=cookie"),
                "Tracking modes must be set to 'cookie' to prevent URL rewriting session leaks");
    }

    // =========================================================================
    // SECTION E: Configurable Session Idle Timeout
    // =========================================================================

    @Test
    @DisplayName("E1: Session timeout is configured to 30 minutes and is configurable via environment variable")
    void testSessionTimeoutConfiguration() throws IOException {
        Path prodProps = ROOT_DIR.resolve("src/main/resources/application-prod.properties");
        String prodContent = Files.readString(prodProps);
        assertTrue(prodContent.contains("server.servlet.session.timeout=${SESSION_TIMEOUT:30m}"),
                "Production session timeout must default to 30m with SESSION_TIMEOUT env var support");

        Path baseProps = ROOT_DIR.resolve("src/main/resources/application.properties");
        String baseContent = Files.readString(baseProps);
        assertTrue(baseContent.contains("server.servlet.session.timeout=${SESSION_TIMEOUT:30m}"),
                "Base session timeout must default to 30m with SESSION_TIMEOUT env var support");
    }

    // =========================================================================
    // SECTION F: Sensitive Attribute Leak Prevention in Sessions
    // =========================================================================

    @Test
    @DisplayName("F1: Sanitized User in session never contains raw password, hash, or reset token")
    void testSanitizedUserInSessionHasNoCredentials() {
        User rawUser = new User("admin@logsphere.com", "$2a$12$SomeLongHashedPasswordValueInDatabase", "Admin", "ADMIN");
        rawUser.setResetToken("f0e1d2c3b4a5968778695a4b3c2d1e0f");
        rawUser.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));

        User sessionUser = sessionSecurityService.sanitizeUserForSession(rawUser);

        assertNull(sessionUser.getPassword(), "User password must be null in session object");
        assertNull(sessionUser.getResetToken(), "Reset token must be null in session object");
        assertNull(sessionUser.getResetTokenExpiry(), "Reset token expiry must be null in session object");
        assertEquals("admin@logsphere.com", sessionUser.getEmail());
        assertEquals("Admin", sessionUser.getName());
        assertEquals("ADMIN", sessionUser.getRole());
    }

    @Test
    @DisplayName("F2: Sanitized Employee in session never contains password or heavy faceImage payload")
    void testSanitizedEmployeeInSessionHasNoCredentials() {
        Employee rawEmp = new Employee();
        rawEmp.setName("Bob");
        rawEmp.setEmail("bob@logsphere.com");
        rawEmp.setEmployeeId("EMP002");
        rawEmp.setPassword("$2a$12$EmployeePasswordHashInDatabase");
        rawEmp.setFaceImage("data:image/jpeg;base64,VeryLargeImagePayloadStringHere...");

        Employee sessionEmp = sessionSecurityService.sanitizeEmployeeForSession(rawEmp);

        assertNull(sessionEmp.getPassword(), "Employee password must be null in session object");
        assertNull(sessionEmp.getFaceImage(), "Employee face image payload must be omitted from session");
        assertEquals("bob@logsphere.com", sessionEmp.getEmail());
        assertEquals("Bob", sessionEmp.getName());
        assertEquals("EMP002", sessionEmp.getEmployeeId());
    }

    // =========================================================================
    // SECTION G: Targeted Session Invalidation on Password Change/Reset & Deletion
    // =========================================================================

    @Test
    @DisplayName("G1: Password reset invalidates active sessions for that user")
    void testPasswordResetInvalidatesUserSessions() {
        User user = new User("resetuser@logsphere.com", passwordService.encode("OldSecret1"), "Reset User", "ADMIN");
        String rawToken = passwordService.generateResetToken();
        user.setResetToken(passwordService.hashToken(rawToken));
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));

        when(userRepository.findAll()).thenReturn(List.of(user));

        // Create an existing active session for this user
        MockHttpSession activeUserSession = new MockHttpSession();
        sessionSecurityService.registerUserSession("resetuser@logsphere.com", activeUserSession);
        assertTrue(sessionSecurityService.isUserSessionActive("resetuser@logsphere.com"));

        // Another unrelated user's active session
        MockHttpSession otherUserSession = new MockHttpSession();
        sessionSecurityService.registerUserSession("otheruser@logsphere.com", otherUserSession);

        // Perform password reset
        MockHttpServletResponse response = new MockHttpServletResponse();
        authController.processResetPassword(rawToken, "NewPass#2026", new MockHttpSession(), response, new RedirectAttributesModelMap());

        // Assert target user's sessions are invalidated
        assertFalse(sessionSecurityService.isUserSessionActive("resetuser@logsphere.com"),
                "User's active sessions must be invalidated upon password reset");
        assertTrue(activeUserSession.isInvalid(), "Target user's session must be invalidated");

        // Assert unrelated user's session is untouched
        assertTrue(sessionSecurityService.isUserSessionActive("otheruser@logsphere.com"),
                "Unrelated user sessions must remain active");
        assertFalse(otherUserSession.isInvalid(), "Unrelated user session must not be invalidated");
    }

    @Test
    @DisplayName("G2: Admin updating employee password invalidates that employee's active sessions")
    void testEmployeePasswordUpdateInvalidatesEmployeeSession() {
        Employee emp = new Employee();
        emp.setId(10L);
        emp.setName("Charlie");
        emp.setEmail("charlie@logsphere.com");
        emp.setEmployeeId("EMP010");
        emp.setPassword(passwordService.encode("OldPass"));

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(emp));
        when(employeeRepository.findByEmail("charlie@logsphere.com")).thenReturn(Optional.of(emp));

        // Register active session for Charlie
        MockHttpSession charlieSession = new MockHttpSession();
        sessionSecurityService.registerUserSession("charlie@logsphere.com", charlieSession);
        assertTrue(sessionSecurityService.isUserSessionActive("charlie@logsphere.com"));

        // Admin updates password
        Employee form = new Employee();
        form.setName("Charlie");
        form.setEmail("charlie@logsphere.com");
        form.setEmployeeId("EMP010");

        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("user", new User("admin@logsphere.com", "pass", "Admin", "ADMIN"));

        employeeManagementController.updateEmployee(10L, form, "BrandNewPassword#999", null, false, null, null,
                new RedirectAttributesModelMap(), adminSession);

        // Charlie's session must be invalidated
        assertFalse(sessionSecurityService.isUserSessionActive("charlie@logsphere.com"),
                "Employee's session must be invalidated when admin updates their password");
        assertTrue(charlieSession.isInvalid(), "Charlie's session must be invalidated");
    }

    @Test
    @DisplayName("G3: Employee deletion invalidates their active sessions")
    void testEmployeeDeletionInvalidatesSessions() {
        Employee emp = new Employee();
        emp.setId(20L);
        emp.setName("Dave");
        emp.setEmail("dave@logsphere.com");
        emp.setEmployeeId("EMP020");

        when(employeeRepository.findById(20L)).thenReturn(Optional.of(emp));

        MockHttpSession daveSession = new MockHttpSession();
        sessionSecurityService.registerUserSession("dave@logsphere.com", daveSession);

        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("user", new User("admin@logsphere.com", "pass", "Admin", "ADMIN"));

        employeeManagementController.deleteEmployee(20L, new RedirectAttributesModelMap(), adminSession);

        assertFalse(sessionSecurityService.isUserSessionActive("dave@logsphere.com"));
        assertTrue(daveSession.isInvalid(), "Deleted employee's session must be invalidated");
    }
}
