package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class PerformanceScoreService {

    private static final double WEIGHT_ATTENDANCE = 0.25;
    private static final double WEIGHT_TASKS = 0.25;
    private static final double WEIGHT_GOALS = 0.20;
    private static final double WEIGHT_ISSUES = 0.20;
    private static final double WEIGHT_FEEDBACK = 0.10;

    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private GoalRepository goalRepository;
    @Autowired
    private IssueRepository issueRepository;
    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private PerformanceRecordRepository performanceRecordRepository;

    public double calculateCompositeScore(Employee employee, LocalDate periodStart, LocalDate periodEnd) {
        double attScore = calculateAttendanceScore(employee, periodStart, periodEnd);
        double taskScore = calculateTaskScore(employee, periodStart, periodEnd);
        double goalScore = calculateGoalScore(employee);
        double issueScore = calculateIssueScore(employee, periodStart, periodEnd);
        double feedbackScore = calculateFeedbackScore(employee, periodStart, periodEnd);

        return attScore * WEIGHT_ATTENDANCE
                + taskScore * WEIGHT_TASKS
                + goalScore * WEIGHT_GOALS
                + issueScore * WEIGHT_ISSUES
                + feedbackScore * WEIGHT_FEEDBACK;
    }

    private double calculateAttendanceScore(Employee employee, LocalDate start, LocalDate end) {
        List<Attendance> records = attendanceRepository.findByEmployeeAndDateRange(employee, start, end);
        long workingDays = start.datesUntil(end.plusDays(1))
                .filter(d -> d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY)
                .count();
        if (workingDays == 0) return 100.0;
        long presentDays = records.stream()
                .filter(a -> "PRESENT".equals(a.getStatus()) || "LATE".equals(a.getStatus()))
                .count();
        double presenceRatio = Math.min(1.0, (double) presentDays / workingDays);
        double effortAvg = records.stream()
                .filter(a -> a.getEffortScore() != null)
                .mapToDouble(Attendance::getEffortScore)
                .average()
                .orElse(100.0);
        return presenceRatio * 50 + (Math.min(100, effortAvg) / 100.0) * 50;
    }

    private double calculateTaskScore(Employee employee, LocalDate start, LocalDate end) {
        List<Task> tasks = taskRepository.findByEmployeeAndAssignedDateBetween(employee, start, end);
        tasks = tasks.stream().filter(t -> !"CANCELLED".equals(t.getStatus())).toList();
        if (tasks.isEmpty()) return 100.0;
        long completed = tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        return 100.0 * completed / tasks.size();
    }

    private double calculateGoalScore(Employee employee) {
        List<Goal> goals = goalRepository.findByEmployee(employee);
        goals = goals.stream().filter(g -> !"COMPLETED".equals(g.getStatus())).toList();
        if (goals.isEmpty()) return 100.0;
        double avgProgress = goals.stream()
                .mapToInt(g -> g.getProgressPercentage() != null ? g.getProgressPercentage() : 0)
                .average()
                .orElse(0);
        return Math.min(100, avgProgress);
    }

    private double calculateIssueScore(Employee employee, LocalDate start, LocalDate end) {
        List<Issue> issues = issueRepository.findByAssignee(employee);
        if (issues.isEmpty()) return 100.0;
        long completed = issues.stream()
                .filter(i -> "DONE".equals(i.getStatus()) || "CLOSED".equals(i.getStatus()))
                .count();
        return 100.0 * completed / issues.size();
    }

    private double calculateFeedbackScore(Employee employee, LocalDate start, LocalDate end) {
        List<Feedback> feedbacks = feedbackRepository.findByEmployeeOrderByCreatedAtDesc(employee);
        long inPeriod = feedbacks.stream()
                .filter(f -> f.getCreatedAt() != null
                        && !f.getCreatedAt().toLocalDate().isBefore(start)
                        && !f.getCreatedAt().toLocalDate().isAfter(end))
                .count();
        return inPeriod > 0 ? 100.0 : 70.0;
    }

    public PerformanceRecord getOrCreateWeeklyRecord(Employee employee) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        List<PerformanceRecord> existing = performanceRecordRepository
                .findByEmployeeAndPeriodTypeOrderByPeriodStartDesc(employee, "WEEKLY");
        PerformanceRecord rec = existing.stream()
                .filter(r -> !r.getPeriodStart().isAfter(weekStart) && !r.getPeriodEnd().isBefore(weekStart))
                .findFirst()
                .orElse(null);

        if (rec == null) {
            rec = new PerformanceRecord();
            rec.setEmployee(employee);
            rec.setPeriodType("WEEKLY");
            rec.setPeriodStart(weekStart);
            rec.setPeriodEnd(weekEnd);

            double attScore = calculateAttendanceScore(employee, weekStart, weekEnd);
            double taskScore = calculateTaskScore(employee, weekStart, weekEnd);
            double goalScore = calculateGoalScore(employee);
            double issueScore = calculateIssueScore(employee, weekStart, weekEnd);
            double feedbackScore = calculateFeedbackScore(employee, weekStart, weekEnd);

            rec.setAttendanceScore(attScore);
            rec.setTaskScore(taskScore);
            rec.setGoalScore(goalScore);
            rec.setIssueScore(issueScore);
            rec.setFeedbackScore(feedbackScore);

            List<Task> tasks = taskRepository.findByEmployeeAndAssignedDateBetween(employee, weekStart, weekEnd);
            tasks = tasks.stream().filter(t -> !"CANCELLED".equals(t.getStatus())).toList();
            rec.setTasksTotal(tasks.size());
            rec.setTasksCompleted((int) tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count());

            List<Issue> issues = issueRepository.findByAssignee(employee);
            rec.setIssuesTotal(issues.size());
            rec.setIssuesCompleted((int) issues.stream()
                    .filter(i -> "DONE".equals(i.getStatus()) || "CLOSED".equals(i.getStatus()))
                    .count());

            double composite = attScore * WEIGHT_ATTENDANCE + taskScore * WEIGHT_TASKS
                    + goalScore * WEIGHT_GOALS + issueScore * WEIGHT_ISSUES + feedbackScore * WEIGHT_FEEDBACK;
            rec.setCompositeScore(Math.round(composite * 10) / 10.0);
            performanceRecordRepository.save(rec);
        }
        return rec;
    }

    public double getTodayTaskCompletionRate(Employee employee) {
        LocalDate today = LocalDate.now();
        List<Task> tasks = taskRepository.findTodayTasksByEmployee(employee, today);
        if (tasks.isEmpty()) return 100.0;
        long completed = tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        return 100.0 * completed / tasks.size();
    }

    public List<PerformanceRecord> getPerformanceHistory(Employee employee) {
        return performanceRecordRepository.findByEmployeeAndPeriodTypeOrderByPeriodStartDesc(employee, "WEEKLY")
                .stream().limit(12).toList();
    }
}
