package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import com.example.demo.service.PerformanceScoreService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Controller
@RequestMapping("/reports")
public class ReportController {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private VisitorRepository visitorRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PerformanceRecordRepository performanceRecordRepository;

    @Autowired
    private PerformanceScoreService performanceScoreService;

    @Autowired(required = false)
    private com.example.demo.service.AuthorizationService authorizationService;

    private boolean isStaffUser(HttpSession session) {
        if (session == null) return false;
        if (authorizationService != null) {
            return authorizationService.isStaff(session);
        }
        Object userObj = session.getAttribute("user");
        if (userObj instanceof com.example.demo.model.User user) {
            String role = user.getRole();
            return "ADMIN".equalsIgnoreCase(role) || "HR".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role);
        }
        return false;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String reportsDashboard(Model model, HttpSession session) {
        if (!isStaffUser(session)) {
            if (session != null && session.getAttribute("employee") != null) {
                return "redirect:/employee/dashboard";
            }
            return "redirect:/login";
        }
        
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate startOfMonth = today.withDayOfMonth(1);
        
        // Today's stats
        List<Attendance> todayAttendance = attendanceRepository.findByAttendanceDate(today);
        List<Visitor> todayVisitors = visitorRepository.findByCheckInTimeBetween(
            LocalDateTime.of(today, java.time.LocalTime.MIN),
            LocalDateTime.of(today, java.time.LocalTime.MAX)
        );
        
        // Weekly stats
        List<Attendance> weeklyAttendance = attendanceRepository.findByDateRange(startOfWeek, today);
        List<Visitor> weeklyVisitors = visitorRepository.findByCheckInTimeBetween(
            startOfWeek.atStartOfDay(),
            LocalDateTime.of(today, java.time.LocalTime.MAX)
        );
        
        // Monthly stats
        List<Attendance> monthlyAttendance = attendanceRepository.findByDateRange(startOfMonth, today);
        List<Visitor> monthlyVisitors = visitorRepository.findByCheckInTimeBetween(
            startOfMonth.atStartOfDay(),
            LocalDateTime.of(today, java.time.LocalTime.MAX)
        );
        
        model.addAttribute("todayAttendance", todayAttendance);
        model.addAttribute("todayVisitors", todayVisitors);
        model.addAttribute("weeklyAttendance", weeklyAttendance);
        model.addAttribute("weeklyVisitors", weeklyVisitors);
        model.addAttribute("monthlyAttendance", monthlyAttendance);
        model.addAttribute("monthlyVisitors", monthlyVisitors);
        
        // Calculate totals
        double todayTotalHours = todayAttendance.stream()
            .mapToDouble(a -> a.getTotalHours() != null ? a.getTotalHours() : 0.0)
            .sum();
        double weeklyTotalHours = weeklyAttendance.stream()
            .mapToDouble(a -> a.getTotalHours() != null ? a.getTotalHours() : 0.0)
            .sum();
        double monthlyTotalHours = monthlyAttendance.stream()
            .mapToDouble(a -> a.getTotalHours() != null ? a.getTotalHours() : 0.0)
            .sum();

        long todayActiveEmployees = todayAttendance.stream()
            .map(a -> a.getEmployee().getId()).distinct().count();
        long weeklyActiveEmployees = weeklyAttendance.stream()
            .map(a -> a.getEmployee().getId()).distinct().count();
        long monthlyActiveEmployees = monthlyAttendance.stream()
            .map(a -> a.getEmployee().getId()).distinct().count();

        long daysInWeekSoFar = Math.max(1, ChronoUnit.DAYS.between(startOfWeek, today) + 1);
        long daysInMonthSoFar = Math.max(1, ChronoUnit.DAYS.between(startOfMonth, today) + 1);

        double weeklyAvgHours = weeklyTotalHours / daysInWeekSoFar;
        double monthlyAvgHours = monthlyTotalHours / daysInMonthSoFar;
        
        model.addAttribute("todayTotalHours", todayTotalHours);
        model.addAttribute("weeklyTotalHours", weeklyTotalHours);
        model.addAttribute("monthlyTotalHours", monthlyTotalHours);

        model.addAttribute("todayActiveEmployees", todayActiveEmployees);
        model.addAttribute("weeklyActiveEmployees", weeklyActiveEmployees);
        model.addAttribute("monthlyActiveEmployees", monthlyActiveEmployees);
        model.addAttribute("weeklyAvgHours", weeklyAvgHours);
        model.addAttribute("monthlyAvgHours", monthlyAvgHours);
        
        return "reports-dashboard";
    }

