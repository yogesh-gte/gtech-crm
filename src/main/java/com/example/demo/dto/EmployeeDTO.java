package com.example.demo.dto;

public class EmployeeDTO {
    private Long id;
    private String name;
    private String employeeId;
    private String email;
    private Double salary;

    public EmployeeDTO(Long id, String name, String employeeId, String email, Double salary) {
        this.id = id;
        this.name = name;
        this.employeeId = employeeId;
        this.email = email;
        this.salary = salary;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Double getSalary() {
        return salary;
    }

    public void setSalary(Double salary) {
        this.salary = salary;
    }
}
