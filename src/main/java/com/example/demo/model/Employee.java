package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "employees", uniqueConstraints = @UniqueConstraint(name = "uk_employee_id", columnNames = {"employee_id"}))
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "employee_id", nullable = false, unique = true)
    private String employeeId;

    @Column(nullable = false)
    private String department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "is_team_lead")
    private Boolean isTeamLead = false;

    @Column(name = "designation")
    private String designation;

    @Column(name = "phone")
    private String phone;

    @Column(name = "status")
    private String status = "OUT"; // IN, OUT

    @Column(name = "current_check_in")
    private LocalDateTime currentCheckIn;

    @Column(name = "current_check_out")
    private LocalDateTime currentCheckOut;

    @Column(name = "total_hours_today")
    private Double totalHoursToday = 0.0;

    @Column(name = "scheduled_start_time")
    private String scheduledStartTime; // Format: "09:00"

    @Column(name = "scheduled_end_time")
    private String scheduledEndTime; // Format: "17:00"

    @Column(name = "late_alert_sent")
    private Boolean lateAlertSent = false;

    @Column(name = "early_alert_sent")
    private Boolean earlyAlertSent = false;

    @Column(name = "work_mode")
    private String workMode = "OFFICE"; // OFFICE, WORK_FROM_HOME, ON_SITE

    @Column(name = "qr_code_token", unique = true)
    private String qrCodeToken; // Unique token for QR code

    @Column(name = "last_activity_time")
    private LocalDateTime lastActivityTime;

    @Column(name = "idle_alert_sent")
    private Boolean idleAlertSent = false;

    @Column(name = "clockin_reminder_sent")
    private Boolean clockInReminderSent = false;

    @Column(name = "clockout_reminder_sent")
    private Boolean clockOutReminderSent = false;

    @Column(name = "effort_score")
    private Double effortScore = 100.0; // Default 100, can be adjusted

    @Column(name = "consistent_days")
    private Integer consistentDays = 0; // Days of consistent performance

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password", nullable = true)
    private String password;


    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "face_image", columnDefinition = "LONGTEXT")
    private String faceImage; // Base64 encoded face image for face recognition

    @Column(name = "is_active")
    private Boolean isActive = true; // Active vs Inactive

    @Column(name = "join_date")
    private LocalDate joinDate; // Date when employee joined

    @Column(name = "on_probation")
    private Boolean onProbation = false; // Employee on probation

    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Column(name = "leave_balance")
    private Integer leaveBalance = 0; // Leave balance

    @Column(name = "paid_leave")
    private Integer paidLeave = 0;

    @Column(name = "sick_leave")
    private Integer sickLeave = 0;

    @Column(name = "casual_leave")
    private Integer casualLeave = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employee_custom_leaves", joinColumns = @JoinColumn(name = "employee_id"))
    @MapKeyColumn(name = "leave_type")
    @Column(name = "days")
    private Map<String, Integer> customLeaves = new HashMap<>();

    @Column(name = "salary")
    private Double salary; // Base salary

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Constructors
    public Employee() {
    }

    public Employee(String name, String email, String employeeId, String department) {
        this.name = name;
        this.email = email;
        this.employeeId = employeeId;
        this.department = department;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Boolean getIsTeamLead() {
        return isTeamLead;
    }

    public void setIsTeamLead(Boolean isTeamLead) {
        this.isTeamLead = isTeamLead;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCurrentCheckIn() {
        return currentCheckIn;
    }

    public void setCurrentCheckIn(LocalDateTime currentCheckIn) {
        this.currentCheckIn = currentCheckIn;
    }

    public LocalDateTime getCurrentCheckOut() {
        return currentCheckOut;
    }

    public void setCurrentCheckOut(LocalDateTime currentCheckOut) {
        this.currentCheckOut = currentCheckOut;
    }

    public Double getTotalHoursToday() {
        return totalHoursToday;
    }

    public void setTotalHoursToday(Double totalHoursToday) {
        this.totalHoursToday = totalHoursToday;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getScheduledStartTime() {
        return scheduledStartTime;
    }

    public void setScheduledStartTime(String scheduledStartTime) {
        this.scheduledStartTime = scheduledStartTime;
    }

    public String getScheduledEndTime() {
        return scheduledEndTime;
    }

    public void setScheduledEndTime(String scheduledEndTime) {
        this.scheduledEndTime = scheduledEndTime;
    }

    public Boolean getLateAlertSent() {
        return lateAlertSent;
    }

    public void setLateAlertSent(Boolean lateAlertSent) {
        this.lateAlertSent = lateAlertSent;
    }

    public Boolean getEarlyAlertSent() {
        return earlyAlertSent;
    }

    public void setEarlyAlertSent(Boolean earlyAlertSent) {
        this.earlyAlertSent = earlyAlertSent;
    }

    public String getWorkMode() {
        return workMode;
    }

    public void setWorkMode(String workMode) {
        this.workMode = workMode;
    }

    public String getQrCodeToken() {
        return qrCodeToken;
    }

    public void setQrCodeToken(String qrCodeToken) {
        this.qrCodeToken = qrCodeToken;
    }

    public LocalDateTime getLastActivityTime() {
        return lastActivityTime;
    }

    public void setLastActivityTime(LocalDateTime lastActivityTime) {
        this.lastActivityTime = lastActivityTime;
    }

    public Boolean getIdleAlertSent() {
        return idleAlertSent;
    }

    public void setIdleAlertSent(Boolean idleAlertSent) {
        this.idleAlertSent = idleAlertSent;
    }

    public Double getEffortScore() {
        return effortScore;
    }

    public void setEffortScore(Double effortScore) {
        this.effortScore = effortScore;
    }

    public Integer getConsistentDays() {
        return consistentDays;
    }

    public void setConsistentDays(Integer consistentDays) {
        this.consistentDays = consistentDays;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFaceImage() {
        return faceImage;
    }

    public void setFaceImage(String faceImage) {
        this.faceImage = faceImage;
    }

    public Boolean getClockInReminderSent() {
        return clockInReminderSent;
    }

    public void setClockInReminderSent(Boolean clockInReminderSent) {
        this.clockInReminderSent = clockInReminderSent;
    }

    public Boolean getClockOutReminderSent() {
        return clockOutReminderSent;
    }

    public void setClockOutReminderSent(Boolean clockOutReminderSent) {
        this.clockOutReminderSent = clockOutReminderSent;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDate getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(LocalDate joinDate) {
        this.joinDate = joinDate;
    }

    public Boolean getOnProbation() {
        return onProbation;
    }

    public void setOnProbation(Boolean onProbation) {
        this.onProbation = onProbation;
    }

    public LocalDate getProbationEndDate() {
        return probationEndDate;
    }

    public void setProbationEndDate(LocalDate probationEndDate) {
        this.probationEndDate = probationEndDate;
    }

    public Integer getLeaveBalance() {
        return leaveBalance;
    }

    public void setLeaveBalance(Integer leaveBalance) {
        this.leaveBalance = leaveBalance;
    }

    public Integer getPaidLeave() {
        return paidLeave;
    }

    public void setPaidLeave(Integer paidLeave) {
        this.paidLeave = paidLeave;
    }

    public Integer getSickLeave() {
        return sickLeave;
    }

    public void setSickLeave(Integer sickLeave) {
        this.sickLeave = sickLeave;
    }

    public Integer getCasualLeave() {
        return casualLeave;
    }

    public void setCasualLeave(Integer casualLeave) {
        this.casualLeave = casualLeave;
    }

    public Map<String, Integer> getCustomLeaves() {
        return customLeaves;
    }

    public void setCustomLeaves(Map<String, Integer> customLeaves) {
        this.customLeaves = customLeaves;
    }

    public Double getSalary() {
        return salary;
    }

    public void setSalary(Double salary) {
        this.salary = salary;
    }

    @Override
    public String toString() {
        return "Employee{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", employeeId='" + employeeId + '\'' +
                ", department='" + department + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}

