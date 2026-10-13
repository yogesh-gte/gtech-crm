package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "team_badges")
public class TeamBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private String badgeName; // STAR_PERFORMER, TEAM_PLAYER, INNOVATOR, etc.

    @Column(name = "badge_type")
    private String badgeType = "WEEKLY_STAR"; // WEEKLY_STAR, APPRECIATION, ACHIEVEMENT

    @Column(name = "appreciation_note", columnDefinition = "TEXT")
    private String appreciationNote;

    @ManyToOne
    @JoinColumn(name = "awarded_by")
    private User awardedBy; // Manager who awarded

    @Column(name = "awarded_date", nullable = false)
    private LocalDateTime awardedDate;

    @Column(name = "is_visible")
    private Boolean isVisible = true; // Visible to team

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (awardedDate == null) {
            awardedDate = LocalDateTime.now();
        }
    }

    // Constructors
    public TeamBadge() {
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

    public String getBadgeName() {
        return badgeName;
    }

    public void setBadgeName(String badgeName) {
        this.badgeName = badgeName;
    }

    public String getBadgeType() {
        return badgeType;
    }

    public void setBadgeType(String badgeType) {
        this.badgeType = badgeType;
    }

    public String getAppreciationNote() {
        return appreciationNote;
    }

    public void setAppreciationNote(String appreciationNote) {
        this.appreciationNote = appreciationNote;
    }

    public User getAwardedBy() {
        return awardedBy;
    }

    public void setAwardedBy(User awardedBy) {
        this.awardedBy = awardedBy;
    }

    public LocalDateTime getAwardedDate() {
        return awardedDate;
    }

    public void setAwardedDate(LocalDateTime awardedDate) {
        this.awardedDate = awardedDate;
    }

    public Boolean getIsVisible() {
        return isVisible;
    }

    public void setIsVisible(Boolean isVisible) {
        this.isVisible = isVisible;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

