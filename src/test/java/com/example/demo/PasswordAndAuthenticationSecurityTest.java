package com.example.demo;

import com.example.demo.config.PasswordConfig;
import com.example.demo.controller.AdminController;
import com.example.demo.controller.AuthController;
import com.example.demo.controller.EmployeeAuthController;
import com.example.demo.controller.EmployeeManagementController;
import com.example.demo.controller.HRController;
import com.example.demo.controller.ManagerController;
import com.example.demo.model.Employee;
import com.example.demo.model.User;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.PasswordService;
import com.example.demo.service.QRCodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 2 Security Remediation Tests:
 * Password Hashing, Verification, Authentication, Auto-Upgrade Migration,
 * Reset-Token Lifecycle, Boundary and Serialization Safety.
 */
public class PasswordAndAuthenticationSecurityTest {

    private static final Pattern BCRYPT_WORK_FACTOR_12_PATTERN =
            Pattern.compile("^\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$");

    private PasswordConfig passwordConfig;
    private PasswordService passwordService;
    private ObjectMapper objectMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private QRCodeService qrCodeService;

    private AuthController authController;
    private HRController hrController;
    private ManagerController managerController;
    private EmployeeAuthController employeeAuthController;
    private EmployeeManagementController employeeManagementController;
    private AdminController adminController;

    private MockHttpSession session;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        passwordConfig = new PasswordConfig();
        BCryptPasswordEncoder encoder = (BCryptPasswordEncoder) passwordConfig.passwordEncoder();
        passwordService = new PasswordService(encoder);
        objectMapper = new ObjectMapper();

