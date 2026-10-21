package com.example.demo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.sql.Connection;

/**
 * Local development schema helper.
 * Restricted to non-production environments to enforce database least-privilege
 * and avoid runtime DDL execution in production.
 */
@Component
@Profile("!prod")
public class DatabaseSchemaFixer implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Running DatabaseSchemaFixer...");

        // Alter notifications table to allow NULL for employee_id.
        // MODIFY is MySQL-only syntax; on other databases (e.g. H2 in dev-h2) Hibernate already
        // creates the column as nullable from the entity mapping, so there is nothing to fix.
        if (isMySql()) {
            try {
                jdbcTemplate.execute("ALTER TABLE notifications MODIFY employee_id BIGINT NULL");
                System.out.println("✅ SUCCESSFULLY altered notifications table: employee_id is now nullable.");
            } catch (Exception e) {
                System.out.println("⚠️ DatabaseSchemaFixer: " + e.getMessage());
            }
        }

        // Ensure employees.employee_id has unique constraint in dev/test databases
        try {
            jdbcTemplate.execute("DELETE FROM employees WHERE employee_id LIKE 'EMP_SEC_%' OR employee_id LIKE 'TX_TEST_%'");
            jdbcTemplate.execute("ALTER TABLE employees ADD CONSTRAINT uk_employee_id UNIQUE (employee_id)");
            System.out.println("✅ SUCCESSFULLY added uk_employee_id unique constraint to employees table.");
        } catch (Exception ignored) {
            // Constraint already exists (or duplicate data prevents it)
        }
    }

    private boolean isMySql() {
        try {
            String product = jdbcTemplate.execute((Connection con) -> con.getMetaData().getDatabaseProductName());
            return product != null && product.toLowerCase().contains("mysql");
        } catch (Exception e) {
            return false;
        }
    }
}
