package com.example.demo;

import com.example.demo.config.AuthorizationInterceptor;
import com.example.demo.controller.*;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.service.AuthorizationService;
import com.example.demo.service.EmailService;
import com.example.demo.service.PerformanceScoreService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 4 Security Verification Test Suite:
 * - Centralized Deny-By-Default Route Authorization
 * - Role Separation (ADMIN, HR, MANAGER, EMPLOYEE)
 * - Anti-IDOR / Anti-BOLA Controls (Meetings, Tasks, Payslips, Visitors)
 * - Mass Assignment & Privilege Escalation Defenses (Salary Protection, Organizer Spoofing)
 * - HTTP Status Code Contracts (401/403 JSON vs 302/403 HTML)
 */
public class RoleBasedAuthorizationSecurityTest {

    private AuthorizationService authorizationService;
    private AuthorizationInterceptor authorizationInterceptor;

    // Repositories
    @Mock private MeetingRepository meetingRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private UserRepository userRepository;
    @Mock private VisitorRepository visitorRepository;
    @Mock private PayrollRepository payrollRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PerformanceRecordRepository performanceRecordRepository;
    @Mock private PerformanceScoreService performanceScoreService;
    @Mock private EmailService emailService;
    @Mock private TeamRepository teamRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private com.example.demo.repository.IdleIncidentRepository idleIncidentRepository;
    @Mock private IssueRepository issueRepository;

    // Controllers
    private AdminController adminController;
    private EmployeeManagementController employeeManagementController;
    private EmployeeController employeeController;
    private MeetingController meetingController;
    private TaskController taskController;
    private VisitorController visitorController;
    private ReportController reportController;
    private PageController pageController;

    // Reusable fixtures
    private User adminUser;
    private User hrUser;
    private User managerUser;
    private Employee regularEmp1;
    private Employee regularEmp2;
    private Employee teamLeadEmp;
    private Team teamAlpha;
    private Team teamBeta;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        authorizationService = new AuthorizationService();
        authorizationInterceptor = new AuthorizationInterceptor();
        ReflectionTestUtils.setField(authorizationInterceptor, "authorizationService", authorizationService);

        // Fixtures: Teams
        teamAlpha = new Team();
        teamAlpha.setId(10L);
        teamAlpha.setName("Alpha Team");

        teamBeta = new Team();
        teamBeta.setId(20L);
        teamBeta.setName("Beta Team");

        // Fixtures: Users
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setName("System Admin");
        adminUser.setEmail("admin@logsphere.com");
        adminUser.setRole("ADMIN");

        hrUser = new User();
        hrUser.setId(2L);
        hrUser.setName("HR Lead");
        hrUser.setEmail("hr@logsphere.com");
        hrUser.setRole("HR");

        managerUser = new User();
        managerUser.setId(3L);
        managerUser.setName("Engineering Manager");
        managerUser.setEmail("mgr@logsphere.com");
        managerUser.setRole("MANAGER");

        // Fixtures: Employees
        regularEmp1 = new Employee();
        regularEmp1.setId(101L);
        regularEmp1.setEmployeeId("EMP101");
        regularEmp1.setName("Alice Dev");
        regularEmp1.setEmail("alice@logsphere.com");
        regularEmp1.setTeam(teamAlpha);
        regularEmp1.setIsTeamLead(false);
        regularEmp1.setSalary(75000.0);

        regularEmp2 = new Employee();
        regularEmp2.setId(102L);
        regularEmp2.setEmployeeId("EMP102");
        regularEmp2.setName("Bob QA");
        regularEmp2.setEmail("bob@logsphere.com");
        regularEmp2.setTeam(teamBeta);
        regularEmp2.setIsTeamLead(false);
        regularEmp2.setSalary(65000.0);

        teamLeadEmp = new Employee();
        teamLeadEmp.setId(103L);
        teamLeadEmp.setEmployeeId("EMP103");
        teamLeadEmp.setName("Charlie Lead");
        teamLeadEmp.setEmail("charlie@logsphere.com");
        teamLeadEmp.setTeam(teamAlpha);
        teamLeadEmp.setIsTeamLead(true);

