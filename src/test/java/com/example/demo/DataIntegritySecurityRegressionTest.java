package com.example.demo;

import com.example.demo.config.GlobalDatabaseExceptionHandler;
import com.example.demo.model.Employee;
import com.example.demo.model.Payroll;
import com.example.demo.model.User;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.PayrollRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DataIntegritySecurityRegressionTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GlobalDatabaseExceptionHandler globalDatabaseExceptionHandler;

    @BeforeEach
    public void setup() {
        cleanup();
    }

    @AfterEach
    public void tearDown() {
        cleanup();
    }

    private void cleanup() {
        payrollRepository.deleteAll();
        employeeRepository.findByEmail("data.integrity1@example.com").ifPresent(employeeRepository::delete);
        employeeRepository.findByEmail("data.integrity2@example.com").ifPresent(employeeRepository::delete);
        employeeRepository.findByEmail("tx.test@example.com").ifPresent(employeeRepository::delete);
        userRepository.findByEmail("sql.inject@example.com").ifPresent(userRepository::delete);
    }

    // =========================================================================
    // 1. UNIQUE CONSTRAINTS AND DATA INTEGRITY
    // =========================================================================

    @Test
    @DisplayName("Unique Constraint: Duplicate employee identifier is rejected at the database level")
    public void testDuplicateEmployeeIdRejected() {
        Employee emp1 = new Employee();
        emp1.setName("Emp One");
        emp1.setEmail("data.integrity1@example.com");
        emp1.setEmployeeId("EMP_UNIQUE_TEST");
        emp1.setDepartment("IT");
        emp1.setStatus("ACTIVE");
        employeeRepository.saveAndFlush(emp1);

        Employee emp2 = new Employee();
        emp2.setName("Emp Two");
        emp2.setEmail("data.integrity2@example.com");
        emp2.setEmployeeId("EMP_UNIQUE_TEST"); // Identical ID
        emp2.setDepartment("Finance");
        emp2.setStatus("ACTIVE");

        assertThrows(DataIntegrityViolationException.class, () -> {
            employeeRepository.saveAndFlush(emp2);
        }, "Database must enforce unique constraint on employee_id");
    }

    @Test
    @DisplayName("Composite Unique Constraint: Duplicate payroll for same employee, month, and year is rejected")
    public void testDuplicatePayrollPeriodRejected() {
        Employee emp = new Employee();
        emp.setName("Payroll Employee");
        emp.setEmail("data.integrity1@example.com");
        emp.setEmployeeId("EMP_PAYROLL_UNIQ");
        emp.setDepartment("Operations");
        emp.setStatus("ACTIVE");
        emp = employeeRepository.saveAndFlush(emp);

        Payroll payroll1 = new Payroll();
        payroll1.setEmployee(emp);
        payroll1.setMonth(5);
        payroll1.setYear(2026);
        payroll1.setNetPay(7500.0);
        payroll1.setPayrollStatus(Payroll.PayrollStatus.PAID);
        payroll1.setPayPeriodStart(LocalDate.of(2026, 5, 1));
        payroll1.setPayPeriodEnd(LocalDate.of(2026, 5, 31));
        payrollRepository.saveAndFlush(payroll1);

        Payroll payroll2 = new Payroll();
        payroll2.setEmployee(emp);
        payroll2.setMonth(5); // Same month
        payroll2.setYear(2026);  // Same year
        payroll2.setNetPay(8000.0);
        payroll2.setPayrollStatus(Payroll.PayrollStatus.PAID);
        payroll2.setPayPeriodStart(LocalDate.of(2026, 5, 1));
        payroll2.setPayPeriodEnd(LocalDate.of(2026, 5, 31));

        assertThrows(DataIntegrityViolationException.class, () -> {
            payrollRepository.saveAndFlush(payroll2);
        }, "Composite unique constraint uk_payroll_emp_month_year must reject duplicate payroll period");
    }

    // =========================================================================
    // 2. TRANSACTIONAL ATOMICITY AND ROLLBACK
    // =========================================================================

    @Test
    @DisplayName("Transaction Atomicity: Multi-step mutation completely rolls back when an intermediate step fails")
    public void testMultiStepTransactionRollback() {
        String testEmpId = "TX_ROLLBACK_EMP";
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            Employee emp = new Employee();
            emp.setName("Tx Employee");
            emp.setEmail("tx.test@example.com");
            emp.setEmployeeId(testEmpId);
            emp.setDepartment("Engineering");
            emp.setStatus("ACTIVE");
            employeeRepository.save(emp);

            // Trigger an intentional failure in second step
            throw new RuntimeException("Simulated intermediate failure in multi-step mutation");
        } catch (RuntimeException e) {
            transactionManager.rollback(status);
        }

        // Verify the entire transaction rolled back and no partial record persists
        Optional<Employee> found = employeeRepository.findByEmployeeId(testEmpId);
        assertTrue(found.isEmpty(), "Rolled back transaction must not leave orphaned records in database");
    }

    // =========================================================================
    // 3. EXCEPTION SANITIZATION AND LEAKAGE DEFENSE
    // =========================================================================

    @Test
    @DisplayName("Exception Sanitization: Database exceptions return sanitized user messages without SQL/schema details")
    public void testDatabaseExceptionSanitization() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Duplicate entry 'test@example.com' for key 'uk_user_email' in SQL statement [insert into users...]");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object result = globalDatabaseExceptionHandler.handleDataIntegrityViolation(ex, request, response);
        assertNotNull(result);

        if (result instanceof org.springframework.http.ResponseEntity<?> entity) {
            Map<?, ?> body = (Map<?, ?>) entity.getBody();
            assertNotNull(body);
            String message = (String) body.get("message");
            assertNotNull(message);
            assertFalse(message.toLowerCase().contains("sql"), "Sanitized error must not mention SQL");
            assertFalse(message.contains("users"), "Sanitized error must not disclose table names");
            assertFalse(message.contains("uk_user_email"), "Sanitized error must not disclose constraint names");
        }
    }

    // =========================================================================
    // 4. SENSITIVE FIELD EXCLUSION IN API SERIALIZATION
    // =========================================================================

    @Test
    @DisplayName("API Serialization: Sensitive fields (password, faceImage) are strictly excluded from JSON representations")
    public void testSensitiveFieldsExcludedFromJson() throws Exception {
        Employee emp = new Employee();
        emp.setName("Security Aware Employee");
        emp.setEmail("data.integrity1@example.com");
        emp.setEmployeeId("EMP_SENSITIVE_01");
        emp.setDepartment("Security");
        emp.setPassword("ConfidentialPasswordHash@123");
        emp.setFaceImage("data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////");

        String json = objectMapper.writeValueAsString(emp);

        assertFalse(json.contains("ConfidentialPasswordHash@123"), "Password must never be serialized into JSON output");
        assertFalse(json.contains("/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////"), "Face image biometric data must never be serialized into JSON output");
    }

    // =========================================================================
    // 5. SQL INJECTION IMMUNITY
    // =========================================================================

    @Test
    @DisplayName("SQL Injection: Malicious SQL payloads are treated as literal strings and safely handled")
    public void testSqlInjectionPayloadsTreatedAsLiterals() {
        String sqlInjectionEmail = "' OR '1'='1' -- ";
        assertTrue(userRepository.findByEmail(sqlInjectionEmail).isEmpty());

        String sqlInjectionEmpId = "EMP'; DROP TABLE employees; --";
        assertTrue(employeeRepository.findByEmployeeId(sqlInjectionEmpId).isEmpty());
    }
}