    @RequestMapping(value = "/employee", method = RequestMethod.GET)
    public String employeeReport(@RequestParam(required = false) String period,
                                @RequestParam(required = false) String startDate,
                                @RequestParam(required = false) String endDate,
                                Model model, HttpSession session) {
        if (!isStaffUser(session)) {
            if (session != null && session.getAttribute("employee") != null) {
                return "redirect:/employee/dashboard";
            }
            return "redirect:/login";
        }
        
        LocalDate today = LocalDate.now();
        LocalDate start;
        LocalDate end = today;
        String detectedPeriod = (period != null && !period.isBlank()) ? period.trim().toLowerCase() : null;
        
        if (startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDate);
            if (detectedPeriod == null) {
                detectedPeriod = "custom";
            }
        } else if ("weekly".equalsIgnoreCase(detectedPeriod)) {
            start = today.minusDays(today.getDayOfWeek().getValue() - 1);
            end = today;
        } else if ("monthly".equalsIgnoreCase(detectedPeriod)) {
            start = today.withDayOfMonth(1);
            end = today;
        } else {
            detectedPeriod = "daily";
            start = today;
            end = today;
        }
        
        List<Attendance> attendanceList = attendanceRepository.findByDateRange(start, end);
        Map<String, Map<String, Object>> employeeSummary = new LinkedHashMap<>();
        double totalWorkHours = 0.0;
        long daysInPeriod = ChronoUnit.DAYS.between(start, end) + 1;
        
        for (Attendance att : attendanceList) {
            if (att.getEmployee() == null) continue;
            String empName = att.getEmployee().getName();
            double hours = att.getTotalHours() != null ? att.getTotalHours() : 0.0;
            totalWorkHours += hours;

            Map<String, Object> data = employeeSummary.computeIfAbsent(empName, k -> {
                Map<String, Object> map = new HashMap<>();
                map.put("name", empName);
                map.put("employeeId", att.getEmployee().getEmployeeId());
                map.put("totalHours", 0.0);
                map.put("daysPresent", 0L);
                return map;
            });

            data.put("totalHours", ((Double) data.get("totalHours")) + hours);
            data.put("daysPresent", ((Long) data.get("daysPresent")) + 1L);
        }
        
