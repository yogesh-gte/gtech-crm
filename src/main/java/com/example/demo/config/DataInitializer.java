package com.example.demo.config;

import com.example.demo.model.Department;
import com.example.demo.model.User;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Override
    public void run(String... args) throws Exception {
        if (departmentRepository.count() == 0) {
            List<Department> departments = Arrays.asList(
                new Department("Engineering", "ENG"),
                new Department("Sales", "SALES"),
                new Department("HR", "HR"),
                new Department("Support", "SUPPORT"),
                new Department("Marketing", "MKT")
            );
            departmentRepository.saveAll(departments);
            System.out.println("Default departments created: Engineering, Sales, HR, Support, Marketing");
        }
        if (userRepository.count() == 0) {
            List<User> users = Arrays.asList(
                new User("admin@logsphere.com", "admin123", "Admin User", "ADMIN"),
                new User("hr@logsphere.com", "hr123", "HR User", "HR"),
                new User("manager@logsphere.com", "manager123", "Manager User", "MANAGER"),
                new User("hr1@logsphere.com", "hr123", "Hr1", "HR"),
                new User("manager1@logsphere.com", "admin123", "M1", "MANAGER"),
                new User("hr2@logsphere.com", "hr123", "Hr2", "HR"),
                new User("manager2@logsphere.com", "manager123", "M2", "MANAGER"),
                new User("admin1@logsphere.com", "admin123", "admin1", "ADMIN")
            );
            
            userRepository.saveAll(users);
            System.out.println("Default credentials synchronized with screenshot: 8 users created.");
        } else {
            System.out.println("Users already exist. Skipping synchronization.");
        }
    }
}
