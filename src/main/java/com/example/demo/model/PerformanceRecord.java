package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "performance_records", indexes = {
    @Index(name = "idx_perf_employee_period", columnList = "employee_id, period_type, period_start")
})
public class PerformanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "period_type", nullable = false)
    private String periodType; // WEEKLY, MONTHLY

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "composite_score")
    private Double compositeScore; // 0-100

    @Column(name = "attendance_score")
    private Double attendanceScore;

    @Column(name = "task_score")
    private Double taskScore;

    @Column(name = "goal_score")
    private Double goalScore;

    @Column(name = "issue_score")
    private Double issueScore;

    @Column(name = "feedback_score")
    private Double feedbackScore;

    @Column(name = "tasks_completed")
    private Integer tasksCompleted;

    @Column(name = "tasks_total")
    private Integer tasksTotal;

    @Column(name = "issues_completed")
    private Integer issuesCompleted;

    @Column(name = "issues_total")
    private Integer issuesTotal;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public PerformanceRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public Double getCompositeScore() { return compositeScore; }
    public void setCompositeScore(Double compositeScore) { this.compositeScore = compositeScore; }
    public Double getAttendanceScore() { return attendanceScore; }
    public void setAttendanceScore(Double attendanceScore) { this.attendanceScore = attendanceScore; }
    public Double getTaskScore() { return taskScore; }
    public void setTaskScore(Double taskScore) { this.taskScore = taskScore; }
    public Double getGoalScore() { return goalScore; }
    public void setGoalScore(Double goalScore) { this.goalScore = goalScore; }
    public Double getIssueScore() { return issueScore; }
    public void setIssueScore(Double issueScore) { this.issueScore = issueScore; }
    public Double getFeedbackScore() { return feedbackScore; }
    public void setFeedbackScore(Double feedbackScore) { this.feedbackScore = feedbackScore; }
    public Integer getTasksCompleted() { return tasksCompleted; }
    public void setTasksCompleted(Integer tasksCompleted) { this.tasksCompleted = tasksCompleted; }
    public Integer getTasksTotal() { return tasksTotal; }
    public void setTasksTotal(Integer tasksTotal) { this.tasksTotal = tasksTotal; }
    public Integer getIssuesCompleted() { return issuesCompleted; }
    public void setIssuesCompleted(Integer issuesCompleted) { this.issuesCompleted = issuesCompleted; }
    public Integer getIssuesTotal() { return issuesTotal; }
    public void setIssuesTotal(Integer issuesTotal) { this.issuesTotal = issuesTotal; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
