package com.example.demo;

import com.example.demo.controller.AdminController;
import com.example.demo.controller.EmployeeController;
import com.example.demo.controller.EmployeeManagementController;
import com.example.demo.controller.OrganizationController;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.service.QRCodeService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AdminBugsVerificationTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private PayrollRepository payrollRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private QRCodeService qrCodeService;

    @Mock
    private HttpSession session;

    @InjectMocks
    private AdminController adminController;

    @InjectMocks
    private OrganizationController organizationController;

    @InjectMocks
    private EmployeeController employeeController;

    @InjectMocks
    private EmployeeManagementController employeeManagementController;

    private User adminUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@example.com");
        adminUser.setName("System Admin");
        adminUser.setRole("ADMIN");

        when(session.getAttribute("user")).thenReturn(adminUser);
        when(session.getAttribute("userEmail")).thenReturn("admin@example.com");
        when(session.getAttribute("userName")).thenReturn("System Admin");
        when(session.getAttribute("role")).thenReturn("ADMIN");
        when(session.getAttribute("userRole")).thenReturn("ADMIN");
    }

    // =========================================================================
    // LOG-ADM-001: Idle Employees Alert should be removed from Admin Dashboard
    // =========================================================================
    @Test
    @DisplayName("LOG-ADM-001: Verify Idle Employees Alert is removed from admin-dashboard.html")
    void testIdleEmployeesAlertRemovedFromAdminDashboard() throws Exception {
        String content = Files.readString(Paths.get("src/main/resources/templates/admin-dashboard.html"));
        assertFalse(content.contains("Idle Employees Alert"),
                "admin-dashboard.html should not contain Idle Employees Alert section");
        assertFalse(content.contains("th:if=\"${idleCount > 0}\""),
                "admin-dashboard.html should not contain idleCount > 0 condition");
        assertFalse(content.contains("th:each=\"inc : ${idleIncidents}\""),
                "admin-dashboard.html should not render idleIncidents table on admin dashboard");
    }

    // =========================================================================
    // LOG-ADM-002: Employee selection fields should support search and manual input
    // =========================================================================
    @Test
    @DisplayName("LOG-ADM-002: Employee search/filter input exists on Admin Dashboard and Employee Management")
    void testEmployeeSearchInputInDashboards() throws Exception {
        String dashboardContent = Files.readString(Paths.get("src/main/resources/templates/admin-dashboard.html"));
        assertTrue(dashboardContent.contains("id=\"employeeFilterInput\""),
                "admin-dashboard.html must have #employeeFilterInput for real-time searching");

        String empMgmtContent = Files.readString(Paths.get("src/main/resources/templates/admin-employee-management.html"));
        assertTrue(empMgmtContent.contains("id=\"employeeFilterInput\""),
                "admin-employee-management.html must have #employeeFilterInput for searching");
    }

    @Test
    @DisplayName("LOG-ADM-002: Searchable datalist & manual input in admin-payroll.html")
    void testSearchableEmployeeInPayrollModal() throws Exception {
        String content = Files.readString(Paths.get("src/main/resources/templates/admin-payroll.html"));
        assertTrue(content.contains("id=\"employeeSearchInput\""),
                "admin-payroll.html must contain #employeeSearchInput for manual search and typing");
        assertTrue(content.contains("id=\"employeeDatalist\""),
                "admin-payroll.html must contain #employeeDatalist for employee suggestions");
        assertTrue(content.contains("list=\"employeeDatalist\""),
                "admin-payroll.html search input must link to employeeDatalist");
    }

    @Test
    @DisplayName("LOG-ADM-002: Searchable datalist & manual input in admin-employee-logs.html")
    void testSearchableEmployeeInEmployeeLogs() throws Exception {
        String content = Files.readString(Paths.get("src/main/resources/templates/admin-employee-logs.html"));
        assertTrue(content.contains("id=\"employeeSearchInput\""),
                "admin-employee-logs.html must contain #employeeSearchInput");
        assertTrue(content.contains("id=\"employeeDatalist\""),
                "admin-employee-logs.html must contain #employeeDatalist");
        assertTrue(content.contains("list=\"employeeDatalist\""),
                "admin-employee-logs.html search input must link to employeeDatalist");
    }

    @Test
    @DisplayName("LOG-ADM-002: Searchable Lead and Owner selection in Team and Project forms")
    void testSearchableTeamLeadAndProjectOwner() throws Exception {
        String teamFormContent = Files.readString(Paths.get("src/main/resources/templates/admin-team-form.html"));
        assertTrue(teamFormContent.contains("id=\"teamLeadSearchInput\""),
                "admin-team-form.html must contain #teamLeadSearchInput");
        assertTrue(teamFormContent.contains("id=\"teamLeadDatalist\""),
                "admin-team-form.html must contain #teamLeadDatalist");

        String projectFormContent = Files.readString(Paths.get("src/main/resources/templates/admin-project-form.html"));
        assertTrue(projectFormContent.contains("id=\"projectOwnerSearchInput\""),
                "admin-project-form.html must contain #projectOwnerSearchInput");
        assertTrue(projectFormContent.contains("id=\"projectOwnerDatalist\""),
                "admin-project-form.html must contain #projectOwnerDatalist");
    }

    // =========================================================================
    // LOG-ADM-003: Calendar and form field validations
    // =========================================================================
    @Test
    @DisplayName("LOG-ADM-003: Admin Payroll save validations for missing employee and invalid salary")
    void testPayrollSaveValidations() {
        // 1. Missing employee ID
        Map<String, Object> resp1 = adminController.savePayroll(null, 50000.0);
        assertFalse((Boolean) resp1.get("success"));
        assertEquals("Employee selection is required", resp1.get("message"));

        // 2. Non-positive base salary
        Map<String, Object> resp2 = adminController.savePayroll(10L, 0.0);
        assertFalse((Boolean) resp2.get("success"));
        assertEquals("Base salary must be greater than zero", resp2.get("message"));

        Map<String, Object> resp3 = adminController.savePayroll(10L, -100.0);
        assertFalse((Boolean) resp3.get("success"));
        assertEquals("Base salary must be greater than zero", resp3.get("message"));
    }

    @Test
    @DisplayName("LOG-ADM-003: Admin Register HR form validations")
    void testRegisterHrValidations() {
        // Missing name
        User user1 = new User("hr@example.com", "password123", "", "HR");
        RedirectAttributes ra1 = new RedirectAttributesModelMap();
        String view1 = adminController.registerHr(user1, ra1, session);
        assertEquals("redirect:/admin/register/hr", view1);
        assertEquals("Full name is required!", ra1.getFlashAttributes().get("error"));

        // Invalid email
        User user2 = new User("invalid-email", "password123", "HR User", "HR");
        RedirectAttributes ra2 = new RedirectAttributesModelMap();
        String view2 = adminController.registerHr(user2, ra2, session);
        assertEquals("redirect:/admin/register/hr", view2);
        assertEquals("A valid email address is required!", ra2.getFlashAttributes().get("error"));

        // Short password
        User user3 = new User("hr@example.com", "123", "HR User", "HR");
        RedirectAttributes ra3 = new RedirectAttributesModelMap();
        String view3 = adminController.registerHr(user3, ra3, session);
        assertEquals("redirect:/admin/register/hr", view3);
        assertEquals("Password must be at least 6 characters long!", ra3.getFlashAttributes().get("error"));

        // Duplicate email
        when(userRepository.findByEmail("hr@example.com")).thenReturn(Optional.of(new User()));
        User user4 = new User("hr@example.com", "password123", "HR User", "HR");
        RedirectAttributes ra4 = new RedirectAttributesModelMap();
        String view4 = adminController.registerHr(user4, ra4, session);
        assertEquals("redirect:/admin/register/hr", view4);
        assertEquals("Email already exists!", ra4.getFlashAttributes().get("error"));
    }

    @Test
    @DisplayName("LOG-ADM-003: Admin Register Manager form validations")
    void testRegisterManagerValidations() {
        // Missing name
        User user1 = new User("mgr@example.com", "password123", "   ", "MANAGER");
        RedirectAttributes ra1 = new RedirectAttributesModelMap();
        String view1 = adminController.registerManager(user1, ra1, session);
        assertEquals("redirect:/admin/register/manager", view1);
        assertEquals("Full name is required!", ra1.getFlashAttributes().get("error"));

        // Invalid email
        User user2 = new User("invalid-mgr", "password123", "Manager User", "MANAGER");
        RedirectAttributes ra2 = new RedirectAttributesModelMap();
        String view2 = adminController.registerManager(user2, ra2, session);
        assertEquals("redirect:/admin/register/manager", view2);
        assertEquals("A valid email address is required!", ra2.getFlashAttributes().get("error"));

        // Short password
        User user3 = new User("mgr@example.com", "abc", "Manager User", "MANAGER");
        RedirectAttributes ra3 = new RedirectAttributesModelMap();
        String view3 = adminController.registerManager(user3, ra3, session);
        assertEquals("redirect:/admin/register/manager", view3);
        assertEquals("Password must be at least 6 characters long!", ra3.getFlashAttributes().get("error"));
    }

    @Test
    @DisplayName("LOG-ADM-003: Organization Controller Department and Team validations")
    void testDepartmentAndTeamValidations() {
        RedirectAttributes ra1 = new RedirectAttributesModelMap();
        String viewDept = organizationController.saveDepartment(null, "   ", null, null, ra1, session);
        assertEquals("redirect:/admin/organization/departments/add", viewDept);
        assertEquals("Department name is required", ra1.getFlashAttributes().get("error"));

        RedirectAttributes ra2 = new RedirectAttributesModelMap();
        String viewTeam1 = organizationController.saveTeam(null, "", null, null, null, ra2, session);
        assertEquals("redirect:/admin/organization/teams/add", viewTeam1);
        assertEquals("Team name is required", ra2.getFlashAttributes().get("error"));

        RedirectAttributes ra3 = new RedirectAttributesModelMap();
        String viewTeam2 = organizationController.saveTeam(null, "Core Platform Team", null, null, null, ra3, session);
        assertEquals("redirect:/admin/organization/teams/add", viewTeam2);
        assertEquals("Department selection is required", ra3.getFlashAttributes().get("error"));
    }

    @Test
    @DisplayName("LOG-ADM-003: Project form calendar date validation (startDate <= endDate)")
    void testProjectDateValidation() {
        RedirectAttributes ra1 = new RedirectAttributesModelMap();
        String view1 = organizationController.saveProject(null, "   ", null, null, null, null, "2026-10-10", "2026-10-20", "ACTIVE", ra1, session);
        assertEquals("redirect:/admin/organization/projects/add", view1);
        assertEquals("Project name is required", ra1.getFlashAttributes().get("error"));

        // Invalid calendar date range: start date is after end date
        RedirectAttributes ra2 = new RedirectAttributesModelMap();
        String view2 = organizationController.saveProject(null, "CRM 2.0", "CRM", "Description", null, null, "2026-10-25", "2026-10-10", "ACTIVE", ra2, session);
        assertEquals("redirect:/admin/organization/projects/add", view2);
        assertEquals("Start date cannot be after end date", ra2.getFlashAttributes().get("error"));
    }

    @Test
    @DisplayName("LOG-ADM-003: Employee Logs calendar date validation")
    void testEmployeeLogsDateValidation() {
        Model model = new ConcurrentModel();
        when(employeeRepository.findAll()).thenReturn(Collections.emptyList());

        // startDate after endDate
        String view = employeeController.allEmployeeLogs(model, session, null, "2026-10-20", "2026-10-10");
        assertEquals("admin-employee-logs", view);
        assertNotNull(model.getAttribute("error"));
        assertEquals("Start date cannot be after end date", model.getAttribute("error"));
    }

    @Test
    @DisplayName("LOG-ADM-003: Employee add and update form validations")
    void testEmployeeManagementValidations() {
        // Add Employee without ID
        Employee empNoId = new Employee();
        RedirectAttributes ra1 = new RedirectAttributesModelMap();
        employeeManagementController.addEmployee(empNoId, null, null, null, null, ra1, session);
        assertEquals("Employee ID is required!", ra1.getFlashAttributes().get("error"));

        // Add Employee without Name
        Employee empNoName = new Employee();
        empNoName.setEmployeeId("EMP101");
        RedirectAttributes ra2 = new RedirectAttributesModelMap();
        employeeManagementController.addEmployee(empNoName, null, null, null, null, ra2, session);
        assertEquals("Employee name is required!", ra2.getFlashAttributes().get("error"));

        // Add Employee with invalid email
        Employee empInvalidEmail = new Employee();
        empInvalidEmail.setEmployeeId("EMP101");
        empInvalidEmail.setName("John");
        empInvalidEmail.setEmail("invalid-email");
        RedirectAttributes ra3 = new RedirectAttributesModelMap();
        employeeManagementController.addEmployee(empInvalidEmail, null, null, null, null, ra3, session);
        assertEquals("A valid email address is required!", ra3.getFlashAttributes().get("error"));

        // Add Employee with negative salary
        Employee empNegativeSalary = new Employee();
        empNegativeSalary.setEmployeeId("EMP101");
        empNegativeSalary.setName("John");
        empNegativeSalary.setEmail("john@example.com");
        empNegativeSalary.setDepartment("Engineering");
        empNegativeSalary.setSalary(-500.0);
        RedirectAttributes ra4 = new RedirectAttributesModelMap();
        employeeManagementController.addEmployee(empNegativeSalary, null, null, null, null, ra4, session);
        assertEquals("Base salary cannot be negative!", ra4.getFlashAttributes().get("error"));

        // Update Employee validations
        Employee existing = new Employee();
        existing.setId(5L);
        existing.setEmployeeId("EMP005");
        existing.setName("Existing Emp");
        existing.setEmail("existing@example.com");
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(existing));

        Employee updatedInvalid = new Employee();
        updatedInvalid.setName("Updated Emp");
        updatedInvalid.setEmail("bad-email");
        RedirectAttributes ra5 = new RedirectAttributesModelMap();
        employeeManagementController.updateEmployee(5L, updatedInvalid, null, null, null, null, null, ra5, session);
        assertEquals("A valid email address is required!", ra5.getFlashAttributes().get("error"));
    }

    // =========================================================================
    // LOG-ADM-004: CSS styling implemented consistently across Admin Dashboard pages
    // =========================================================================
    @Test
    @DisplayName("LOG-ADM-004: Check shared admin-sidebar fragment presence and consistent CSS across admin views")
    void testAdminTemplatesConsistentCssAndSidebar() throws Exception {
        String sidebarFragment = Files.readString(Paths.get("src/main/resources/templates/fragments/admin-sidebar.html"));
        assertTrue(sidebarFragment.contains("th:fragment=\"sidebar(activeTab)\""),
                "admin-sidebar.html must define the reusable sidebar fragment");
        assertTrue(sidebarFragment.contains("toggleAdminSidebar()"),
                "admin-sidebar.html must include mobile drawer toggle function");
        assertTrue(sidebarFragment.contains("adminSidebarOverlay"),
                "admin-sidebar.html must include overlay backdrop for mobile responsiveness");

        String[] adminPages = {
                "admin-dashboard.html",
                "admin-payroll.html",
                "admin-employee-logs.html",
                "admin-employee-edit.html",
                "admin-employee-management.html",
                "admin-organization.html",
                "admin-departments.html",
                "admin-teams.html",
                "admin-projects.html",
                "admin-department-form.html",
                "admin-team-form.html",
                "admin-project-form.html",
                "admin-register-hr.html",
                "admin-register-manager.html",
                "meeting-list.html",
                "visitor-dashboard.html",
                "visitor-history.html",
                "visitor-search.html"
        };

        for (String page : adminPages) {
            String content = Files.readString(Paths.get("src/main/resources/templates/" + page));
            assertTrue(content.contains("fragments/admin-sidebar"),
                    page + " must include the unified admin-sidebar fragment");
            assertTrue(content.contains("name=\"viewport\""),
                    page + " must contain responsive viewport meta tag");
            assertTrue(content.contains("toggleAdminSidebar()"),
                    page + " must contain mobile drawer toggle button");
        }
    }

    // =========================================================================
    // LOG-ADM-005: Create Meeting option removed from view-only Admin Dashboard
    // =========================================================================
    @Test
    @DisplayName("LOG-ADM-005: Verify Create Meeting option is removed from Admin Dashboard and Sidebar")
    void testCreateMeetingRemovedFromAdminDashboardAndSidebar() throws Exception {
        String sidebarContent = Files.readString(Paths.get("src/main/resources/templates/fragments/admin-sidebar.html"));
        assertFalse(sidebarContent.contains("Create Meeting"),
                "admin-sidebar.html must NOT contain Create Meeting option");
        assertFalse(sidebarContent.contains("/meeting/create"),
                "admin-sidebar.html must NOT contain meeting creation link");

        String dashboardContent = Files.readString(Paths.get("src/main/resources/templates/admin-dashboard.html"));
        assertFalse(dashboardContent.contains("Create Meeting"),
                "admin-dashboard.html must NOT contain Create Meeting option");
        assertFalse(dashboardContent.contains("/meeting/create"),
                "admin-dashboard.html must NOT contain meeting creation link");
    }

    @Test
    @DisplayName("Verify Projects & Reports items (Issues, Sprints, Reports) removed from Admin Dashboard and Sidebar")
    void testProjectsAndReportsRemovedFromAdminDashboardAndSidebar() throws Exception {
        String sidebarContent = Files.readString(Paths.get("src/main/resources/templates/fragments/admin-sidebar.html"));
        assertFalse(sidebarContent.contains("th:href=\"@{/issues}\""),
                "admin-sidebar.html must NOT contain Issues link");
        assertFalse(sidebarContent.contains("th:href=\"@{/sprints}\""),
                "admin-sidebar.html must NOT contain Sprints link");
        assertFalse(sidebarContent.contains("th:href=\"@{/reports}\""),
                "admin-sidebar.html must NOT contain Reports link");

        String dashboardContent = Files.readString(Paths.get("src/main/resources/templates/admin-dashboard.html"));
        assertFalse(dashboardContent.contains("th:href=\"@{/issues}\""),
                "admin-dashboard.html must NOT contain Issues link");
        assertFalse(dashboardContent.contains("Open Issues"),
                "admin-dashboard.html must NOT contain Open Issues stat card");
    }
}
