package com.example.demo;

import com.example.demo.model.Employee;
import com.example.demo.model.Payroll;
import com.example.demo.model.User;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.PayrollRepository;
import com.example.demo.repository.UserRepository;
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

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthorizationSecurityRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    private Employee employeeA;
    private Employee employeeB;
    private Payroll payrollA;
    private Payroll payrollB;

    @BeforeEach
    public void setup() {
        cleanup();

        employeeA = new Employee();
        employeeA.setName("Alice Employee");
        employeeA.setEmail("alice.authz@example.com");
        employeeA.setEmployeeId("EMP_ALICE");
        employeeA.setDepartment("Engineering");
        employeeA.setStatus("ACTIVE");
        employeeA = employeeRepository.save(employeeA);

        employeeB = new Employee();
        employeeB.setName("Bob Employee");
        employeeB.setEmail("bob.authz@example.com");
        employeeB.setEmployeeId("EMP_BOB");
        employeeB.setDepartment("Operations");
        employeeB.setStatus("ACTIVE");
        employeeB = employeeRepository.save(employeeB);

        payrollA = new Payroll();
        payrollA.setEmployee(employeeA);
        payrollA.setMonth(1);
        payrollA.setYear(2026);
        payrollA.setNetPay(5000.0);
        payrollA.setPayrollStatus(Payroll.PayrollStatus.PAID);
        payrollA.setPayPeriodStart(LocalDate.of(2026, 1, 1));
        payrollA.setPayPeriodEnd(LocalDate.of(2026, 1, 31));
        payrollA = payrollRepository.save(payrollA);

        payrollB = new Payroll();
        payrollB.setEmployee(employeeB);
        payrollB.setMonth(1);
        payrollB.setYear(2026);
        payrollB.setNetPay(6000.0);
        payrollB.setPayrollStatus(Payroll.PayrollStatus.PAID);
        payrollB.setPayPeriodStart(LocalDate.of(2026, 1, 1));
        payrollB.setPayPeriodEnd(LocalDate.of(2026, 1, 31));
        payrollB = payrollRepository.save(payrollB);
    }

    @AfterEach
    public void tearDown() {
        cleanup();
    }

    private void cleanup() {
        payrollRepository.deleteAll();
        employeeRepository.findByEmail("alice.authz@example.com").ifPresent(employeeRepository::delete);
        employeeRepository.findByEmail("bob.authz@example.com").ifPresent(employeeRepository::delete);
    }

    @Test
    @DisplayName("IDOR/BOLA Defense: Employee A cannot access Employee B's payslip")
    public void testIdorPayslipAccessPrevented() throws Exception {
        MockHttpSession aliceSession = new MockHttpSession();
        aliceSession.setAttribute("employee", employeeA);
        aliceSession.setAttribute("employeeId", employeeA.getEmployeeId());

        // Alice attempts to access Bob's payslip
        mockMvc.perform(get("/employee/payroll/view/" + payrollB.getId())
                        .session(aliceSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employee/payroll/history"))
                .andExpect(flash().attribute("error", "Unauthorized access to payslip."));
    }

    @Test
    @DisplayName("Authorized Access: Employee A can access their own payslip")
    public void testAuthorizedPayslipAccessAllowed() throws Exception {
        MockHttpSession aliceSession = new MockHttpSession();
        aliceSession.setAttribute("employee", employeeA);
        aliceSession.setAttribute("employeeId", employeeA.getEmployeeId());

        mockMvc.perform(get("/employee/payroll/view/" + payrollA.getId())
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("payroll"))
                .andExpect(view().name("employee-payslip-view"));
    }

    @Test
    @DisplayName("RBAC: Unauthenticated user is redirected to login when accessing admin dashboard")
    public void testUnauthenticatedAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    @DisplayName("RBAC: Employee role cannot access Admin routes")
    public void testEmployeeRoleForbiddenFromAdminRoutes() throws Exception {
        MockHttpSession empSession = new MockHttpSession();
        empSession.setAttribute("employee", employeeA);
        empSession.setAttribute("employeeId", employeeA.getEmployeeId());

        mockMvc.perform(get("/admin/dashboard").session(empSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC: HR role cannot access strictly Admin routes like organization management")
    public void testHrForbiddenFromAdminOrganization() throws Exception {
        MockHttpSession hrSession = new MockHttpSession();
        User hrUser = new User("hr.authz@example.com", "pass", "HR User", "HR");
        hrSession.setAttribute("user", hrUser);
        hrSession.setAttribute("userRole", "HR");

        mockMvc.perform(get("/admin/organization").session(hrSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC: Admin has authorized access to admin dashboard and employee management")
    public void testAdminAuthorizedAccess() throws Exception {
        MockHttpSession adminSession = new MockHttpSession();
        User adminUser = new User("admin.authz@example.com", "pass", "Admin User", "ADMIN");
        adminSession.setAttribute("user", adminUser);
        adminSession.setAttribute("userRole", "ADMIN");

        mockMvc.perform(get("/admin/dashboard").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-dashboard"));
    }

    @Test
    @DisplayName("RBAC: Manager cannot access HR-specific payroll generation")
    public void testManagerForbiddenFromHrPayrollGeneration() throws Exception {
        MockHttpSession managerSession = new MockHttpSession();
        User managerUser = new User("manager.authz@example.com", "pass", "Manager User", "MANAGER");
        managerSession.setAttribute("user", managerUser);
        managerSession.setAttribute("userRole", "MANAGER");

        mockMvc.perform(get("/hr/payroll").session(managerSession))
                .andExpect(status().isForbidden());
    }
}
