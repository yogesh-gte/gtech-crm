package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "meetings")
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne
    @JoinColumn(name = "organizer_id")
    private Employee organizer;

    @Column(name = "visitor_id")
    private Long visitorId;

    @Column(name = "visitor_name")
    private String visitorName;

    @ManyToOne
    @JoinColumn(name = "target_employee_id")
    private Employee targetEmployee;

    @ManyToOne
    @JoinColumn(name = "target_manager_id")
    private User targetManager;

    @Column(name = "is_all_employees")
    private Boolean isAllEmployees = false;

    @Column(name = "is_all_managers")
    private Boolean isAllManagers = false;

    @ManyToMany
    @JoinTable(
        name = "meeting_participants_employees",
        joinColumns = @JoinColumn(name = "meeting_id"),
        inverseJoinColumns = @JoinColumn(name = "employee_id")
    )
    private List<Employee> employeeParticipants;

    @ManyToMany
    @JoinTable(
        name = "meeting_participants_managers",
        joinColumns = @JoinColumn(name = "meeting_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> managerParticipants;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status")
    private String status = "SCHEDULED"; // SCHEDULED, ONGOING, COMPLETED, CANCELLED

    @Column(name = "location")
    private String location;

    @Column(name = "meeting_type")
    private String meetingType; // INTERNAL, EXTERNAL, CLIENT

    @ManyToMany
    @JoinTable(name = "meeting_attendees", joinColumns = @JoinColumn(name = "meeting_id"), inverseJoinColumns = @JoinColumn(name = "employee_id"))
    private List<Employee> attendees;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "reminder_1hour_sent")
    private Boolean reminder1HourSent = false;

    @Column(name = "reminder_15min_sent")
    private Boolean reminder15MinSent = false;

    @Column(name = "invite_everyone")
    private Boolean inviteEveryone = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (startTime != null && startTime.isBefore(LocalDateTime.now()) && endTime == null) {
            status = "ONGOING";
        }
    }

    // Constructors
    public Meeting() {
    }

    public Meeting(String title, LocalDateTime startTime) {
        this.title = title;
        this.startTime = startTime;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Employee getOrganizer() {
        return organizer;
    }

    public void setOrganizer(Employee organizer) {
        this.organizer = organizer;
    }

    public Long getVisitorId() {
        return visitorId;
    }

    public void setVisitorId(Long visitorId) {
        this.visitorId = visitorId;
    }

    public String getVisitorName() {
        return visitorName;
    }

    public void setVisitorName(String visitorName) {
        this.visitorName = visitorName;
    }

    public Employee getTargetEmployee() {
        return targetEmployee;
    }

    public void setTargetEmployee(Employee targetEmployee) {
        this.targetEmployee = targetEmployee;
    }

    public User getTargetManager() {
        return targetManager;
    }

    public void setTargetManager(User targetManager) {
        this.targetManager = targetManager;
    }

    public Boolean getIsAllEmployees() {
        return isAllEmployees;
    }

    public void setIsAllEmployees(Boolean isAllEmployees) {
        this.isAllEmployees = isAllEmployees;
    }

    public Boolean getIsAllManagers() {
        return isAllManagers;
    }

    public void setIsAllManagers(Boolean isAllManagers) {
        this.isAllManagers = isAllManagers;
    }

    public List<Employee> getEmployeeParticipants() {
        return employeeParticipants;
    }

    public void setEmployeeParticipants(List<Employee> employeeParticipants) {
        this.employeeParticipants = employeeParticipants;
    }

    public List<User> getManagerParticipants() {
        return managerParticipants;
    }

    public void setManagerParticipants(List<User> managerParticipants) {
        this.managerParticipants = managerParticipants;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getMeetingType() {
        return meetingType;
    }

    public void setMeetingType(String meetingType) {
        this.meetingType = meetingType;
    }

    public List<Employee> getAttendees() {
        return attendees;
    }

    public void setAttendees(List<Employee> attendees) {
        this.attendees = attendees;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getReminder1HourSent() {
        return reminder1HourSent;
    }

    public void setReminder1HourSent(Boolean reminder1HourSent) {
        this.reminder1HourSent = reminder1HourSent;
    }

    public Boolean getReminder15MinSent() {
        return reminder15MinSent;
    }

    public void setReminder15MinSent(Boolean reminder15MinSent) {
        this.reminder15MinSent = reminder15MinSent;
    }


    public Boolean getInviteEveryone() {
        return inviteEveryone;
    }

    public void setInviteEveryone(Boolean inviteEveryone) {
        this.inviteEveryone = inviteEveryone;
    }

}