        Map<String, Double> employeeHours = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : employeeSummary.entrySet()) {
            double th = (Double) entry.getValue().get("totalHours");
            employeeHours.put(entry.getKey(), th);
            long dp = (Long) entry.getValue().get("daysPresent");
            double avg = dp > 0 ? th / dp : 0.0;
            entry.getValue().put("avgHoursPerDay", avg);
        }

        double overallDailyAvg = daysInPeriod > 0 ? totalWorkHours / daysInPeriod : 0.0;
        
        String periodTitle;
        if ("daily".equals(detectedPeriod)) {
            periodTitle = "Daily Work Hours (" + start + ")";
        } else if ("weekly".equals(detectedPeriod)) {
            periodTitle = "Weekly Work Hours (" + start + " to " + end + ")";
        } else if ("monthly".equals(detectedPeriod)) {
            periodTitle = "Monthly Work Hours (" + start.getMonth().name() + " " + start.getYear() + ")";
        } else {
            periodTitle = "Custom Work Hours (" + start + " to " + end + ")";
        }

        model.addAttribute("attendanceList", attendanceList);
        model.addAttribute("employeeHours", employeeHours);
        model.addAttribute("employeeSummary", employeeSummary.values());
        model.addAttribute("totalWorkHours", totalWorkHours);
        model.addAttribute("activeEmployeesCount", employeeSummary.size());
        model.addAttribute("totalRecordsCount", attendanceList.size());
        model.addAttribute("daysInPeriod", daysInPeriod);
        model.addAttribute("overallDailyAvg", overallDailyAvg);
        model.addAttribute("periodTitle", periodTitle);
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        model.addAttribute("period", detectedPeriod);
        
        return "employee-report";
    }

    @RequestMapping(value = "/visitor", method = RequestMethod.GET)
    public String visitorReport(@RequestParam(required = false) String period,
                               @RequestParam(required = false) String startDate,
                               @RequestParam(required = false) String endDate,
                               Model model, HttpSession session) {
        if (!isStaffUser(session)) {
            if (session != null && session.getAttribute("employee") != null) {
                return "redirect:/employee/dashboard";
            }
            return "redirect:/login";
        }
        
        LocalDate today = LocalDate.now();
        LocalDate start;
        LocalDate end = today;
        String detectedPeriod = (period != null && !period.isBlank()) ? period.trim().toLowerCase() : null;
        
        if (startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDate);
            if (detectedPeriod == null) {
                detectedPeriod = "custom";
            }
        } else if ("weekly".equalsIgnoreCase(detectedPeriod)) {
            start = today.minusDays(today.getDayOfWeek().getValue() - 1);
            end = today;
        } else if ("monthly".equalsIgnoreCase(detectedPeriod)) {
            start = today.withDayOfMonth(1);
            end = today;
        } else {
            detectedPeriod = "daily";
            start = today;
            end = today;
        }
        
        List<Visitor> visitors = visitorRepository.findByCheckInTimeBetween(
            start.atStartOfDay(),
            end.atTime(23, 59, 59)
        );
        
        Map<String, Long> visitorFlow = new TreeMap<>();
        for (Visitor v : visitors) {
            if (v.getCheckInTime() != null) {
                String date = v.getCheckInTime().toLocalDate().toString();
                visitorFlow.put(date, visitorFlow.getOrDefault(date, 0L) + 1L);
            }
        }

        long daysInPeriod = ChronoUnit.DAYS.between(start, end) + 1;
        double avgVisitorsPerDay = daysInPeriod > 0 ? (double) visitors.size() / daysInPeriod : 0.0;

        String periodTitle;
        if ("daily".equals(detectedPeriod)) {
            periodTitle = "Daily Visitor Flow (" + start + ")";
        } else if ("weekly".equals(detectedPeriod)) {
            periodTitle = "Weekly Visitor Flow (" + start + " to " + end + ")";
        } else if ("monthly".equals(detectedPeriod)) {
            periodTitle = "Monthly Visitor Flow (" + start.getMonth().name() + " " + start.getYear() + ")";
        } else {
            periodTitle = "Custom Visitor Flow (" + start + " to " + end + ")";
        }
        
        model.addAttribute("visitors", visitors);
        model.addAttribute("visitorFlow", visitorFlow);
        model.addAttribute("totalVisitors", visitors.size());
        model.addAttribute("daysInPeriod", daysInPeriod);
        model.addAttribute("avgVisitorsPerDay", avgVisitorsPerDay);
        model.addAttribute("periodTitle", periodTitle);
        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        model.addAttribute("period", detectedPeriod);
        
        return "visitor-report";
    }

    @GetMapping("/export/performance")
    public void exportPerformance(HttpServletResponse response, HttpSession session) throws IOException {
        boolean canExport = false;
        if (authorizationService != null) {
            canExport = authorizationService.hasAnyRole(session, com.example.demo.service.AuthorizationService.ROLE_ADMIN, com.example.demo.service.AuthorizationService.ROLE_HR);
        } else if (session != null) {
            Object userObj = session.getAttribute("user");
            if (userObj instanceof com.example.demo.model.User user) {
                String role = user.getRole();
                canExport = "ADMIN".equalsIgnoreCase(role) || "HR".equalsIgnoreCase(role);
            }
        }

        if (!canExport) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Performance export restricted to ADMIN and HR");
            return;
        }

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"Performance_Report_" + LocalDate.now() + ".csv\"");
        try (PrintWriter w = response.getWriter()) {
            w.println("Employee,Period Start,Period End,Composite Score,Attendance Score,Task Score,Goal Score,Issue Score,Tasks Completed,Tasks Total,Issues Completed,Issues Total");
            List<Employee> emps = employeeRepository.findAll().stream()
                    .filter(e -> e.getIsActive() == null || e.getIsActive()).toList();
            for (Employee emp : emps) {
                PerformanceRecord r = performanceScoreService.getOrCreateWeeklyRecord(emp);
                w.printf("\"%s\",%s,%s,%.1f,%.1f,%.1f,%.1f,%.1f,%d,%d,%d,%d%n",
                        emp.getName().replace("\"", "\"\""),
                        r.getPeriodStart(), r.getPeriodEnd(),
                        r.getCompositeScore() != null ? r.getCompositeScore() : 0,
                        r.getAttendanceScore() != null ? r.getAttendanceScore() : 0,
                        r.getTaskScore() != null ? r.getTaskScore() : 0,
                        r.getGoalScore() != null ? r.getGoalScore() : 0,
                        r.getIssueScore() != null ? r.getIssueScore() : 0,
                        r.getTasksCompleted() != null ? r.getTasksCompleted() : 0,
                        r.getTasksTotal() != null ? r.getTasksTotal() : 0,
                        r.getIssuesCompleted() != null ? r.getIssuesCompleted() : 0,
                        r.getIssuesTotal() != null ? r.getIssuesTotal() : 0);
            }
        }
    }

    @GetMapping("/export/tasks")
    public void exportTasks(HttpServletResponse response, HttpSession session) throws IOException {
        if (!isStaffUser(session)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Restricted to Staff");
            return;
        }
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"Tasks_Report_" + LocalDate.now() + ".csv\"");
        try (PrintWriter w = response.getWriter()) {
            w.println("Title,Assignee,Assigned Date,Due Date,Status,Priority");
            for (Task t : taskRepository.findAll()) {
                w.printf("\"%s\",\"%s\",%s,%s,%s,%s%n",
                        (t.getTitle() != null ? t.getTitle().replace("\"", "\"\"") : ""),
                        (t.getEmployee() != null ? t.getEmployee().getName().replace("\"", "\"\"") : ""),
                        t.getAssignedDate(), t.getDueDate(), t.getStatus(), t.getPriority());
            }
        }
    }

    @GetMapping("/export/issues")
    public void exportIssues(HttpServletResponse response, HttpSession session) throws IOException {
        if (!isStaffUser(session)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Restricted to Staff");
            return;
        }
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"Issues_Report_" + LocalDate.now() + ".csv\"");
        try (PrintWriter w = response.getWriter()) {
            w.println("Key,Title,Type,Project,Assignee,Status,Priority,Created");
            for (Issue i : issueRepository.findAllWithDetails()) {
                w.printf("\"%s\",\"%s\",%s,\"%s\",\"%s\",%s,%s,%s%n",
                        i.getIssueKey(),
                        (i.getTitle() != null ? i.getTitle().replace("\"", "\"\"") : ""),
                        i.getIssueType(),
                        (i.getProject() != null ? i.getProject().getName().replace("\"", "\"\"") : ""),
                        (i.getAssignee() != null ? i.getAssignee().getName().replace("\"", "\"\"") : ""),
                        i.getStatus(), i.getPriority(),
                        i.getCreatedAt() != null ? i.getCreatedAt().toLocalDate() : "");
            }
        }
    }
}


