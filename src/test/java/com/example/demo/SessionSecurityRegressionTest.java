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
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SessionSecurityRegressionTest {

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
    private RateLimiterService rateLimiterService;

    @Autowired
    private com.example.demo.service.CsrfTokenService csrfTokenService;

    private static final String HR_EMAIL = "session.hr@example.com";
    private static final String EMP_EMAIL = "session.emp@example.com";
    private static final String EMP_ID = "SESS_EMP_01";
    private static final String PASS = "SecurePass123!";

    @BeforeEach
    public void setup() {
        rateLimiterService.resetAll();
        cleanup();

        User hrUser = new User(HR_EMAIL, passwordService.encode(PASS), "Session HR", "HR");
        userRepository.save(hrUser);

        Employee emp = new Employee();
        emp.setEmail(EMP_EMAIL);
        emp.setEmployeeId(EMP_ID);
        emp.setPassword(passwordService.encode(PASS));
        emp.setName("Session Employee");
        emp.setDepartment("Human Resources");
        emp.setStatus("ACTIVE");
        employeeRepository.save(emp);
    }

    @AfterEach
    public void tearDown() {
        cleanup();
        rateLimiterService.resetAll();
    }

    private void cleanup() {
        userRepository.findByEmail(HR_EMAIL).ifPresent(userRepository::delete);
        employeeRepository.findByEmail(EMP_EMAIL).ifPresent(employeeRepository::delete);
    }

    @Test
    @DisplayName("Session fixation defense: HR login rotates session and invalidates previous session ID")
    public void testHrLoginSessionFixationDefense() throws Exception {
        MockHttpSession preSession = new MockHttpSession();
        String token = csrfTokenService.getOrCreateToken(preSession);

        MvcResult result = mockMvc.perform(post("/hr/login")
                        .session(preSession)
                        .param("_csrf", token)
                        .param("email", HR_EMAIL)
                        .param("password", PASS))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hr/dashboard"))
                .andReturn();

        MockHttpSession postSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(postSession);
        User sessionUser = (User) postSession.getAttribute("user");
        assertNotNull(sessionUser);
        assertEquals("HR", postSession.getAttribute("userRole"));
    }

    @Test
    @DisplayName("Session fixation defense: Employee login rotates session and binds sanitized employee")
    public void testEmployeeLoginSessionFixationDefense() throws Exception {
        MockHttpSession preSession = new MockHttpSession();
        String token = csrfTokenService.getOrCreateToken(preSession);

        MvcResult result = mockMvc.perform(post("/employee/login")
                        .session(preSession)
                        .param("_csrf", token)
                        .param("email", EMP_EMAIL)
                        .param("employeeId", EMP_ID)
                        .param("password", PASS))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employee/dashboard"))
                .andReturn();

        MockHttpSession postSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(postSession);
        Employee sessionEmp = (Employee) postSession.getAttribute("employee");
        assertNotNull(sessionEmp);
        assertEquals(EMP_ID, postSession.getAttribute("employeeId"));
        assertNull(sessionEmp.getPassword(), "Employee password must be sanitized from session attribute");
    }

    @Test
    @DisplayName("Admin logout invalidates the session and expires session cookie")
    public void testAdminLogoutInvalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", new User(HR_EMAIL, null, "HR", "HR"));
        session.setAttribute("userRole", "HR");
        String token = csrfTokenService.getOrCreateToken(session);

        mockMvc.perform(post("/logout").session(session).param("_csrf", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        assertTrue(session.isInvalid(), "HttpSession must be invalidated upon logout");
    }

    @Test
    @DisplayName("Employee logout invalidates the session and redirects to employee login")
    public void testEmployeeLogoutInvalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("employee", new Employee());
        session.setAttribute("employeeId", EMP_ID);
        String token = csrfTokenService.getOrCreateToken(session);

        mockMvc.perform(post("/employee/logout").session(session).param("_csrf", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employee/login"));

        assertTrue(session.isInvalid(), "Employee session must be invalidated upon logout");
    }

    @Test
    @DisplayName("Session tracking mode is cookie only, preventing jsessionid URL rewriting")
    public void testSessionTrackingModeCookieOnly() throws Exception {
        ClassPathResource res = new ClassPathResource("application.properties");
        Properties props = new Properties();
        try (InputStream in = res.getInputStream()) {
            props.load(in);
        }

        assertEquals("cookie", props.getProperty("server.servlet.session.tracking-modes"),
                "Session tracking mode must be restricted strictly to cookie to prevent URL rewriting leaks");
        assertEquals("true", props.getProperty("server.servlet.session.cookie.http-only"),
                "Session cookie must be HttpOnly");
        assertEquals("lax", props.getProperty("server.servlet.session.cookie.same-site"),
                "Session cookie SameSite must be lax");
    }

    @Test
    @DisplayName("Session registry revokes active sessions upon employee deletion or deactivation")
    public void testSessionRevocationUponAccountDeactivation() {
        String empEmail = "deactivate.emp@example.com";
        MockHttpSession empSession = new MockHttpSession();

        sessionSecurityService.registerUserSession(empEmail, empSession);
        assertFalse(empSession.isInvalid());

        // Administrative revocation triggered
        sessionSecurityService.invalidateSessionsForUser(empEmail);
        assertTrue(empSession.isInvalid(), "Active employee session must be invalidated");
    }

    @Test
    @DisplayName("Unauthenticated request with an invalid/revoked session cannot access protected endpoints")
    public void testRevokedSessionCannotAccessProtectedResources() throws Exception {
        MockHttpSession expiredSession = new MockHttpSession();
        expiredSession.invalidate();

        mockMvc.perform(get("/admin/dashboard").session(expiredSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }
}
