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

    private static final double BASE_SCORE = 100.0;
    private static final double LATE_PENALTY = 5.0; // Per 15 minutes late
    private static final double EARLY_DEPARTURE_PENALTY = 10.0; // Per 30 minutes early
    private static final double CONSISTENCY_BONUS = 2.0; // Bonus for consistent performance

    public double calculateEffortScore(Employee employee, Attendance attendance) {
        double score = BASE_SCORE;

        // Check for late arrival
        if (employee.getScheduledStartTime() != null && attendance.getCheckInTime() != null) {
            LocalTime scheduledTime = LocalTime.parse(employee.getScheduledStartTime());
            LocalTime actualTime = attendance.getCheckInTime().toLocalTime();
            
            if (actualTime.isAfter(scheduledTime)) {
                long minutesLate = java.time.temporal.ChronoUnit.MINUTES.between(scheduledTime, actualTime);
                
                if (minutesLate <= 15) {
                    // Minor delay - apply grace
                    score -= (minutesLate / 15.0) * 2.0; // Reduced penalty
                } else {
                    // Significant delay
                    score -= (minutesLate / 15.0) * LATE_PENALTY;
                }
            } else {
                // On time or early - small bonus
                score += 1.0;
            }
        }

        // Check for early departure
        if (employee.getScheduledEndTime() != null && attendance.getCheckOutTime() != null) {
            LocalTime scheduledEndTime = LocalTime.parse(employee.getScheduledEndTime());
            LocalTime actualEndTime = attendance.getCheckOutTime().toLocalTime();
            
            if (actualEndTime.isBefore(scheduledEndTime)) {
                long minutesEarly = java.time.temporal.ChronoUnit.MINUTES.between(actualEndTime, scheduledEndTime);
                score -= (minutesEarly / 30.0) * EARLY_DEPARTURE_PENALTY;
            }
        }

        // Consistency bonus
        if (employee.getConsistentDays() != null && employee.getConsistentDays() >= 5) {
            score += CONSISTENCY_BONUS * Math.min(employee.getConsistentDays() / 5, 5); // Max 5 bonuses
        }

        // Ensure score is between 0 and 120
        return Math.max(0, Math.min(120, score));
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

