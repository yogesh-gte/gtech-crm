package com.example.demo;

import com.example.demo.model.Employee;
import com.example.demo.model.User;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.PasswordService;
import com.example.demo.service.RateLimiterService;
import com.example.demo.service.SessionSecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthenticationSecurityRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private SessionSecurityService sessionSecurityService;

    @Autowired
    private com.example.demo.service.CsrfTokenService csrfTokenService;

    @Autowired
    private RateLimiterService rateLimiterService;

    private static final String TEST_ADMIN_EMAIL = "auth.test.admin@example.com";
    private static final String TEST_RAW_PASSWORD = "StrongPassword@123";

    @BeforeEach
    public void setup() {
        rateLimiterService.resetAll();
        cleanupUser();
        User admin = new User(TEST_ADMIN_EMAIL, passwordService.encode(TEST_RAW_PASSWORD), "Auth Test Admin", "ADMIN");
        userRepository.save(admin);
    }

    @AfterEach
    public void tearDown() {
        cleanupUser();
        rateLimiterService.resetAll();
    }

    private void cleanupUser() {
        userRepository.findByEmail(TEST_ADMIN_EMAIL).ifPresent(u -> userRepository.delete(u));
        userRepository.findByEmail("auth.test.reset@example.com").ifPresent(u -> userRepository.delete(u));
    }

    @Test
    @DisplayName("Valid login succeeds, rotates session ID, and authenticates the user")
    public void testSuccessfulLoginRotatesSession() throws Exception {
        MockHttpSession originalSession = new MockHttpSession();
        String token = csrfTokenService.getOrCreateToken(originalSession);

        MvcResult result = mockMvc.perform(post("/admin/login")
                        .session(originalSession)
                        .param("_csrf", token)
                        .param("email", TEST_ADMIN_EMAIL)
                        .param("password", TEST_RAW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"))
                .andReturn();

        MockHttpSession newSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(newSession, "New session must be created");
        User sessionUser = (User) newSession.getAttribute("user");
        assertNotNull(sessionUser, "Session must contain authenticated user");
        assertEquals(TEST_ADMIN_EMAIL, sessionUser.getEmail());
        assertEquals("ADMIN", newSession.getAttribute("userRole"));
    }

    @Test
    @DisplayName("Failed login with incorrect password rejects authentication and records failure")
    public void testFailedLoginIncorrectPassword() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = csrfTokenService.getOrCreateToken(session);

        mockMvc.perform(post("/admin/login")
                        .session(session)
                        .param("_csrf", token)
                        .param("email", TEST_ADMIN_EMAIL)
                        .param("password", "WrongPassword123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(flash().attribute("error", "Invalid email or password!"));

        assertNull(session.getAttribute("user"), "Session must not contain authenticated user");
    }

    @Test
    @DisplayName("Failed login with unknown account returns uniform error message to prevent enumeration")
    public void testFailedLoginUnknownAccount() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = csrfTokenService.getOrCreateToken(session);

        mockMvc.perform(post("/admin/login")
                        .session(session)
                        .param("_csrf", token)
                        .param("email", "nonexistent.user.123@example.com")
                        .param("password", "AnyPassword123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(flash().attribute("error", "Invalid email or password!"));

        assertNull(session.getAttribute("user"));
    }

    @Test
    @DisplayName("Password hash is never stored in sanitized session object")
    public void testPasswordHashNeverStoredInSession() throws Exception {
        User user = userRepository.findByEmail(TEST_ADMIN_EMAIL).orElseThrow();
        User sanitized = sessionSecurityService.sanitizeUserForSession(user);

        assertNull(sanitized.getPassword(), "Sanitized user session object must have null password");
        assertEquals(user.getEmail(), sanitized.getEmail());
        assertEquals(user.getName(), sanitized.getName());
        assertEquals(user.getRole(), sanitized.getRole());
    }

    @Test
    @DisplayName("Password service verifies password and upgrades legacy hash to BCrypt")
    public void testPasswordVerificationAndUpgrade() {
        String rawPassword = "TestMigrationPass123!";
        String encoded = passwordService.encode(rawPassword);

        assertTrue(passwordService.matches(rawPassword, encoded), "BCrypt password must verify correctly");
        assertFalse(passwordService.matches("WrongPass", encoded), "Incorrect password must fail verification");

        // Test verifyAndUpgrade with custom consumer
        boolean[] upgraded = {false};
        boolean verified = passwordService.verifyAndUpgrade(rawPassword, encoded, newHash -> upgraded[0] = true);
        assertTrue(verified);
    }

    @Test
    @DisplayName("Password reset token is stored as digest and allows single-use reset")
    public void testPasswordResetWorkflow() throws Exception {
        String resetEmail = "auth.test.reset@example.com";
        User resetUser = new User(resetEmail, passwordService.encode("InitialPass123"), "Reset User", "ADMIN");
        String rawToken = passwordService.generateResetToken();
        String hashedToken = passwordService.hashToken(rawToken);

        resetUser.setResetToken(hashedToken);
        resetUser.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(resetUser);

        // Verify token in database is hashed, not raw token
        User saved = userRepository.findByEmail(resetEmail).orElseThrow();
        assertEquals(hashedToken, saved.getResetToken(), "Reset token in DB must be hashed");
        assertNotEquals(rawToken, saved.getResetToken(), "Raw token must never be stored in database");

        // Submit password reset with valid raw token
        String newPassword = "NewStrongPassword456!";
        mockMvc.perform(post("/reset-password")
                        .param("token", rawToken)
                        .param("password", newPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(flash().attribute("message", "You have successfully reset your password."));

        // Verify token was cleared (single-use enforcement)
        User updatedUser = userRepository.findByEmail(resetEmail).orElseThrow();
        assertNull(updatedUser.getResetToken(), "Reset token must be cleared after use");
        assertNull(updatedUser.getResetTokenExpiry(), "Reset token expiry must be cleared");
        assertTrue(passwordService.matches(newPassword, updatedUser.getPassword()), "New password must be set");

        // Attempting to reuse the same token must fail
        mockMvc.perform(post("/reset-password")
                        .param("token", rawToken)
                        .param("password", "AnotherPass789!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(flash().attribute("error", "Invalid or expired reset token!"));
    }

    @Test
    @DisplayName("Password reset revokes active sessions for the target user")
    public void testPasswordResetRevokesActiveSessions() {
        String targetEmail = "session.target@example.com";
        MockHttpSession activeSession = new MockHttpSession();

        sessionSecurityService.registerUserSession(targetEmail, activeSession);
        assertFalse(activeSession.isInvalid());

        // Invalidate sessions for user
        sessionSecurityService.invalidateSessionsForUser(targetEmail);
        assertTrue(activeSession.isInvalid(), "Active session must be invalidated upon password reset");
    }

    @Test
    @DisplayName("Forgot password request returns uniform response to prevent account enumeration")
    public void testForgotPasswordAccountEnumerationProtection() throws Exception {
        // Existing user
        mockMvc.perform(post("/forgot-password")
                        .param("email", TEST_ADMIN_EMAIL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(flash().attribute("message",
                        "If an account exists for that email, we have sent a password reset link."));

        // Non-existent user returns the identical response
        mockMvc.perform(post("/forgot-password")
                        .param("email", "nonexistent.account.999@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(flash().attribute("message",
                        "If an account exists for that email, we have sent a password reset link."));
    }

    @Test
    @DisplayName("Password reset form rejects expired tokens")
    public void testExpiredPasswordResetTokenRejected() throws Exception {
        String expiredEmail = "auth.test.expired@example.com";
        User user = new User(expiredEmail, passwordService.encode("SomePass123"), "Expired Token User", "ADMIN");
        String rawToken = passwordService.generateResetToken();
        user.setResetToken(passwordService.hashToken(rawToken));
        user.setResetTokenExpiry(LocalDateTime.now().minusMinutes(5)); // already expired
        userRepository.save(user);

        mockMvc.perform(get("/reset-password")
                        .param("token", rawToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"))
                .andExpect(flash().attribute("error", "Invalid or expired reset token!"));

        userRepository.delete(user);
    }
}
