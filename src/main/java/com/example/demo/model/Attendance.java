package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "total_hours")
    private Double totalHours = 0.0;

    @Column(name = "status")
    private String status = "PRESENT"; // PRESENT, ABSENT, LATE, HALF_DAY

    @Column(name = "work_mode")
    private String workMode; // OFFICE, WORK_FROM_HOME, ON_SITE

    @Column(name = "effort_score")
    private Double effortScore; // Calculated effort score for the day

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (attendanceDate == null) {
            attendanceDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (checkInTime != null && checkOutTime != null) {
            long minutes = ChronoUnit.MINUTES.between(checkInTime, checkOutTime);
            totalHours = minutes / 60.0;
        }
    }

    // Constructors
    public Attendance() {
    }

    public Attendance(Employee employee, LocalDate attendanceDate) {
        this.employee = employee;
        this.attendanceDate = attendanceDate;
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

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public LocalDateTime getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(LocalDateTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public LocalDateTime getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(LocalDateTime checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public Double getTotalHours() {
        return totalHours;
    }

    public void setTotalHours(Double totalHours) {
        this.totalHours = totalHours;
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

    public String getWorkMode() {
        return workMode;
    }

    public void setWorkMode(String workMode) {
        this.workMode = workMode;
    }

    public Double getEffortScore() {
        return effortScore;
    }

    public void setEffortScore(Double effortScore) {
        this.effortScore = effortScore;
    }
}
