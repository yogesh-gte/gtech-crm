package com.example.demo.service;

import com.example.demo.model.Attendance;
import com.example.demo.model.Employee;
import com.example.demo.model.Meeting;
import com.example.demo.model.Visitor;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.MeetingRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VisitorRepository;
import com.example.demo.service.EffortScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class ScheduledTaskService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private EffortScoreService effortScoreService;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private VisitorRepository visitorRepository;

    @Autowired
    private com.example.demo.repository.IdleIncidentRepository idleIncidentRepository;

    @Autowired
    private com.example.demo.repository.TaskRepository taskRepository;

    @Autowired
    private com.example.demo.repository.SprintRepository sprintRepository;

    @Autowired
    private com.example.demo.repository.IssueRepository issueRepository;

    @Autowired
    private com.example.demo.repository.NotificationRepository notificationRepository;

    // Send 5-minute reminder before clock-in time - runs every minute during work
    // hours
    @Scheduled(cron = "0 * 6-10 * * MON-FRI") // Every minute from 6 AM to 10 AM, Mon-Fri
    public void sendClockInReminders() {
        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        List<Employee> allEmployees = employeeRepository.findAll();

        for (Employee employee : allEmployees) {
            // Use default 9 AM if scheduled start time is not set
            LocalTime scheduledStartTime;
            if (employee.getScheduledStartTime() == null || employee.getScheduledStartTime().isEmpty()) {
                scheduledStartTime = LocalTime.of(9, 0); // Default 9 AM
                // Set default for future use
                employee.setScheduledStartTime("09:00");
                employeeRepository.save(employee);
            } else {
                scheduledStartTime = LocalTime.parse(employee.getScheduledStartTime());
            }

            // Skip if already checked in today
            if ("IN".equals(employee.getStatus()) &&
                    employee.getCurrentCheckIn() != null &&
                    employee.getCurrentCheckIn().toLocalDate().equals(today)) {
                continue;
            }

            // Skip if reminder already sent today
            if (Boolean.TRUE.equals(employee.getClockInReminderSent())) {
                // Check if it's a new day
                if (employee.getCurrentCheckIn() != null &&
                        !employee.getCurrentCheckIn().toLocalDate().equals(today)) {
                    employee.setClockInReminderSent(false);
                } else {
                    continue;
                }
            }

            LocalTime reminderTime = scheduledStartTime.minusMinutes(5);

            // Check if current time is within 1 minute of reminder time (5 minutes before
            // scheduled time)
            // For example: if scheduled time is 9:00 AM, reminder time is 8:55 AM
            // Check if current time is between 8:54:30 and 8:55:30
            if (currentTime.isAfter(reminderTime.minusSeconds(30)) &&
                    currentTime.isBefore(reminderTime.plusSeconds(30))) {
                // Send reminder
                System.out.println("Triggering clock-in reminder for: " + employee.getName() +
                        " at " + currentTime + " (reminder time: " + reminderTime + ", scheduled: " + scheduledStartTime
                        + ")");
                emailService.sendClockInReminder(
                        employee.getEmail(),
                        employee.getName(),
                        scheduledStartTime.toString());

                employee.setClockInReminderSent(true);
                employeeRepository.save(employee);
            }
        }
    }

    // Send 5-minute reminder before clock-out time - runs every minute during
    // afternoon hours
    @Scheduled(cron = "0 * 14-20 * * MON-FRI") // Every minute from 2 PM to 8 PM, Mon-Fri
    public void sendClockOutReminders() {
        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        List<Employee> clockedInEmployees = employeeRepository.findByStatus("IN");

        for (Employee employee : clockedInEmployees) {
            // Use default 6 PM if scheduled end time is not set
            LocalTime scheduledEndTime;
            if (employee.getScheduledEndTime() == null || employee.getScheduledEndTime().isEmpty()) {
                scheduledEndTime = LocalTime.of(18, 0); // Default 6 PM
                // Set default for future use
                employee.setScheduledEndTime("18:00");
                employeeRepository.save(employee);
            } else {
                scheduledEndTime = LocalTime.parse(employee.getScheduledEndTime());
            }

            // Skip if not checked in today
            if (employee.getCurrentCheckIn() == null ||
                    !employee.getCurrentCheckIn().toLocalDate().equals(today)) {
                continue;
            }

            // Skip if reminder already sent today
            if (Boolean.TRUE.equals(employee.getClockOutReminderSent())) {
                continue;
            }

            LocalTime reminderTime = scheduledEndTime.minusMinutes(5);

            // Check if current time is within 1 minute of reminder time (5 minutes before
            // scheduled time)
            // For example: if scheduled time is 6:00 PM, reminder time is 5:55 PM
            // Check if current time is between 5:54:30 and 5:55:30
            if (currentTime.isAfter(reminderTime.minusSeconds(30)) &&
                    currentTime.isBefore(reminderTime.plusSeconds(30))) {
                // Send reminder
                System.out.println("Triggering clock-out reminder for: " + employee.getName() +
                        " at " + currentTime + " (reminder time: " + reminderTime + ", scheduled: " + scheduledEndTime
                        + ")");
                emailService.sendClockOutReminder(
                        employee.getEmail(),
                        employee.getName(),
                        scheduledEndTime.toString());

                employee.setClockOutReminderSent(true);
                employeeRepository.save(employee);
            }
        }
    }

    // Check for late arrivals - runs every 15 minutes during work hours (8 AM to 6
    // PM)
    @Scheduled(cron = "0 */15 8-18 * * MON-FRI") // Every 15 minutes from 8 AM to 6 PM, Mon-Fri
    public void checkLateArrivals() {
        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        List<Employee> allEmployees = employeeRepository.findAll();

        for (Employee employee : allEmployees) {
            // Skip if employee doesn't have scheduled start time
            if (employee.getScheduledStartTime() == null || employee.getScheduledStartTime().isEmpty()) {
                continue;
            }

            // Skip if alert already sent today
            if (Boolean.TRUE.equals(employee.getLateAlertSent())) {
                // Check if it's a new day - reset flag if needed
                if (employee.getCurrentCheckIn() != null &&
                        !employee.getCurrentCheckIn().toLocalDate().equals(today)) {
                    employee.setLateAlertSent(false);
                } else {
                    continue;
                }
            }

            LocalTime scheduledStartTime = LocalTime.parse(employee.getScheduledStartTime());

            // Check if current time is past scheduled start time (with 5 minute grace
            // period)
            if (currentTime.isAfter(scheduledStartTime.plusMinutes(5))) {
                // Check if employee is checked in
                if ("IN".equals(employee.getStatus()) && employee.getCurrentCheckIn() != null) {
                    // Employee checked in late
                    LocalTime actualCheckInTime = employee.getCurrentCheckIn().toLocalTime();

                    if (actualCheckInTime.isAfter(scheduledStartTime)) {
                        // Mark late arrival (email sending removed - only tracking for records)
                        employee.setLateAlertSent(true);
                        employeeRepository.save(employee);

                        // Update attendance status to LATE
                        List<Attendance> attendanceOpt = attendanceRepository
                                .findByEmployeeAndAttendanceDate(employee, today);
                        if (!attendanceOpt.isEmpty()) {
                            Attendance attendance = attendanceOpt.get(0);
                            attendance.setStatus("LATE");
                            attendanceRepository.save(attendance);
                        }
                    }
                } else if ("OUT".equals(employee.getStatus())) {
                    // Employee hasn't checked in yet and it's past their scheduled time
                    // Check if they're more than 15 minutes late (to avoid false alarms)
                    if (currentTime.isAfter(scheduledStartTime.plusMinutes(15))) {
                        // Mark late arrival (email sending removed - only tracking for records)
                        employee.setLateAlertSent(true);
                        employeeRepository.save(employee);
                    }
                }
            }
        }
    }

    // Check for early departures - runs every 15 minutes during work hours (2 PM to
    // 6 PM)
    @Scheduled(cron = "0 */15 14-18 * * MON-FRI") // Every 15 minutes from 2 PM to 6 PM, Mon-Fri
    public void checkEarlyDepartures() {
        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        List<Employee> clockedInEmployees = employeeRepository.findByStatus("IN");

        for (Employee employee : clockedInEmployees) {
            // Use default 6 PM if scheduled end time is not set
            LocalTime scheduledEndTime;
            if (employee.getScheduledEndTime() == null || employee.getScheduledEndTime().isEmpty()) {
                scheduledEndTime = LocalTime.of(18, 0); // Default 6 PM
                // Set default for future use
                employee.setScheduledEndTime("18:00");
                employeeRepository.save(employee);
            } else {
                scheduledEndTime = LocalTime.parse(employee.getScheduledEndTime());
            }

            // Skip if alert already sent today
            if (Boolean.TRUE.equals(employee.getEarlyAlertSent())) {
                // Check if it's a new day - reset flag if needed
                if (employee.getCurrentCheckIn() != null &&
                        !employee.getCurrentCheckIn().toLocalDate().equals(today)) {
                    employee.setEarlyAlertSent(false);
                } else {
                    continue;
                }
            }

            // Check if current time is before scheduled end time (with 5 minute buffer)
            // Only check if we're within 2 hours of scheduled end time
            if (currentTime.isBefore(scheduledEndTime.minusMinutes(5)) &&
                    currentTime.isAfter(scheduledEndTime.minusHours(2))) {
                // This is a pre-check - employee is still clocked in but approaching end time
                // We'll check again when they clock out
                continue;
            }
        }

        // Check employees who have already clocked out early
        List<Employee> allEmployees = employeeRepository.findAll();
        for (Employee employee : allEmployees) {
            if (employee.getScheduledEndTime() == null || employee.getScheduledEndTime().isEmpty()) {
                continue;
            }

            if (Boolean.TRUE.equals(employee.getEarlyAlertSent())) {
                if (employee.getCurrentCheckOut() != null &&
                        !employee.getCurrentCheckOut().toLocalDate().equals(today)) {
                    employee.setEarlyAlertSent(false);
                } else {
                    continue;
                }
            }

            // Check if employee checked out today
            if (employee.getCurrentCheckOut() != null &&
                    employee.getCurrentCheckOut().toLocalDate().equals(today)) {
                LocalTime actualCheckOutTime = employee.getCurrentCheckOut().toLocalTime();
                LocalTime sixPM = LocalTime.of(18, 0); // 6:00 PM

                // Use default 6 PM if scheduled end time is not set
                LocalTime scheduledEndTime = sixPM;
                String scheduledTimeStr = "18:00";
                if (employee.getScheduledEndTime() != null && !employee.getScheduledEndTime().isEmpty()) {
                    scheduledEndTime = LocalTime.parse(employee.getScheduledEndTime());
                    scheduledTimeStr = employee.getScheduledEndTime();
                } else {
                    // Set default for future use
                    employee.setScheduledEndTime("18:00");
                    employeeRepository.save(employee);
                }

                // Check if employee clocked out before scheduled time
                if (actualCheckOutTime.isBefore(scheduledEndTime) &&
                        (employee.getEarlyAlertSent() == null || !employee.getEarlyAlertSent())) {
                    // Mark early clock-out (email sending removed - only tracking for records)
                    employee.setEarlyAlertSent(true);
                    employeeRepository.save(employee);
                }
            }
        }
    }

    // Check for missed clock outs every hour after 5 PM
    @Scheduled(cron = "0 0 17-23 * * *") // Every hour from 5 PM to 11 PM
    public void checkMissedClockOuts() {
        List<Employee> clockedInEmployees = employeeRepository.findByStatus("IN");
        LocalTime currentTime = LocalTime.now();

        // Only check if it's past scheduled end time
        for (Employee employee : clockedInEmployees) {
            if (employee.getScheduledEndTime() != null && !employee.getScheduledEndTime().isEmpty()) {
                LocalTime scheduledEndTime = LocalTime.parse(employee.getScheduledEndTime());

                // If current time is 30 minutes past scheduled end time and still clocked in
                if (currentTime.isAfter(scheduledEndTime.plusMinutes(30))) {
                    // Check if employee has been clocked in for more than 8 hours (likely forgot to
                    // clock out)
                    if (employee.getCurrentCheckIn() != null) {
                        long hoursSinceCheckIn = java.time.temporal.ChronoUnit.HOURS.between(
                                employee.getCurrentCheckIn(), LocalDateTime.now());

                        if (hoursSinceCheckIn > 8) {
                            // Send missed clock out alert to employee
                            emailService.sendMissedClockOutAlert(
                                    employee.getEmail(),
                                    employee.getName(),
                                    employee.getCurrentCheckIn().toLocalTime().toString());

                            // Send alert to admin
                            userRepository.findByEmail("admin@logsphere.com").ifPresent(admin -> {
                                emailService.sendMissedClockOutAlertToAdmin(
                                        admin.getEmail(),
                                        employee.getName(),
                                        employee.getEmployeeId(),
                                        employee.getCurrentCheckIn().toLocalTime().toString());
                            });
                        }
                    }
                }
            }
        }
    }

    // Reset alert flags daily at midnight
    @Scheduled(cron = "0 0 0 * * *") // Every day at midnight
    public void resetDailyFlags() {
        List<Employee> employees = employeeRepository.findAll();
        LocalDate today = LocalDate.now();

        for (Employee employee : employees) {
            // Reset flags only if it's a new day
            boolean shouldReset = false;

            if (employee.getCurrentCheckIn() != null) {
                shouldReset = !employee.getCurrentCheckIn().toLocalDate().equals(today);
            } else if (employee.getCurrentCheckOut() != null) {
                shouldReset = !employee.getCurrentCheckOut().toLocalDate().equals(today);
            } else {
                // No check-in/out today, reset flags
                shouldReset = true;
            }

            if (shouldReset) {
                employee.setLateAlertSent(false);
                employee.setEarlyAlertSent(false);
                employee.setIdleAlertSent(false);
                employee.setClockInReminderSent(false);
                employee.setClockOutReminderSent(false);
                employeeRepository.save(employee);
            }
        }
    }

    // Check for idle employees every minute during work hours
    @Scheduled(cron = "0 * 9-18 * * MON-FRI") // Every minute from 9 AM to 6 PM, Mon-FRI
    public void checkIdleEmployees() {
        List<Employee> clockedInEmployees = employeeRepository.findByStatus("IN");
        LocalDateTime now = LocalDateTime.now();

        for (Employee employee : clockedInEmployees) {
            LocalDateTime lastActivity = employee.getLastActivityTime();

            // If no activity time set, use check-in time
            if (lastActivity == null && employee.getCurrentCheckIn() != null) {
                lastActivity = employee.getCurrentCheckIn();
            }

            if (lastActivity != null) {
                long minutesIdle = java.time.temporal.ChronoUnit.MINUTES.between(lastActivity, now);

                // Alert if idle for more than 1 minute
                if (minutesIdle >= 1) {
                    // Check if there's already an active incident
                    Optional<com.example.demo.model.IdleIncident> existingIncident = idleIncidentRepository
                            .findActiveIncidentByEmployee(employee);

                    if (existingIncident.isEmpty() || !Boolean.TRUE.equals(existingIncident.get().getAlertSent())) {
                        // Create or update idle incident
                        com.example.demo.model.IdleIncident incident;
                        if (existingIncident.isPresent()) {
                            incident = existingIncident.get();
                        } else {
                            incident = new com.example.demo.model.IdleIncident(employee, lastActivity);
                        }

                        incident.setIdleDurationMinutes(minutesIdle);
                        incident.setStatus("ACTIVE");
                        incident = idleIncidentRepository.save(incident);

                        // Mark alert as sent (for dashboard notification tracking)
                        // Email sending removed - notifications now only shown in dashboard
                        if (!Boolean.TRUE.equals(incident.getAlertSent())) {
                            incident.setAlertSent(true);
                            idleIncidentRepository.save(incident);

                            employee.setIdleAlertSent(true);
                            employeeRepository.save(employee);

                            System.out.println("Idle incident logged for: " + employee.getName() +
                                    " - Idle for " + minutesIdle + " minutes (notification shown in dashboard)");
                        }
                    } else {
                        // Update existing incident duration
                        com.example.demo.model.IdleIncident incident = existingIncident.get();
                        incident.setIdleDurationMinutes(minutesIdle);
                        idleIncidentRepository.save(incident);
                    }
                }
            }
        }
    }

    // Calculate effort scores daily at end of day
    @Scheduled(cron = "0 0 18 * * *") // Every day at 6 PM
    public void calculateDailyEffortScores() {
        LocalDate today = LocalDate.now();
        List<Employee> employees = employeeRepository.findAll();

        for (Employee employee : employees) {
            List<Attendance> attendanceOpt = attendanceRepository.findByEmployeeAndAttendanceDate(employee, today);

            if (!attendanceOpt.isEmpty()) {
                Attendance attendance = attendanceOpt.get(0);

                // Calculate effort score
                double score = effortScoreService.calculateEffortScore(employee, attendance);
                attendance.setEffortScore(score);
                attendanceRepository.save(attendance);

                // Update employee's overall effort score (average of last 7 days)
                updateEmployeeEffortScore(employee);

                // Update consistency days
                effortScoreService.updateConsistencyDays(employee);
                employeeRepository.save(employee);
            }
        }
    }

    private void updateEmployeeEffortScore(Employee employee) {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(7);

        List<Attendance> recentAttendance = attendanceRepository.findByEmployeeAndDateRange(
                employee, sevenDaysAgo, today);

        if (!recentAttendance.isEmpty()) {
            double averageScore = recentAttendance.stream()
                    .filter(a -> a.getEffortScore() != null)
                    .mapToDouble(Attendance::getEffortScore)
                    .average()
                    .orElse(100.0);

            employee.setEffortScore(averageScore);
        }
    }

    // Send meeting reminders - runs every 5 minutes
    @Scheduled(cron = "0 */5 * * * *") // Every 5 minutes
    public void sendMeetingReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Meeting> upcomingMeetings = meetingRepository.findUpcomingMeetings(now);

        for (Meeting meeting : upcomingMeetings) {
            if (meeting.getStartTime() == null || meeting.getOrganizer() == null) {
                continue;
            }

            LocalDateTime startTime = meeting.getStartTime();
            long minutesUntilMeeting = java.time.temporal.ChronoUnit.MINUTES.between(now, startTime);

            // Send 1 hour reminder (60 minutes before)
            if (minutesUntilMeeting <= 60 && minutesUntilMeeting > 55 &&
                    (meeting.getReminder1HourSent() == null || !meeting.getReminder1HourSent())) {
                sendOneHourReminder(meeting);
                meeting.setReminder1HourSent(true);
                meetingRepository.save(meeting);
            }

            // Send 15 minutes reminder
            if (minutesUntilMeeting <= 15 && minutesUntilMeeting > 10 &&
                    (meeting.getReminder15MinSent() == null || !meeting.getReminder15MinSent())) {
                sendFifteenMinuteReminder(meeting);
                meeting.setReminder15MinSent(true);
                meetingRepository.save(meeting);
            }
        }
    }

    private void sendOneHourReminder(Meeting meeting) {
        String timeUntil = "1 hour";
        String organizerName = meeting.getOrganizer().getName();
        String organizerEmail = meeting.getOrganizer().getEmail();
        String visitorName = meeting.getVisitorName();
        String location = meeting.getLocation() != null ? meeting.getLocation() : "TBD";

        // Send reminder to organizer
        emailService.sendMeetingReminder(
                organizerEmail,
                organizerName,
                meeting.getTitle(),
                organizerName,
                visitorName,
                location,
                meeting.getStartTime(),
                meeting.getEndTime(),
                timeUntil);

        // Send reminder to visitor if applicable
        if (meeting.getVisitorId() != null) {
            Visitor visitor = visitorRepository.findById(meeting.getVisitorId()).orElse(null);
            if (visitor != null && visitor.getEmail() != null && !visitor.getEmail().isEmpty()) {
                emailService.sendMeetingReminder(
                        visitor.getEmail(),
                        visitor.getName(),
                        meeting.getTitle(),
                        organizerName,
                        visitorName,
                        location,
                        meeting.getStartTime(),
                        meeting.getEndTime(),
                        timeUntil);
            }
        }
    }

    // Overdue task reminders - daily at 9 AM
    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void sendOverdueTaskReminders() {
        LocalDate today = LocalDate.now();
        java.util.List<com.example.demo.model.Task> overdue = taskRepository.findOverdueTasks(today);
        java.util.Map<com.example.demo.model.Employee, java.util.List<com.example.demo.model.Task>> byEmployee = overdue.stream()
                .filter(t -> t.getEmployee() != null)
                .collect(java.util.stream.Collectors.groupingBy(com.example.demo.model.Task::getEmployee));
        for (java.util.Map.Entry<com.example.demo.model.Employee, java.util.List<com.example.demo.model.Task>> e : byEmployee.entrySet()) {
            com.example.demo.model.Employee emp = e.getKey();
            if (emp.getEmail() != null && !emp.getEmail().isEmpty()) {
                java.util.List<com.example.demo.model.Task> tasks = e.getValue();
                String titles = tasks.stream().map(com.example.demo.model.Task::getTitle).limit(5)
                        .collect(java.util.stream.Collectors.joining("\n- ", "- ", ""));
                if (tasks.size() > 5) titles += "\n... and " + (tasks.size() - 5) + " more";
                emailService.sendOverdueTaskReminder(emp.getEmail(), emp.getName(), tasks.size(), titles);
                for (com.example.demo.model.Task t : tasks) {
                    com.example.demo.model.Notification n = new com.example.demo.model.Notification(emp,
                            "Overdue: " + t.getTitle() + " (due " + (t.getDueDate() != null ? t.getDueDate() : "N/A") + ")");
                    notificationRepository.save(n);
                }
            }
        }
    }

    // Sprint end reminders - daily at 9 AM for sprints ending tomorrow
    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void sendSprintEndReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        java.util.List<com.example.demo.model.Sprint> ending = sprintRepository.findByStatusAndEndDate("ACTIVE", tomorrow);
        for (com.example.demo.model.Sprint sprint : ending) {
            java.util.List<com.example.demo.model.Issue> issues = issueRepository.findBySprint(sprint);
            java.util.Set<com.example.demo.model.Employee> assignees = new java.util.HashSet<>();
            for (com.example.demo.model.Issue i : issues) {
                if (i.getAssignee() != null && !"DONE".equals(i.getStatus()) && !"CLOSED".equals(i.getStatus())) {
                    assignees.add(i.getAssignee());
                }
            }
            String projectName = sprint.getProject() != null ? sprint.getProject().getName() : "Project";
            for (com.example.demo.model.Employee emp : assignees) {
                if (emp.getEmail() != null && !emp.getEmail().isEmpty()) {
                    long openCount = issues.stream().filter(i -> emp.equals(i.getAssignee()) && !"DONE".equals(i.getStatus()) && !"CLOSED".equals(i.getStatus())).count();
                    if (openCount > 0) {
                        emailService.sendSprintEndReminder(emp.getEmail(), emp.getName(), sprint.getName(), projectName, (int) openCount);
                        com.example.demo.model.Notification n = new com.example.demo.model.Notification(emp,
                                "Sprint \"" + sprint.getName() + "\" ends tomorrow. You have " + openCount + " open issue(s).");
                        notificationRepository.save(n);
                    }
                }
            }
        }
    }

    private void sendFifteenMinuteReminder(Meeting meeting) {
        String timeUntil = "15 minutes";
        String organizerName = meeting.getOrganizer().getName();
        String organizerEmail = meeting.getOrganizer().getEmail();
        String visitorName = meeting.getVisitorName();
        String location = meeting.getLocation() != null ? meeting.getLocation() : "TBD";

        // Send reminder to organizer
        emailService.sendMeetingReminder(
                organizerEmail,
                organizerName,
                meeting.getTitle(),
                organizerName,
                visitorName,
                location,
                meeting.getStartTime(),
                meeting.getEndTime(),
                timeUntil);

        // Send reminder to visitor if applicable
        if (meeting.getVisitorId() != null) {
            Visitor visitor = visitorRepository.findById(meeting.getVisitorId()).orElse(null);
            if (visitor != null && visitor.getEmail() != null && !visitor.getEmail().isEmpty()) {
                emailService.sendMeetingReminder(
                        visitor.getEmail(),
                        visitor.getName(),
                        meeting.getTitle(),
                        organizerName,
                        visitorName,
                        location,
                        meeting.getStartTime(),
                        meeting.getEndTime(),
                        timeUntil);
            }
        }
    }
}
