package com.example.demo.service;

import com.example.demo.model.Attendance;
import com.example.demo.model.Employee;
import com.example.demo.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class EffortScoreService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private com.example.demo.repository.TaskRepository taskRepository;

    @Autowired
    private com.example.demo.repository.IssueRepository issueRepository;

    private static final double BASE_SCORE = 100.0;
    private static final double LATE_PENALTY = 5.0; // Per 15 minutes late
    private static final double EARLY_DEPARTURE_PENALTY = 10.0; // Per 30 minutes early
    private static final double CONSISTENCY_BONUS = 2.0; // Bonus for consistent performance

    public double calculateEffortScore(Employee employee, Attendance attendance) {
        // 1. Attendance & Punctuality Score (50% weight)
        double attendanceScore = BASE_SCORE;

        // Check for late arrival
        if (employee.getScheduledStartTime() != null && attendance != null && attendance.getCheckInTime() != null) {
            try {
                LocalTime scheduledTime = LocalTime.parse(employee.getScheduledStartTime());
                LocalTime actualTime = attendance.getCheckInTime().toLocalTime();
                
                if (actualTime.isAfter(scheduledTime)) {
                    long minutesLate = java.time.temporal.ChronoUnit.MINUTES.between(scheduledTime, actualTime);
                    if (minutesLate <= 15) {
                        attendanceScore -= (minutesLate / 15.0) * 2.0; // Minor delay grace
                    } else {
                        attendanceScore -= (minutesLate / 15.0) * LATE_PENALTY;
                    }
                } else {
                    attendanceScore += 2.0; // Punctuality bonus
                }
            } catch (Exception ignored) {}
        }

        // Check for early departure
        if (employee.getScheduledEndTime() != null && attendance != null && attendance.getCheckOutTime() != null) {
            try {
                LocalTime scheduledEndTime = LocalTime.parse(employee.getScheduledEndTime());
                LocalTime actualEndTime = attendance.getCheckOutTime().toLocalTime();
                
                if (actualEndTime.isBefore(scheduledEndTime)) {
                    long minutesEarly = java.time.temporal.ChronoUnit.MINUTES.between(actualEndTime, scheduledEndTime);
                    attendanceScore -= (minutesEarly / 30.0) * EARLY_DEPARTURE_PENALTY;
                }
            } catch (Exception ignored) {}
        }

        // Shift duration evaluation
        if (attendance != null && attendance.getTotalHours() != null) {
            if (attendance.getTotalHours() >= 8.0) {
                attendanceScore += 2.0;
            } else if (attendance.getTotalHours() < 4.0 && attendance.getCheckOutTime() != null) {
                attendanceScore -= 10.0;
            }
        }
        attendanceScore = Math.max(0, Math.min(100, attendanceScore));

        // 2. Employee Task Performance (40% weight)
        double performanceScore = BASE_SCORE;
        LocalDate targetDate = (attendance != null && attendance.getAttendanceDate() != null) 
                ? attendance.getAttendanceDate() 
                : LocalDate.now();

        List<com.example.demo.model.Task> tasks = taskRepository.findTodayTasksByEmployee(employee, targetDate);
        if (tasks != null && !tasks.isEmpty()) {
            long total = tasks.size();
            long completed = tasks.stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus())).count();
            long inProgress = tasks.stream().filter(t -> "IN_PROGRESS".equalsIgnoreCase(t.getStatus())).count();
            
            // Completion rate: completed = 100%, in-progress = 50%
            double completionRate = ((completed * 1.0) + (inProgress * 0.5)) / total * 100.0;
            performanceScore = completionRate;
        } else {
            // Check weekly tasks if none assigned today
            LocalDate startOfWeek = targetDate.minusDays(targetDate.getDayOfWeek().getValue() - 1);
            List<com.example.demo.model.Task> weeklyTasks = taskRepository.findByEmployeeAndAssignedDateBetween(employee, startOfWeek, targetDate);
            if (weeklyTasks != null && !weeklyTasks.isEmpty()) {
                long total = weeklyTasks.size();
                long completed = weeklyTasks.stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus())).count();
                double completionRate = ((double) completed / total) * 100.0;
                performanceScore = completionRate;
            } else {
                performanceScore = 100.0; // Neutral baseline if no tasks assigned
            }
        }
        performanceScore = Math.max(0, Math.min(100, performanceScore));

        // 3. Consistency Score (10% weight)
        double consistencyScore = 80.0;
        if (employee.getConsistentDays() != null && employee.getConsistentDays() > 0) {
            consistencyScore = Math.min(100.0, 80.0 + (employee.getConsistentDays() * 4.0));
        }

        // Composite Effort Score: 50% Attendance + 40% Performance + 10% Consistency
        double compositeEffort = (attendanceScore * 0.50) + (performanceScore * 0.40) + (consistencyScore * 0.10);
        return Math.round(Math.max(0.0, Math.min(100.0, compositeEffort)) * 10.0) / 10.0;
    }

    public void updateConsistencyDays(Employee employee) {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(7);
        
        List<Attendance> recentAttendance = attendanceRepository.findByEmployeeAndDateRange(
            employee, sevenDaysAgo, today);
        
        // Count consecutive present days
        int consistentDays = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate checkDate = today.minusDays(i);
            boolean present = recentAttendance.stream()
                .anyMatch(a -> a.getAttendanceDate().equals(checkDate) && 
                             "PRESENT".equals(a.getStatus()));
            if (present) {
                consistentDays++;
            } else {
                break;
            }
        }
        
        employee.setConsistentDays(consistentDays);
    }
}