        session = new MockHttpSession();
        request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8083);
        request.setContextPath("");

        // Wire controllers
        authController = new AuthController();
        setField(authController, "userRepository", userRepository);
        setField(authController, "emailService", emailService);
        setField(authController, "passwordService", passwordService);

        hrController = new HRController();
        setField(hrController, "userRepository", userRepository);
        setField(hrController, "passwordService", passwordService);

        managerController = new ManagerController();
        setField(managerController, "userRepository", userRepository);
        setField(managerController, "passwordService", passwordService);

        employeeAuthController = new EmployeeAuthController();
        setField(employeeAuthController, "employeeRepository", employeeRepository);
        setField(employeeAuthController, "passwordService", passwordService);

        employeeManagementController = new EmployeeManagementController();
        setField(employeeManagementController, "employeeRepository", employeeRepository);
        setField(employeeManagementController, "passwordService", passwordService);
        setField(employeeManagementController, "qrCodeService", qrCodeService);

        adminController = new AdminController();
        setField(adminController, "userRepository", userRepository);
        setField(adminController, "passwordService", passwordService);
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
    // SECTION A: PasswordEncoder & PasswordService Unit Tests
    // =========================================================================

    @Test
    @DisplayName("A1: Correct passwords match and incorrect passwords fail")
    void testPasswordMatchingAndMismatch() {
        String raw = "StrongSecret#2026";
        String hash = passwordService.encode(raw);

        assertTrue(passwordService.matches(raw, hash), "Correct password must match hash");
        assertFalse(passwordService.matches("WrongPassword", hash), "Incorrect password must not match hash");
        assertFalse(passwordService.matches("strongsecret#2026", hash), "Password matching must be case sensitive");
    }

    @Test
    @DisplayName("A2: Stored password is never plaintext and adheres to BCrypt work factor 12")
    void testStoredPasswordFormatAndWorkFactor() {
        String raw = "UserPassword123!";
        String hash = passwordService.encode(raw);

        assertNotEquals(raw, hash, "Stored password must not be plaintext");
        assertFalse(hash.contains(raw), "Hash must never contain raw password");
        assertTrue(BCRYPT_WORK_FACTOR_12_PATTERN.matcher(hash).matches(),
                "Hash must match standard BCrypt format with cost factor 12: " + hash);
    }

    @Test
    @DisplayName("A3: Two hashes of the same password produce distinct salt values")
    void testDifferentSaltsGenerated() {
        String raw = "SamePassword123";
        String hash1 = passwordService.encode(raw);
        String hash2 = passwordService.encode(raw);

        assertNotEquals(hash1, hash2, "BCrypt must generate unique salts for each hash");
        assertTrue(passwordService.matches(raw, hash1));
        assertTrue(passwordService.matches(raw, hash2));
    }

    // =========================================================================
    // SECTION B: Authentication & Role-Based Login Integration Tests
    // =========================================================================

    @Test
    @DisplayName("B1: Admin login success and failure")
    void testAdminLogin() {
        User admin = new User("admin@logsphere.com", passwordService.encode("AdminPass123!"), "Admin User", "ADMIN");
        when(userRepository.findByEmail("admin@logsphere.com")).thenReturn(Optional.of(admin));

        RedirectAttributes raSuccess = new RedirectAttributesModelMap();
        String viewSuccess = authController.login("admin@logsphere.com", "AdminPass123!", session, raSuccess);
        assertEquals("redirect:/admin/dashboard", viewSuccess);
        assertNotNull(session.getAttribute("user"));
        assertEquals("ADMIN", session.getAttribute("userRole"));

        // Wrong password
        RedirectAttributes raFail = new RedirectAttributesModelMap();
        String viewFail = authController.login("admin@logsphere.com", "WrongPassword", session, raFail);
        assertEquals("redirect:/admin/login", viewFail);
        assertEquals("Invalid email or password!", raFail.getFlashAttributes().get("error"));

        // Non-existent user
        RedirectAttributes raMissing = new RedirectAttributesModelMap();
        String viewMissing = authController.login("ghost@logsphere.com", "AnyPassword", session, raMissing);
        assertEquals("redirect:/admin/login", viewMissing);
        assertEquals("Invalid email or password!", raMissing.getFlashAttributes().get("error"));
    }

    @Test
    @DisplayName("B2: HR login success and role restriction")
    void testHrLogin() {
        User hr = new User("hr@logsphere.com", passwordService.encode("HrPassword!"), "HR User", "HR");
        when(userRepository.findByEmail("hr@logsphere.com")).thenReturn(Optional.of(hr));

        RedirectAttributes raSuccess = new RedirectAttributesModelMap();
        String viewSuccess = hrController.hrLogin("hr@logsphere.com", "HrPassword!", session, raSuccess);
        assertEquals("redirect:/hr/dashboard", viewSuccess);
        assertEquals("HR", session.getAttribute("userRole"));

        // Attempting to log in as HR with non-HR role
        User manager = new User("mgr@logsphere.com", passwordService.encode("MgrPassword!"), "Manager", "MANAGER");
        when(userRepository.findByEmail("mgr@logsphere.com")).thenReturn(Optional.of(manager));
        RedirectAttributes raDeny = new RedirectAttributesModelMap();
        String viewDeny = hrController.hrLogin("mgr@logsphere.com", "MgrPassword!", session, raDeny);
        assertEquals("redirect:/hr/login", viewDeny);
        assertEquals("Access denied. HR login only.", raDeny.getFlashAttributes().get("error"));
    }

    @Test
    @DisplayName("B3: Manager login success and role restriction")
    void testManagerLogin() {
        User manager = new User("manager@logsphere.com", passwordService.encode("MgrPass123!"), "Manager User", "MANAGER");
        when(userRepository.findByEmail("manager@logsphere.com")).thenReturn(Optional.of(manager));

        RedirectAttributes raSuccess = new RedirectAttributesModelMap();
        String viewSuccess = managerController.managerLogin("manager@logsphere.com", "MgrPass123!", session, raSuccess);
        assertEquals("redirect:/manager/dashboard", viewSuccess);
        assertEquals("MANAGER", session.getAttribute("userRole"));

        // Non-manager role rejected
        User hr = new User("hr@logsphere.com", passwordService.encode("HrPassword!"), "HR User", "HR");
        when(userRepository.findByEmail("hr@logsphere.com")).thenReturn(Optional.of(hr));
        RedirectAttributes raDeny = new RedirectAttributesModelMap();
        String viewDeny = managerController.managerLogin("hr@logsphere.com", "HrPassword!", session, raDeny);
        assertEquals("redirect:/manager/login", viewDeny);
        assertEquals("Access denied. Manager login only.", raDeny.getFlashAttributes().get("error"));
    }

    @Test
    @DisplayName("B4: Employee login success with email, employeeId and password")
    void testEmployeeLogin() {
        Employee emp = new Employee();
        emp.setName("Jane Doe");
        emp.setEmail("jane@logsphere.com");
        emp.setEmployeeId("EMP100");
        emp.setPassword(passwordService.encode("EmployeePass#1"));

        when(employeeRepository.findByEmail("jane@logsphere.com")).thenReturn(Optional.of(emp));

        RedirectAttributes raSuccess = new RedirectAttributesModelMap();
        String viewSuccess = employeeAuthController.employeeLogin("jane@logsphere.com", "EmployeePass#1", "EMP100", null, session, raSuccess);
        assertEquals("redirect:/employee/dashboard", viewSuccess);
        assertNotNull(session.getAttribute("employee"));
        assertEquals("Jane Doe", session.getAttribute("employeeName"));

        // Wrong employee ID
        RedirectAttributes raBadId = new RedirectAttributesModelMap();
        String viewBadId = employeeAuthController.employeeLogin("jane@logsphere.com", "EmployeePass#1", "WRONG_ID", null, session, raBadId);
        assertEquals("redirect:/employee/login", viewBadId);
        assertEquals("Invalid email / password / employee ID!", raBadId.getFlashAttributes().get("error"));

        // Wrong password
        RedirectAttributes raBadPass = new RedirectAttributesModelMap();
        String viewBadPass = employeeAuthController.employeeLogin("jane@logsphere.com", "WrongPass", "EMP100", null, session, raBadPass);
        assertEquals("redirect:/employee/login", viewBadPass);
        assertEquals("Invalid email / password / employee ID!", raBadPass.getFlashAttributes().get("error"));
    }

    // =========================================================================
    // SECTION C: Registration, Creation, Password Change & Reset Lifecycle Tests
    // =========================================================================

    @Test
    @DisplayName("C1: Admin registration hashes password before saving to database")
    void testAdminRegistrationHashesPassword() {
        when(userRepository.findByRole("ADMIN")).thenReturn(Collections.emptyList());
        when(userRepository.findByEmail("newadmin@logsphere.com")).thenReturn(Optional.empty());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        RedirectAttributes ra = new RedirectAttributesModelMap();

        String view = authController.registerAdmin("New Admin", "newadmin@logsphere.com", "AdminSecret#1", ra);
        assertEquals("redirect:/admin/login", view);

        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertNotEquals("AdminSecret#1", savedUser.getPassword());
        assertTrue(passwordService.matches("AdminSecret#1", savedUser.getPassword()));
        assertTrue(passwordService.isBCryptHash(savedUser.getPassword()));
    }

    @Test
    @DisplayName("C2: Admin registering HR and Manager hashes passwords")
    void testAdminRegisterHrAndManagerHashesPassword() {
        session.setAttribute("user", new User("admin@logsphere.com", "pass", "Admin", "ADMIN"));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // HR
        User hrUser = new User("newhr@logsphere.com", "HrPassword123", "New HR", null);
        ArgumentCaptor<User> hrCaptor = ArgumentCaptor.forClass(User.class);
        adminController.registerHr(hrUser, new RedirectAttributesModelMap(), session);
        verify(userRepository, atLeastOnce()).save(hrCaptor.capture());
        User savedHr = hrCaptor.getValue();
        assertTrue(passwordService.isBCryptHash(savedHr.getPassword()));
        assertTrue(passwordService.matches("HrPassword123", savedHr.getPassword()));

        // Manager
        User mgrUser = new User("newmgr@logsphere.com", "MgrPassword123", "New Mgr", null);
        ArgumentCaptor<User> mgrCaptor = ArgumentCaptor.forClass(User.class);
        adminController.registerManager(mgrUser, new RedirectAttributesModelMap(), session);
        verify(userRepository, atLeastOnce()).save(mgrCaptor.capture());
        User savedMgr = mgrCaptor.getValue();
        assertTrue(passwordService.isBCryptHash(savedMgr.getPassword()));
        assertTrue(passwordService.matches("MgrPassword123", savedMgr.getPassword()));
    }

    @Test
    @DisplayName("C3: Employee password change in EmployeeManagementController hashes new password")
    void testEmployeeUpdatePasswordHashed() {
        session.setAttribute("user", new User("admin@logsphere.com", "pass", "Admin", "ADMIN"));

        Employee existing = new Employee();
        existing.setId(5L);
        existing.setName("Existing Emp");
        existing.setEmail("emp@logsphere.com");
        existing.setEmployeeId("EMP005");
        existing.setPassword(passwordService.encode("OldPassword123"));

        when(employeeRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(employeeRepository.findByEmail("emp@logsphere.com")).thenReturn(Optional.of(existing));

        Employee formEmp = new Employee();
        formEmp.setName("Existing Emp");
        formEmp.setEmail("emp@logsphere.com");
        formEmp.setEmployeeId("EMP005");

        RedirectAttributes ra = new RedirectAttributesModelMap();
        employeeManagementController.updateEmployee(5L, formEmp, "BrandNewPassword#99", null, false, null, null, ra, session);

        assertTrue(passwordService.matches("BrandNewPassword#99", existing.getPassword()),
                "Updated password must match the new raw password");
        assertTrue(passwordService.isBCryptHash(existing.getPassword()),
                "Updated password must be a valid BCrypt hash");
    }

    @Test
    @DisplayName("C4: Password reset token lifecycle: generation, SHA-256 hash storage, 15m expiry, single-use")
    void testPasswordResetTokenLifecycle() {
        User user = new User("resetme@logsphere.com", passwordService.encode("OldSecret1"), "Reset User", "ADMIN");
        when(userRepository.findByEmail("resetme@logsphere.com")).thenReturn(Optional.of(user));
        when(userRepository.findAll()).thenReturn(List.of(user));

        // 1. Request reset token
        RedirectAttributes raForgot = new RedirectAttributesModelMap();
        authController.processForgotPassword("resetme@logsphere.com", raForgot, request);

        // Verify token in DB is hashed, not raw
        assertNotNull(user.getResetToken());
        assertEquals(64, user.getResetToken().length(), "Hashed token must be 64-character SHA-256 hex");
        assertNotNull(user.getResetTokenExpiry());
        assertTrue(user.getResetTokenExpiry().isAfter(LocalDateTime.now().plusMinutes(14)));

        // Capture raw token sent in reset email
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("resetme@logsphere.com"), urlCaptor.capture());
        String resetUrl = urlCaptor.getValue();
        assertTrue(resetUrl.contains("/reset-password?token="));
        String rawToken = resetUrl.substring(resetUrl.indexOf("token=") + 6);
        assertEquals(64, rawToken.length(), "Raw token must be 64-character hex");

        // Verify SHA-256 hash matches what is in DB
        assertEquals(passwordService.hashToken(rawToken), user.getResetToken());

        // 2. Validate token on form render
        Model model = new ConcurrentModel();
        RedirectAttributes raForm = new RedirectAttributesModelMap();
        String formView = authController.showResetPasswordForm(rawToken, model, raForm);
        assertEquals("reset-password", formView);
        assertEquals(rawToken, model.getAttribute("token"));

        // 3. Process valid reset
        RedirectAttributes raReset = new RedirectAttributesModelMap();
        String resetResult = authController.processResetPassword(rawToken, "NewPass#2026", raReset);
        assertEquals("redirect:/admin/login", resetResult);
        assertEquals("You have successfully reset your password.", raReset.getFlashAttributes().get("message"));

        // 4. Verify password changed and token invalidated (single-use)
        assertTrue(passwordService.matches("NewPass#2026", user.getPassword()));
        assertNull(user.getResetToken(), "Reset token must be cleared after single use");
        assertNull(user.getResetTokenExpiry(), "Reset token expiry must be cleared after single use");

        // 5. Reusing the token must fail
        RedirectAttributes raReuse = new RedirectAttributesModelMap();
        String reuseResult = authController.processResetPassword(rawToken, "AnotherPass#2026", raReuse);
        assertEquals("redirect:/admin/login", reuseResult);
        assertEquals("Invalid or expired reset token!", raReuse.getFlashAttributes().get("error"));
    }

    @Test
    @DisplayName("C5: Expired password reset tokens are rejected")
    void testExpiredResetTokenRejected() {
        User user = new User("expired@logsphere.com", passwordService.encode("OldPass"), "User", "ADMIN");
        String rawToken = passwordService.generateResetToken();
        user.setResetToken(passwordService.hashToken(rawToken));
        user.setResetTokenExpiry(LocalDateTime.now().minusMinutes(5)); // Expired

        when(userRepository.findAll()).thenReturn(List.of(user));

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String result = authController.processResetPassword(rawToken, "NewPass123", ra);
        assertEquals("redirect:/admin/login", result);
        assertEquals("Invalid or expired reset token!", ra.getFlashAttributes().get("error"));
    }

    @Test
    @DisplayName("C6: Forgot password returns uniform message to prevent account enumeration")
    void testForgotPasswordEnumerationProtection() {
        when(userRepository.findByEmail("exists@logsphere.com"))
                .thenReturn(Optional.of(new User("exists@logsphere.com", "pass", "Name", "ADMIN")));
        when(userRepository.findByEmail("doesnotexist@logsphere.com"))
                .thenReturn(Optional.empty());

        RedirectAttributes raExists = new RedirectAttributesModelMap();
        authController.processForgotPassword("exists@logsphere.com", raExists, request);

        RedirectAttributes raMissing = new RedirectAttributesModelMap();
        authController.processForgotPassword("doesnotexist@logsphere.com", raMissing, request);

        assertEquals(raExists.getFlashAttributes().get("message"), raMissing.getFlashAttributes().get("message"),
                "User-facing response must be identical for existing and non-existing accounts to prevent enumeration");
    }

    // =========================================================================
    // SECTION D: Legacy Account Migration & Auto-Upgrade Tests
    // =========================================================================

    @Test
    @DisplayName("D1: Legacy plaintext password is automatically upgraded to BCrypt on admin login")
    void testLegacyPlaintextAutoUpgradeOnAdminLogin() {
        // User with legacy plaintext password stored in database
        User legacyUser = new User("legacy@logsphere.com", "legacyPlaintext123", "Legacy User", "ADMIN");
        when(userRepository.findByEmail("legacy@logsphere.com")).thenReturn(Optional.of(legacyUser));

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = authController.login("legacy@logsphere.com", "legacyPlaintext123", session, ra);
        assertEquals("redirect:/admin/dashboard", view);

        // Verify password was automatically upgraded to BCrypt
        assertTrue(passwordService.isBCryptHash(legacyUser.getPassword()),
                "Legacy password must be upgraded to BCrypt hash: " + legacyUser.getPassword());
        assertTrue(passwordService.matches("legacyPlaintext123", legacyUser.getPassword()));
        verify(userRepository, atLeastOnce()).save(legacyUser);
    }

    @Test
    @DisplayName("D2: Legacy plaintext password is automatically upgraded to BCrypt on employee login")
    void testLegacyPlaintextAutoUpgradeOnEmployeeLogin() {
        Employee legacyEmp = new Employee();
        legacyEmp.setName("Legacy Emp");
        legacyEmp.setEmail("legacyemp@logsphere.com");
        legacyEmp.setEmployeeId("LEG001");
        legacyEmp.setPassword("legacyPlaintext#EMP");

        when(employeeRepository.findByEmail("legacyemp@logsphere.com")).thenReturn(Optional.of(legacyEmp));

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = employeeAuthController.employeeLogin("legacyemp@logsphere.com", "legacyPlaintext#EMP", "LEG001", null, session, ra);
        assertEquals("redirect:/employee/dashboard", view);

        assertTrue(passwordService.isBCryptHash(legacyEmp.getPassword()),
                "Legacy employee password must be upgraded to BCrypt hash");
        assertTrue(passwordService.matches("legacyPlaintext#EMP", legacyEmp.getPassword()));
        verify(employeeRepository, atLeastOnce()).save(legacyEmp);
    }

    @Test
    @DisplayName("D3: Malformed or corrupted password hashes fail gracefully without crashing")
    void testMalformedPasswordHashHandledSafely() {
        User brokenHashUser = new User("broken@logsphere.com", "$2a$invalidHashStringThatIsBroken", "Broken", "ADMIN");
        when(userRepository.findByEmail("broken@logsphere.com")).thenReturn(Optional.of(brokenHashUser));

        RedirectAttributes ra = new RedirectAttributesModelMap();
        assertDoesNotThrow(() -> {
            String view = authController.login("broken@logsphere.com", "password", session, ra);
            assertEquals("redirect:/admin/login", view);
        }, "Malformed hash in database must fail safely without 500 error or crash");
    }

    // =========================================================================
    // SECTION E: Information Leak Prevention & Serialization Safety
    // =========================================================================

    @Test
    @DisplayName("E1: User JSON serialization never exposes password, resetToken or expiry")
    void testUserSerializationExcludesSecrets() throws Exception {
        User user = new User("audit@logsphere.com", "$2a$12$eX4mPL3H45hV4Lu3N07L34k3d1nJ50N0u7pu7", "Audit User", "ADMIN");
        user.setResetToken("f0e1d2c3b4a5968778695a4b3c2d1e0f0e1d2c3b4a5968778695a4b3c2d1e0f");
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));

        String json = objectMapper.writeValueAsString(user);

        assertFalse(json.contains("password"), "JSON serialization of User must not contain 'password' property");
        assertFalse(json.contains("resetToken"), "JSON serialization of User must not contain 'resetToken' property");
        assertFalse(json.contains("resetTokenExpiry"), "JSON serialization of User must not contain 'resetTokenExpiry' property");
        assertFalse(json.contains(user.getResetToken()), "JSON must not contain reset token value");
    }

    @Test
    @DisplayName("E2: Employee JSON serialization never exposes password")
    void testEmployeeSerializationExcludesPassword() throws Exception {
        Employee employee = new Employee();
        employee.setName("Audit Employee");
        employee.setEmail("auditemp@logsphere.com");
        employee.setPassword("$2a$12$s0m353cr37H45hV4Lu3N07L34k3d");

        String json = objectMapper.writeValueAsString(employee);

        assertFalse(json.contains("password"), "JSON serialization of Employee must not contain 'password' property");
        assertFalse(json.contains(employee.getPassword()), "JSON must not contain password hash value");
    }

    @Test
    @DisplayName("E3: Entity toString() implementations do not leak passwords or reset tokens")
    void testEntityToStringOmitsSecrets() {
        User user = new User("test@logsphere.com", "SecretPass123!", "Test User", "ADMIN");
        user.setResetToken("superSecretResetTokenHexValue1234567890");

        String userStr = user.toString();
        assertFalse(userStr.contains("SecretPass123!"), "User.toString() must never contain password");
        assertFalse(userStr.contains("superSecretResetTokenHexValue1234567890"), "User.toString() must never contain reset token");

        Employee emp = new Employee();
        emp.setName("Test Employee");
        emp.setPassword("EmployeePassword#1");

        String empStr = emp.toString();
        assertFalse(empStr.contains("EmployeePassword#1"), "Employee.toString() must never contain password");
    }

    // =========================================================================
    // SECTION F: Boundary & Malformed Input Handling
    // =========================================================================

    @Test
    @DisplayName("F1: Invalid, missing or excessively long password inputs handled safely")
    void testBoundaryAndLongPasswordInputs() {
        // Null & empty
        assertFalse(passwordService.matches(null, "$2a$12$somevalidhash"), "Null raw password must return false");
        assertFalse(passwordService.matches("pass", null), "Null encoded hash must return false");
        assertFalse(passwordService.matches("", ""), "Empty strings must return false");

        // Excessively long input (e.g. 5,000 chars - CPU starvation DoS vector)
        String massivePassword = "A".repeat(5000);
        assertFalse(passwordService.matches(massivePassword, "$2a$12$somevalidhash"),
                "Excessively long password must be rejected safely without CPU starvation");

        assertThrows(IllegalArgumentException.class, () -> passwordService.encode(massivePassword),
                "Encoding passwords exceeding maximum length must be rejected with IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () -> passwordService.encode(""),
                "Encoding empty password must throw IllegalArgumentException");
    }

    @Test
    @DisplayName("F2: Short password is rejected in admin registration and reset flows")
    void testShortPasswordValidation() {
        RedirectAttributes raReg = new RedirectAttributesModelMap();
        String viewReg = authController.registerAdmin("Short Pass", "short@logsphere.com", "123", raReg);
        assertEquals("redirect:/register/admin", viewReg);
        assertEquals("Password must be at least 6 characters long!", raReg.getFlashAttributes().get("error"));

        RedirectAttributes raReset = new RedirectAttributesModelMap();
        String viewReset = authController.processResetPassword("dummy-token", "123", raReset);
        assertEquals("redirect:/reset-password?token=dummy-token", viewReset);
        assertEquals("Password must be at least 6 characters long!", raReset.getFlashAttributes().get("error"));
    }
}
