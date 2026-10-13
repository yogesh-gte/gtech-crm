package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "issues")
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_key", unique = true, length = 30)
    private String issueKey; // e.g., PROJ-101

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "issue_type", nullable = false)
    private String issueType; // BUG, STORY, TASK, EPIC, SUBTASK

    @Column(name = "priority")
    private String priority = "MEDIUM"; // BLOCKER, CRITICAL, MAJOR, MINOR, TRIVIAL

    @Column(name = "severity")
    private String severity; // For bugs: CRITICAL, MAJOR, MINOR, COSMETIC

    @Column(name = "steps_to_reproduce", columnDefinition = "TEXT")
    private String stepsToReproduce; // For bugs

    @Column(name = "environment")
    private String environment; // For bugs - e.g., Chrome, Windows 10

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Employee assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private Employee reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_issue_id")
    private Issue parentIssue; // For SUBTASK - links to parent Epic/Story

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;

    @Column(name = "status")
    private String status = "OPEN"; // TRIAGE, OPEN, IN_PROGRESS, IN_REVIEW, TESTING, DONE, CLOSED

    @Column(name = "resolution")
    private String resolution; // For bugs when closed: DONE, WONTFIX, DUPLICATE, CANNOT_REPRODUCE

    @Column(name = "progress_percentage")
    private Integer progressPercentage = 0;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IssueComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "issue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IssueAttachment> attachments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if ("DONE".equals(status) || "CLOSED".equals(status)) {
            if (completedDate == null) completedDate = LocalDate.now();
            progressPercentage = 100;
        }
    }

    public Issue() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIssueKey() { return issueKey; }
    public void setIssueKey(String issueKey) { this.issueKey = issueKey; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getStepsToReproduce() { return stepsToReproduce; }
    public void setStepsToReproduce(String stepsToReproduce) { this.stepsToReproduce = stepsToReproduce; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public Employee getAssignee() { return assignee; }
    public void setAssignee(Employee assignee) { this.assignee = assignee; }

    public Employee getReporter() { return reporter; }
    public void setReporter(Employee reporter) { this.reporter = reporter; }

    public Issue getParentIssue() { return parentIssue; }
    public void setParentIssue(Issue parentIssue) { this.parentIssue = parentIssue; }

    public Sprint getSprint() { return sprint; }
    public void setSprint(Sprint sprint) { this.sprint = sprint; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public Integer getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Integer progressPercentage) { this.progressPercentage = progressPercentage; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDate getCompletedDate() { return completedDate; }
    public void setCompletedDate(LocalDate completedDate) { this.completedDate = completedDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<IssueComment> getComments() { return comments; }
    public void setComments(List<IssueComment> comments) { this.comments = comments; }

    public List<IssueAttachment> getAttachments() { return attachments; }
    public void setAttachments(List<IssueAttachment> attachments) { this.attachments = attachments; }
}
