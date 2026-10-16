package com.example.demo;

import com.example.demo.config.GlobalDatabaseExceptionHandler;
import com.example.demo.model.Employee;
import com.example.demo.model.Payroll;
import com.example.demo.model.User;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.PayrollRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DatabaseSecurityHardeningTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GlobalDatabaseExceptionHandler globalDatabaseExceptionHandler;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setUp() {
        payrollRepository.deleteAll();
        try {
            jdbcTemplate.execute("DELETE FROM employees WHERE employee_id LIKE 'EMP_SEC_%' OR employee_id LIKE 'TX_TEST_%' OR employee_id = 'EMP_SENSITIVE'");
        } catch (Exception ignored) {}
    }

    @org.junit.jupiter.api.AfterEach
    public void tearDown() {
        try {
            jdbcTemplate.execute("DELETE FROM employees WHERE employee_id LIKE 'EMP_SEC_%' OR employee_id LIKE 'TX_TEST_%' OR employee_id = 'EMP_SENSITIVE'");
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("SQL Injection Payload is Treated as Literal in Parameterized Repository Queries")
    public void testSqlInjectionPayloadsTreatedAsLiterals() {
        String sqlInjectionEmail = "' OR '1'='1' -- ";
        assertTrue(userRepository.findByEmail(sqlInjectionEmail).isEmpty(),
                "SQL injection in findByEmail must safely treat payload as literal and return empty");

        String sqlInjectionRole = "ADMIN' OR '1'='1";
        List<User> usersByRole = userRepository.findByRole(sqlInjectionRole);
        assertTrue(usersByRole.isEmpty(),
                "SQL injection in findByRole must safely treat payload as literal and return empty");

        String sqlInjectionEmpId = "EMP-001'; DROP TABLE employees; --";
        assertTrue(employeeRepository.findByEmployeeId(sqlInjectionEmpId).isEmpty(),
                "SQL injection in findByEmployeeId must safely treat payload as literal and return empty");
    }

    @Test
    @DisplayName("User Email Uniqueness Constraint is Enforced by Database")
    public void testUserEmailUniquenessConstraint() {
        String uniqueEmail = "dbsec_test_" + System.currentTimeMillis() + "@example.com";
        User user1 = new User(uniqueEmail, "hash123456", "User One", "EMPLOYEE");
        userRepository.saveAndFlush(user1);

        User user2 = new User(uniqueEmail, "hash654321", "User Two", "EMPLOYEE");
        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(user2);
        }, "Persisting duplicate email must throw DataIntegrityViolationException");
    }

    @Test
    @DisplayName("Employee ID Uniqueness Constraint is Enforced by Database")
    public void testEmployeeIdUniquenessConstraint() {
        String uniqueEmpId = "EMP_SEC_" + System.currentTimeMillis();
        Employee emp1 = new Employee();
        emp1.setEmployeeId(uniqueEmpId);
        emp1.setName("Emp One");
        emp1.setEmail("emp1_" + System.currentTimeMillis() + "@test.com");
        emp1.setDepartment("Engineering");
        emp1.setDesignation("Developer");
        emp1.setSalary(50000.0);
        emp1.setJoinDate(LocalDate.now());
        employeeRepository.saveAndFlush(emp1);

        Employee emp2 = new Employee();
        emp2.setEmployeeId(uniqueEmpId);
        emp2.setName("Emp Two");
        emp2.setEmail("emp2_" + System.currentTimeMillis() + "@test.com");
        emp2.setDepartment("Engineering");
        emp2.setDesignation("Developer");
        emp2.setSalary(55000.0);
        emp2.setJoinDate(LocalDate.now());

        assertThrows(DataIntegrityViolationException.class, () -> {
            employeeRepository.saveAndFlush(emp2);
        }, "Persisting duplicate employeeId must throw DataIntegrityViolationException");
    }

    @Test
    @DisplayName("Payroll Composite Uniqueness Constraint Prevents Duplicate Monthly Salary Generation")
    public void testPayrollCompositeUniquenessConstraint() {
        Employee emp = new Employee();
        emp.setEmployeeId("EMP_PAY_" + System.currentTimeMillis());
        emp.setName("Payroll Employee");
        emp.setEmail("pay_emp_" + System.currentTimeMillis() + "@test.com");
        emp.setDepartment("Engineering");
        emp.setDesignation("Developer");
        emp.setSalary(60000.0);
        emp.setJoinDate(LocalDate.now());
        emp = employeeRepository.saveAndFlush(emp);

        Payroll payroll1 = new Payroll();
        payroll1.setEmployee(emp);
        payroll1.setMonth(10);
        payroll1.setYear(2026);
        payroll1.setGrossSalary(60000.0);
        payroll1.setNetPay(55000.0);
        payrollRepository.saveAndFlush(payroll1);

        Payroll payroll2 = new Payroll();
        payroll2.setEmployee(emp);
        payroll2.setMonth(10);
        payroll2.setYear(2026);
        payroll2.setGrossSalary(60000.0);
        payroll2.setNetPay(55000.0);

        assertThrows(DataIntegrityViolationException.class, () -> {
            payrollRepository.saveAndFlush(payroll2);
        }, "Persisting duplicate payroll for same employee, month, and year must throw DataIntegrityViolationException");
    }

    @Test
    @DisplayName("Transaction Boundary Enforces Complete Rollback on Failure")
    public void testTransactionAtomicityAndRollback() {
        String testEmpId = "TX_TEST_" + System.currentTimeMillis();
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        try {
            Employee emp = new Employee();
            emp.setEmployeeId(testEmpId);
            emp.setName("Tx Test Employee");
            emp.setEmail("tx_" + System.currentTimeMillis() + "@test.com");
            emp.setDepartment("Engineering");
            emp.setDesignation("Developer");
            emp.setSalary(50000.0);
            emp.setJoinDate(LocalDate.now());
            employeeRepository.save(emp);

            // Simulate explicit rollback or failure
            transactionManager.rollback(status);
        } catch (Exception ex) {
            transactionManager.rollback(status);
        }

        assertTrue(employeeRepository.findByEmployeeId(testEmpId).isEmpty(),
                "Rolled back transaction must leave no orphaned records in database");
    }

    @Test
    @DisplayName("Biometric Face Data and Password Hashes are Excluded from JSON Serialization")
    public void testSensitiveFieldsExcludedFromSerialization() throws Exception {
        Employee employee = new Employee();
        employee.setEmployeeId("EMP_SENSITIVE");
        employee.setName("Sensitive User");
        employee.setFaceImage("data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD...");

        String employeeJson = objectMapper.writeValueAsString(employee);
        assertFalse(employeeJson.contains("data:image/jpeg;base64"),
                "Employee JSON serialization must not leak faceImage biometric data");

        User user = new User("user@test.com", "superSecretBcryptHash$2a$10...", "User", "ADMIN");
        user.setResetToken("token12345");
        String userJson = objectMapper.writeValueAsString(user);
        assertFalse(userJson.contains("superSecretBcryptHash"),
                "User JSON serialization must not leak password hash");
        assertFalse(userJson.contains("token12345"),
                "User JSON serialization must not leak password reset token");
    }

    @Test
    @DisplayName("Global Database Exception Handler Sanitizes Error Messages and Returns 409 Conflict for JSON")
    public void testGlobalDatabaseExceptionHandlerSanitizesResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/payroll/save");
        request.addHeader("Accept", "application/json");

        MockHttpServletResponse response = new MockHttpServletResponse();
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "Duplicate entry 'admin@logsphere.com' for key 'users.uk_email'",
                new java.sql.SQLException("Duplicate entry 'admin@logsphere.com' for key 'users.uk_email'"));

        Object result = globalDatabaseExceptionHandler.handleDataIntegrityViolation(ex, request, response);
        assertInstanceOf(ResponseEntity.class, result);

        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> responseEntity = (ResponseEntity<Map<String, Object>>) result;
        assertEquals(HttpStatus.CONFLICT, responseEntity.getStatusCode());

        Map<String, Object> body = responseEntity.getBody();
        assertNotNull(body);
        assertEquals("DataIntegrityConflict", body.get("error"));

        String clientMessage = (String) body.get("message");
        assertFalse(clientMessage.contains("users.uk_email"), "Client message must not leak table or constraint names");
        assertFalse(clientMessage.contains("admin@logsphere.com"), "Client message must not leak raw SQL parameters");
        assertTrue(clientMessage.contains("already exists"), "Client message must be sanitized and user-friendly");
    }

    @Test
    @DisplayName("Production Configuration Enforces Safe Schema Validation and Suppresses Sensitive Output")
    public void testProductionConfigurationProperties() throws Exception {
        Properties prodProps = new Properties();
        try (InputStream in = new ClassPathResource("application-prod.properties").getInputStream()) {
            prodProps.load(in);
        }

        assertEquals("validate", prodProps.getProperty("spring.jpa.hibernate.ddl-auto"),
                "Production must use ddl-auto=validate to prevent runtime DDL modifications");
        assertEquals("false", prodProps.getProperty("spring.jpa.show-sql"),
                "Production must disable show-sql to prevent SQL queries from appearing in logs");
        assertEquals("WARN", prodProps.getProperty("logging.level.org.hibernate.SQL"),
                "Production must suppress Hibernate SQL logging");
        assertEquals("WARN", prodProps.getProperty("logging.level.org.hibernate.orm.jdbc.bind"),
                "Production must suppress SQL bind parameter logging");
        assertEquals("never", prodProps.getProperty("server.error.include-stacktrace"),
                "Production must suppress stack traces in error responses");
        assertEquals("never", prodProps.getProperty("server.error.include-message"),
                "Production must suppress internal exception messages in error responses");

        // HikariCP connection pool settings
        assertTrue(prodProps.getProperty("spring.datasource.hikari.maximum-pool-size").contains("20"),
                "Production must define HikariCP maximum pool size");
        assertTrue(prodProps.getProperty("spring.datasource.hikari.minimum-idle").contains("5"),
                "Production must define HikariCP minimum idle connections");
    }
}