        // Controllers setup
        adminController = new AdminController();
        ReflectionTestUtils.setField(adminController, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(adminController, "employeeRepository", employeeRepository);
        ReflectionTestUtils.setField(adminController, "userRepository", userRepository);
        ReflectionTestUtils.setField(adminController, "visitorRepository", visitorRepository);
        ReflectionTestUtils.setField(adminController, "meetingRepository", meetingRepository);
        ReflectionTestUtils.setField(adminController, "payrollRepository", payrollRepository);
        ReflectionTestUtils.setField(adminController, "idleIncidentRepository", idleIncidentRepository);
        when(idleIncidentRepository.findActiveIncidents()).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(adminController, "issueRepository", issueRepository);
        when(issueRepository.findAllWithDetails()).thenReturn(Collections.emptyList());

        employeeManagementController = new EmployeeManagementController();
        ReflectionTestUtils.setField(employeeManagementController, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(employeeManagementController, "employeeRepository", employeeRepository);
        ReflectionTestUtils.setField(employeeManagementController, "teamRepository", teamRepository);

        employeeController = new EmployeeController();
        ReflectionTestUtils.setField(employeeController, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(employeeController, "employeeRepository", employeeRepository);
        ReflectionTestUtils.setField(employeeController, "meetingRepository", meetingRepository);
        ReflectionTestUtils.setField(employeeController, "payrollRepository", payrollRepository);
        ReflectionTestUtils.setField(employeeController, "attendanceRepository", attendanceRepository);
        ReflectionTestUtils.setField(employeeController, "notificationRepository", notificationRepository);

        meetingController = new MeetingController();
        ReflectionTestUtils.setField(meetingController, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(meetingController, "meetingRepository", meetingRepository);
        ReflectionTestUtils.setField(meetingController, "employeeRepository", employeeRepository);
        ReflectionTestUtils.setField(meetingController, "visitorRepository", visitorRepository);
        ReflectionTestUtils.setField(meetingController, "emailService", emailService);
        ReflectionTestUtils.setField(meetingController, "notificationRepository", notificationRepository);

        taskController = new TaskController();
        ReflectionTestUtils.setField(taskController, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(taskController, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(taskController, "employeeRepository", employeeRepository);

        visitorController = new VisitorController();
        ReflectionTestUtils.setField(visitorController, "visitorRepository", visitorRepository);

        reportController = new ReportController();
        ReflectionTestUtils.setField(reportController, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(reportController, "employeeRepository", employeeRepository);
        ReflectionTestUtils.setField(reportController, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(reportController, "performanceScoreService", performanceScoreService);

        pageController = new PageController();
    }

    // =========================================================================
    // 1. AuthorizationService Unit Tests
    // =========================================================================
    @Nested
    @DisplayName("1. AuthorizationService Role and Ownership Checks")
    class AuthorizationServiceTests {

        @Test
        @DisplayName("Role detection returns expected boolean flags for all user roles and employees")
        void testRoleDetection() {
            MockHttpSession adminSession = new MockHttpSession();
            adminSession.setAttribute("user", adminUser);
            adminSession.setAttribute("userRole", "ADMIN");

            MockHttpSession hrSession = new MockHttpSession();
            hrSession.setAttribute("user", hrUser);
            hrSession.setAttribute("userRole", "HR");

            MockHttpSession mgrSession = new MockHttpSession();
            mgrSession.setAttribute("user", managerUser);
            mgrSession.setAttribute("userRole", "MANAGER");

            MockHttpSession empSession = new MockHttpSession();
            empSession.setAttribute("employee", regularEmp1);
            empSession.setAttribute("userRole", "EMPLOYEE");

            MockHttpSession anonSession = new MockHttpSession();

            // Admin checks
            assertTrue(authorizationService.isAdmin(adminSession));
            assertFalse(authorizationService.isAdmin(hrSession));
            assertFalse(authorizationService.isAdmin(mgrSession));
            assertFalse(authorizationService.isAdmin(empSession));
            assertFalse(authorizationService.isAdmin(anonSession));

            // HR checks
            assertTrue(authorizationService.isHr(hrSession));
            assertFalse(authorizationService.isHr(adminSession));
            assertFalse(authorizationService.isHr(mgrSession));

            // Manager checks
            assertTrue(authorizationService.isManager(mgrSession));
            assertFalse(authorizationService.isManager(adminSession));

            // Staff checks (ADMIN, HR, MANAGER are all staff)
            assertTrue(authorizationService.isStaff(adminSession));
            assertTrue(authorizationService.isStaff(hrSession));
            assertTrue(authorizationService.isStaff(mgrSession));
            assertFalse(authorizationService.isStaff(empSession));
            assertFalse(authorizationService.isStaff(anonSession));

            // Employee checks
            assertTrue(authorizationService.isEmployee(empSession));
            assertFalse(authorizationService.isEmployee(adminSession));
        }

        @Test
        @DisplayName("Meeting access ownership checks prevent IDOR while allowing attendees and staff")
        void testMeetingAccessOwnership() {
            Meeting meeting = new Meeting();
            meeting.setId(500L);
            meeting.setOrganizer(regularEmp1); // Alice is organizer
            meeting.setAttendees(List.of(teamLeadEmp)); // Charlie is attendee
            meeting.setInviteEveryone(false);

            MockHttpSession aliceSession = new MockHttpSession();
            aliceSession.setAttribute("employee", regularEmp1);

            MockHttpSession charlieSession = new MockHttpSession();
            charlieSession.setAttribute("employee", teamLeadEmp);

            MockHttpSession bobSession = new MockHttpSession(); // Bob is uninvolved
            bobSession.setAttribute("employee", regularEmp2);

            MockHttpSession adminSession = new MockHttpSession();
            adminSession.setAttribute("user", adminUser);

            // Alice (organizer) can access
            assertTrue(authorizationService.canAccessMeeting(aliceSession, meeting));
            // Charlie (attendee) can access
            assertTrue(authorizationService.canAccessMeeting(charlieSession, meeting));
            // Bob (unrelated) CANNOT access
            assertFalse(authorizationService.canAccessMeeting(bobSession, meeting));
            // Admin (oversight) can access
            assertTrue(authorizationService.canAccessMeeting(adminSession, meeting));

            // If meeting is company-wide, all employees can access
            meeting.setInviteEveryone(true);
            assertTrue(authorizationService.canAccessMeeting(bobSession, meeting));
        }

        @Test
        @DisplayName("Meeting modification is restricted to organizer and Admin")
        void testMeetingModificationOwnership() {
            Meeting meeting = new Meeting();
            meeting.setId(501L);
            meeting.setOrganizer(regularEmp1);

            MockHttpSession organizerSession = new MockHttpSession();
            organizerSession.setAttribute("employee", regularEmp1);

            MockHttpSession otherEmpSession = new MockHttpSession();
            otherEmpSession.setAttribute("employee", regularEmp2);

            MockHttpSession adminSession = new MockHttpSession();
            adminSession.setAttribute("user", adminUser);

            assertTrue(authorizationService.canModifyMeeting(organizerSession, meeting));
            assertTrue(authorizationService.canModifyMeeting(adminSession, meeting));
            assertFalse(authorizationService.canModifyMeeting(otherEmpSession, meeting));
        }

        @Test
        @DisplayName("Task deletion enforces team-scoping for team leads and blocks regular employees")
        void testTaskDeletionScoping() {
            Task taskAlpha = new Task();
            taskAlpha.setId(1001L);
            taskAlpha.setEmployee(regularEmp1); // regularEmp1 is in teamAlpha

            Task taskBeta = new Task();
            taskBeta.setId(1002L);
            taskBeta.setEmployee(regularEmp2); // regularEmp2 is in teamBeta

            MockHttpSession leadSession = new MockHttpSession(); // teamLeadEmp is lead of teamAlpha
            leadSession.setAttribute("employee", teamLeadEmp);

            MockHttpSession regularEmpSession = new MockHttpSession();
            regularEmpSession.setAttribute("employee", regularEmp1);

            MockHttpSession managerSession = new MockHttpSession();
            managerSession.setAttribute("user", managerUser);

            MockHttpSession adminSession = new MockHttpSession();
            adminSession.setAttribute("user", adminUser);

            // Team Lead can delete task for employee in Team Alpha
            assertTrue(authorizationService.canDeleteTask(leadSession, taskAlpha));

            // Team Lead CANNOT delete task for employee in Team Beta (Cross-team BOLA blocked)
            assertFalse(authorizationService.canDeleteTask(leadSession, taskBeta));

            // Regular employee cannot delete tasks
            assertFalse(authorizationService.canDeleteTask(regularEmpSession, taskAlpha));

            // Admin and Manager can delete
            assertTrue(authorizationService.canDeleteTask(adminSession, taskAlpha));
            assertTrue(authorizationService.canDeleteTask(managerSession, taskBeta));
        }

        @Test
        @DisplayName("Payroll access is restricted to owner employee, HR, and Admin")
        void testPayrollAccessScoping() {
            Payroll payroll = new Payroll();
            payroll.setId(901L);
            payroll.setEmployee(regularEmp1);

            MockHttpSession ownerSession = new MockHttpSession();
            ownerSession.setAttribute("employee", regularEmp1);

            MockHttpSession otherEmpSession = new MockHttpSession();
            otherEmpSession.setAttribute("employee", regularEmp2);

            MockHttpSession hrSession = new MockHttpSession();
            hrSession.setAttribute("user", hrUser);

            MockHttpSession adminSession = new MockHttpSession();
            adminSession.setAttribute("user", adminUser);

            assertTrue(authorizationService.canAccessPayroll(ownerSession, payroll));
            assertTrue(authorizationService.canAccessPayroll(hrSession, payroll));
            assertTrue(authorizationService.canAccessPayroll(adminSession, payroll));
            assertFalse(authorizationService.canAccessPayroll(otherEmpSession, payroll));
        }
    }

    // =========================================================================
    // 2. AuthorizationInterceptor Route Protection Tests
    // =========================================================================
    @Nested
    @DisplayName("2. Centralized AuthorizationInterceptor Tests")
    class AuthorizationInterceptorTests {

        @Test
        @DisplayName("Public routes are accessible without any session or authentication")
        void testPublicRoutesAccessible() throws Exception {
            String[] publicRoutes = {
                    "/", "/index", "/error", "/login", "/admin/login", "/hr/login",
                    "/manager/login", "/employee/login", "/register/admin", "/visitors/register",
                    "/reception/dashboard", "/attendance/mobile", "/employee/qrcode/scan",
                    "/css/style.css", "/js/app.js", "/images/logo.png"
            };

            for (String uri : publicRoutes) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
                MockHttpServletResponse response = new MockHttpServletResponse();
                boolean allowed = authorizationInterceptor.preHandle(request, response, new Object());
                assertTrue(allowed, "Route should be public and allowed: " + uri);
                assertEquals(200, response.getStatus());
            }
        }

        @Test
        @DisplayName("Unauthenticated requests to protected HTML routes redirect to login")
        void testUnauthenticatedHtmlRequestsRedirect() throws Exception {
            Map<String, String> protectedRoutesToRedirects = Map.of(
                    "/admin/dashboard", "/admin/login",
                    "/hr/dashboard", "/hr/login",
                    "/manager/dashboard", "/manager/login",
                    "/employee/dashboard", "/employee/login",
                    "/reports", "/login",
                    "/sprints", "/login",
                    "/dashboard", "/login"
            );

            for (Map.Entry<String, String> entry : protectedRoutesToRedirects.entrySet()) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", entry.getKey());
                MockHttpServletResponse response = new MockHttpServletResponse();
                boolean allowed = authorizationInterceptor.preHandle(request, response, new Object());
                assertFalse(allowed, "Route should require authentication: " + entry.getKey());
                assertEquals(302, response.getStatus());
                assertTrue(response.getRedirectedUrl().endsWith(entry.getValue()),
                        "Redirect for " + entry.getKey() + " should end with " + entry.getValue());
            }
        }

        @Test
        @DisplayName("Unauthenticated requests to protected API endpoints return HTTP 401 JSON")
        void testUnauthenticatedApiRequestsReturn401Json() throws Exception {
            String[] apiRoutes = {
                    "/admin/api/employees",
                    "/employee/api/meetings/1",
                    "/admin/api/payroll/base-salary/101"
            };

            for (String uri : apiRoutes) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
                request.addHeader("Accept", "application/json");
                MockHttpServletResponse response = new MockHttpServletResponse();

                boolean allowed = authorizationInterceptor.preHandle(request, response, new Object());
                assertFalse(allowed, "API route should reject unauthenticated request: " + uri);
                assertTrue(response.getContentType() != null && response.getContentType().startsWith("application/json"));
                assertTrue(response.getContentAsString().contains("Unauthorized"));
            }
        }

        @Test
        @DisplayName("Unauthorized role requests return HTTP 403 Forbidden")
        void testUnauthorizedRoleReturns403() throws Exception {
            // Employee tries to access Admin Dashboard
            MockHttpServletRequest empRequest = new MockHttpServletRequest("GET", "/admin/dashboard");
            MockHttpSession empSession = new MockHttpSession();
            empSession.setAttribute("employee", regularEmp1);
            empRequest.setSession(empSession);
            MockHttpServletResponse empResponse = new MockHttpServletResponse();

            boolean allowed = authorizationInterceptor.preHandle(empRequest, empResponse, new Object());
            assertFalse(allowed);
            assertEquals(403, empResponse.getStatus());

            // Manager tries to access Organization Settings (Strictly ADMIN)
            MockHttpServletRequest mgrRequest = new MockHttpServletRequest("GET", "/admin/organization");
            MockHttpSession mgrSession = new MockHttpSession();
            mgrSession.setAttribute("user", managerUser);
            mgrRequest.setSession(mgrSession);
            MockHttpServletResponse mgrResponse = new MockHttpServletResponse();

            allowed = authorizationInterceptor.preHandle(mgrRequest, mgrResponse, new Object());
            assertFalse(allowed);
            assertEquals(403, mgrResponse.getStatus());

            // Employee tries to query Admin Employee API (returns 403 JSON)
            MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/admin/api/employees");
            apiRequest.setSession(empSession);
            apiRequest.addHeader("Accept", "application/json");
            MockHttpServletResponse apiResponse = new MockHttpServletResponse();

            allowed = authorizationInterceptor.preHandle(apiRequest, apiResponse, new Object());
            assertFalse(allowed);
            assertEquals(403, apiResponse.getStatus());
            assertTrue(apiResponse.getContentType() != null && apiResponse.getContentType().startsWith("application/json"));
            assertTrue(apiResponse.getContentAsString().contains("Forbidden"));
        }

        @Test
        @DisplayName("Admin has access to admin, hr, manager, and management routes")
        void testAdminHasFullAdministrativeAccess() throws Exception {
            String[] adminAccessibleRoutes = {
                    "/admin/dashboard",
                    "/admin/organization",
                    "/admin/employees",
                    "/admin/register/hr",
                    "/hr/dashboard",
                    "/manager/dashboard",
                    "/reports",
                    "/reports/export/performance",
                    "/visitors/delete/5"
            };

            for (String uri : adminAccessibleRoutes) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
                MockHttpSession session = new MockHttpSession();
                session.setAttribute("user", adminUser);
                request.setSession(session);
                MockHttpServletResponse response = new MockHttpServletResponse();

                boolean allowed = authorizationInterceptor.preHandle(request, response, new Object());
                assertTrue(allowed, "Admin should be authorized for: " + uri);
                assertEquals(200, response.getStatus());
            }
        }
    }

    // =========================================================================
    // 3. Controller Security & IDOR Verification Tests
    // =========================================================================
    @Nested
    @DisplayName("3. Controller-Level IDOR and Privilege Enforcement")
    class ControllerSecurityTests {

        @Test
        @DisplayName("AdminController: Non-admins cannot access admin dashboard or execute payroll changes")
        void testAdminControllerRoleEnforcement() {
            MockHttpSession hrSession = new MockHttpSession();
            hrSession.setAttribute("user", hrUser);

            MockHttpSession adminSession = new MockHttpSession();
            adminSession.setAttribute("user", adminUser);

            Model model = new ConcurrentModel();
            RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

            // HR cannot access admin dashboard
            String dashResult = adminController.adminDashboard(model, hrSession);
            assertEquals("redirect:/login", dashResult);

            // Admin can access admin dashboard
            when(employeeRepository.findAll()).thenReturn(List.of(regularEmp1));
            when(employeeRepository.findCurrentlyPresentEmployees()).thenReturn(Collections.emptyList());
            when(visitorRepository.findAllOrderByCheckInTimeDesc()).thenReturn(Collections.emptyList());
            when(visitorRepository.findByStatus(org.mockito.ArgumentMatchers.anyString())).thenReturn(Collections.emptyList());
            when(meetingRepository.findOngoingMeetings()).thenReturn(Collections.emptyList());
            when(meetingRepository.findAll()).thenReturn(Collections.emptyList());
            String adminDash = adminController.adminDashboard(model, adminSession);
            assertEquals("admin-dashboard", adminDash);

            // Non-admin attempting to save payroll gets 403 Forbidden
            MockHttpServletResponse hrResp = new MockHttpServletResponse();
            Map<String, Object> forbiddenSave = adminController.savePayroll(101L, 90000.0, hrSession, hrResp);
            assertEquals(403, hrResp.getStatus());
            assertFalse((Boolean) forbiddenSave.get("success"));

            // Admin can save payroll
            when(employeeRepository.findById(101L)).thenReturn(Optional.of(regularEmp1));
            MockHttpServletResponse adminResp = new MockHttpServletResponse();
            Map<String, Object> okSave = adminController.savePayroll(101L, 90000.0, adminSession, adminResp);
            assertTrue((Boolean) okSave.get("success"));
            assertEquals(90000.0, regularEmp1.getSalary());
        }

        @Test
        @DisplayName("EmployeeManagementController: HR cannot modify employee base salary (Privilege escalation blocked)")
        void testEmployeeSalaryMassAssignmentProtection() {
            MockHttpSession hrSession = new MockHttpSession();
            hrSession.setAttribute("user", hrUser);
            hrSession.setAttribute("userRole", "HR");

            MockHttpSession adminSession = new MockHttpSession();
            adminSession.setAttribute("user", adminUser);
            adminSession.setAttribute("userRole", "ADMIN");

            Employee existing = new Employee();
            existing.setId(101L);
            existing.setEmployeeId("EMP101");
            existing.setName("Alice Dev");
            existing.setEmail("alice@logsphere.com");
            existing.setSalary(50000.0);

            when(employeeRepository.findById(101L)).thenReturn(Optional.of(existing));

            Employee updateAttempt = new Employee();
            updateAttempt.setId(101L);
            updateAttempt.setEmployeeId("EMP101");
            updateAttempt.setName("Alice Dev Updated");
            updateAttempt.setEmail("alice@logsphere.com");
            updateAttempt.setSalary(120000.0); // Attempt to inflate salary

            RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

            // 1. HR submits update with modified salary -> salary change is IGNORED
            employeeManagementController.updateEmployee(101L, updateAttempt, null, null, null, null, null, ra, hrSession);
            assertEquals(50000.0, existing.getSalary(), "HR must NOT be able to alter baseSalary");

            // 2. Admin submits update with modified salary -> salary change is ALLOWED
            updateAttempt.setSalary(95000.0);
            employeeManagementController.updateEmployee(101L, updateAttempt, null, null, null, null, null, ra, adminSession);
            assertEquals(95000.0, existing.getSalary(), "Admin is authorized to update baseSalary");
        }

        @Test
        @DisplayName("EmployeeController: API meeting details endpoint blocks IDOR for non-attendees")
        void testEmployeeMeetingDetailsIdorProtection() {
            Meeting confidentialMeeting = new Meeting();
            confidentialMeeting.setId(777L);
            confidentialMeeting.setTitle("Confidential 1-on-1");
            confidentialMeeting.setOrganizer(regularEmp1); // Alice
            confidentialMeeting.setAttendees(List.of(teamLeadEmp)); // Charlie
            confidentialMeeting.setInviteEveryone(false);

            when(meetingRepository.findById(777L)).thenReturn(Optional.of(confidentialMeeting));

            // Bob (unrelated employee) attempts to inspect meeting details
            MockHttpSession bobSession = new MockHttpSession();
            bobSession.setAttribute("employee", regularEmp2);

            ResponseEntity<?> bobResponse = employeeController.getMeetingDetails(777L, bobSession);
            assertEquals(HttpStatus.FORBIDDEN.value(), bobResponse.getStatusCode().value(),
                    "Unrelated employee must receive 403 Forbidden on meeting details");

            // Alice (organizer) inspects meeting details
            MockHttpSession aliceSession = new MockHttpSession();
            aliceSession.setAttribute("employee", regularEmp1);

            ResponseEntity<?> aliceResponse = employeeController.getMeetingDetails(777L, aliceSession);
            assertEquals(HttpStatus.OK.value(), aliceResponse.getStatusCode().value(),
                    "Organizer must be able to view meeting details");

            // Charlie (attendee) inspects meeting details
            MockHttpSession charlieSession = new MockHttpSession();
            charlieSession.setAttribute("employee", teamLeadEmp);

            ResponseEntity<?> charlieResponse = employeeController.getMeetingDetails(777L, charlieSession);
            assertEquals(HttpStatus.OK.value(), charlieResponse.getStatusCode().value(),
                    "Attendee must be able to view meeting details");
        }

        @Test
        @DisplayName("EmployeeController: Payslip inspection blocks IDOR between employees")
        void testEmployeePayslipIdorProtection() {
            Payroll alicePayroll = new Payroll();
            alicePayroll.setId(888L);
            alicePayroll.setEmployee(regularEmp1);

            when(payrollRepository.findById(888L)).thenReturn(Optional.of(alicePayroll));

            MockHttpSession bobSession = new MockHttpSession();
            bobSession.setAttribute("employee", regularEmp2);

            Model model = new ConcurrentModel();
            RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

            // Bob attempts to view Alice's payslip
            String viewResult = employeeController.viewPayslip(888L, model, bobSession, ra);
            assertEquals("redirect:/employee/payroll/history", viewResult);
            assertTrue(ra.getFlashAttributes().containsKey("error"));
            assertEquals("Unauthorized access to payslip.", ra.getFlashAttributes().get("error"));

            // Alice views her own payslip
            MockHttpSession aliceSession = new MockHttpSession();
            aliceSession.setAttribute("employee", regularEmp1);
            String aliceResult = employeeController.viewPayslip(888L, model, aliceSession, ra);
            assertEquals("employee-payslip-view", aliceResult);
        }

        @Test
        @DisplayName("MeetingController: Spoofed organizerId is overridden with authenticated employee ID")
        void testMeetingOrganizerSpoofingPrevention() {
            MockHttpSession aliceSession = new MockHttpSession();
            aliceSession.setAttribute("employee", regularEmp1); // Alice ID = 101

            Meeting meeting = new Meeting();
            meeting.setTitle("Security Sync");
            meeting.setMeetingType("INTERNAL");

            when(employeeRepository.findById(101L)).thenReturn(Optional.of(regularEmp1));
            when(employeeRepository.findById(102L)).thenReturn(Optional.of(regularEmp2));

            RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

            // Alice sends organizerId = 102L (Bob) in attempt to spoof organizer
            meetingController.createMeeting(meeting, 102L, null, false, null, aliceSession, ra);

            // Verify meeting organizer was forced to Alice (101L), NOT Bob (102L)
            assertNotNull(meeting.getOrganizer());
            assertEquals(101L, meeting.getOrganizer().getId(), "Organizer ID must be forced to authenticated employee");
        }

        @Test
        @DisplayName("MeetingController: Non-organizer employee cannot end meeting")
        void testMeetingEndAuthorization() {
            Meeting meeting = new Meeting();
            meeting.setId(650L);
            meeting.setOrganizer(regularEmp1); // Alice is organizer
            meeting.setStatus("ONGOING");

            when(meetingRepository.findById(650L)).thenReturn(Optional.of(meeting));

            MockHttpSession bobSession = new MockHttpSession();
            bobSession.setAttribute("employee", regularEmp2); // Bob

            RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

            // Bob attempts to end Alice's meeting
            String bobResult = meetingController.endMeeting(650L, bobSession, ra);
            assertEquals("redirect:/employee/dashboard", bobResult);
            assertTrue(ra.getFlashAttributes().containsKey("error"));
            assertEquals("ONGOING", meeting.getStatus(), "Meeting status should remain ONGOING");

            // Alice ends her meeting
            MockHttpSession aliceSession = new MockHttpSession();
            aliceSession.setAttribute("employee", regularEmp1);
            String aliceResult = meetingController.endMeeting(650L, aliceSession, ra);
            assertEquals("redirect:/employee/dashboard", aliceResult);
            assertEquals("COMPLETED", meeting.getStatus());
        }

        @Test
        @DisplayName("TaskController: Team lead cannot delete tasks of other teams (Cross-team BOLA blocked)")
        void testTaskDeletionCrossTeamBolaPrevention() {
            Task taskBeta = new Task();
            taskBeta.setId(2001L);
            taskBeta.setEmployee(regularEmp2); // regularEmp2 is in teamBeta

            when(taskRepository.findById(2001L)).thenReturn(Optional.of(taskBeta));

            MockHttpSession charlieLeadSession = new MockHttpSession();
            charlieLeadSession.setAttribute("employee", teamLeadEmp); // Lead of teamAlpha

            RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

            // Team Alpha Lead attempts to delete Team Beta Task
            String deleteResult = taskController.deleteTask(2001L, charlieLeadSession, ra);
            assertEquals("redirect:/employee/tasks", deleteResult);
            assertTrue(ra.getFlashAttributes().containsKey("error"));
            assertTrue(ra.getFlashAttributes().get("error").toString().contains("Unauthorized"));
            verify(taskRepository, never()).deleteById(2001L);

            // Admin deletes task
            MockHttpSession adminSession = new MockHttpSession();
            adminSession.setAttribute("user", adminUser);
            String adminDeleteResult = taskController.deleteTask(2001L, adminSession, ra);
            assertEquals("redirect:/employee/tasks", adminDeleteResult);
            verify(taskRepository, times(1)).deleteById(2001L);
        }

        @Test
        @DisplayName("VisitorController: Deletion is strictly reserved for ADMIN role")
        void testVisitorDeletionAdminOnly() {
            MockHttpSession hrSession = new MockHttpSession();
            hrSession.setAttribute("user", hrUser);

            MockHttpSession adminSession = new MockHttpSession();
            adminSession.setAttribute("user", adminUser);

            RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

            // HR attempts to delete visitor
            String hrResult = visitorController.deleteVisitor(99L, hrSession, ra);
            assertEquals("redirect:/visitors/dashboard", hrResult);
            assertTrue(ra.getFlashAttributes().containsKey("error"));
            verify(visitorRepository, never()).deleteById(99L);

            // Admin deletes visitor
            String adminResult = visitorController.deleteVisitor(99L, adminSession, ra);
            assertEquals("redirect:/visitors/dashboard", adminResult);
            verify(visitorRepository, times(1)).deleteById(99L);
        }

        @Test
        @DisplayName("ReportController: Performance export is restricted to ADMIN and HR; blocked for others")
        void testPerformanceExportRoleScoping() throws IOException {
            MockHttpSession empSession = new MockHttpSession();
            empSession.setAttribute("employee", regularEmp1);

            MockHttpServletResponse empResponse = new MockHttpServletResponse();
            reportController.exportPerformance(empResponse, empSession);
            assertEquals(403, empResponse.getStatus(), "Regular employee must be denied performance export with 403");

            MockHttpSession mgrSession = new MockHttpSession();
            mgrSession.setAttribute("user", managerUser);

            MockHttpServletResponse mgrResponse = new MockHttpServletResponse();
            reportController.exportPerformance(mgrResponse, mgrSession);
            assertEquals(403, mgrResponse.getStatus(), "Manager must be denied performance export with 403");

            MockHttpSession hrSession = new MockHttpSession();
            hrSession.setAttribute("user", hrUser);

            when(employeeRepository.findAll()).thenReturn(Collections.emptyList());
            MockHttpServletResponse hrResponse = new MockHttpServletResponse();
            reportController.exportPerformance(hrResponse, hrSession);
            assertEquals(200, hrResponse.getStatus(), "HR is authorized to export performance CSV");
            assertEquals("text/csv", hrResponse.getContentType());
        }

        @Test
        @DisplayName("PageController: Unauthenticated users are redirected to login")
        void testPageControllerAuthenticationEnforcement() {
            MockHttpSession anonSession = new MockHttpSession();
            assertEquals("redirect:/login", pageController.dashboard(anonSession));
            assertEquals("redirect:/login", pageController.timelog(anonSession));
            assertEquals("redirect:/login", pageController.qrScanner(anonSession));

            MockHttpSession empSession = new MockHttpSession();
            empSession.setAttribute("employee", regularEmp1);
            assertEquals("dashboard", pageController.dashboard(empSession));
            assertEquals("timelog", pageController.timelog(empSession));
            assertEquals("qr-scanner", pageController.qrScanner(empSession));
        }
    }
}
