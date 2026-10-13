package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idle_incidents")
public class IdleIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "idle_start_time", nullable = false)
    private LocalDateTime idleStartTime;

    @Column(name = "idle_duration_minutes")
    private Long idleDurationMinutes;

    @Column(name = "alert_sent")
    private Boolean alertSent = false;

    @Column(name = "status")
    private String status = "ACTIVE"; // ACTIVE, RESOLVED

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (idleStartTime == null) {
            idleStartTime = LocalDateTime.now();
        }
    }

    // Constructors
    public IdleIncident() {
    }

    public IdleIncident(Employee employee, LocalDateTime idleStartTime) {
        this.employee = employee;
        this.idleStartTime = idleStartTime;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LocalDateTime getIdleStartTime() {
        return idleStartTime;
    }

    public void setIdleStartTime(LocalDateTime idleStartTime) {
        this.idleStartTime = idleStartTime;
    }

    public Long getIdleDurationMinutes() {
        return idleDurationMinutes;
    }

    public void setIdleDurationMinutes(Long idleDurationMinutes) {
        this.idleDurationMinutes = idleDurationMinutes;
    }

    public Boolean getAlertSent() {
        return alertSent;
    }

    public void setAlertSent(Boolean alertSent) {
        this.alertSent = alertSent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

