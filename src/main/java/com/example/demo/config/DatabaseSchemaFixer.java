package com.example.demo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaFixer implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try {
            System.out.println("Running DatabaseSchemaFixer...");
            // Alter notifications table to allow NULL for employee_id
            jdbcTemplate.execute("ALTER TABLE notifications MODIFY employee_id BIGINT NULL");
            System.out.println("✅ SUCCESSFULLY altered notifications table: employee_id is now nullable.");
        } catch (Exception e) {
            // It might fail if the column is already nullable or DB is not MySQL (but logs confirmed MySQL dialect)
            System.out.println("⚠️ DatabaseSchemaFixer: " + e.getMessage());
        }
    }
}
