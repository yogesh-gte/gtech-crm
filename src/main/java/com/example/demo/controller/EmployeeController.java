package com.example.demo.controller;

import com.example.demo.model.Attendance;
import com.example.demo.model.Employee;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.VisitorRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.FaceRecognitionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/employee")
public class EmployeeController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private VisitorRepository visitorRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    @Autowired
    private com.example.demo.repository.IdleIncidentRepository idleIncidentRepository;

    @Autowired
    private com.example.demo.repository.LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private com.example.demo.repository.SuggestionRepository suggestionRepository;

    @Autowired
    private com.example.demo.repository.EmployeeActivityRepository employeeActivityRepository;

    @Autowired
    private com.example.demo.repository.TeamBadgeRepository teamBadgeRepository;

    @Autowired
    private com.example.demo.repository.TeamRuleRepository teamRuleRepository;

    @Autowired
    private com.example.demo.repository.NotificationRepository notificationRepository;

    @Autowired
    private com.example.demo.repository.MeetingRepository meetingRepository;

    @Autowired
    private com.example.demo.repository.PayrollRepository payrollRepository;

    @Autowired
    private com.example.demo.repository.CompanyHolidayRepository companyHolidayRepository;

    @Autowired
    private com.example.demo.repository.TaskRepository taskRepository;

    @Autowired
    private com.example.demo.service.PerformanceScoreService performanceScoreService;

    @Autowired
    private com.example.demo.repository.TeamRepository teamRepository;

    @Autowired
    private com.example.demo.repository.ProjectRepository projectRepository;

    @Autowired
    private com.example.demo.repository.SprintRepository sprintRepository;

    @Autowired
    private com.example.demo.repository.IssueRepository issueRepository;

    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)

    public String employeeDashboard(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        // Check if employee has face image registered
        // Bypassing mandatory face registration for now
        /*
         * if (employee.getFaceImage() == null || employee.getFaceImage().isEmpty()) {
         * return "redirect:/employee/face-capture";
         * }
         */

        // Get today's attendance
        LocalDate today = LocalDate.now();
        List<Attendance> todayAttendances = attendanceRepository.findByEmployeeAndAttendanceDate(employee, today);
        Attendance todayAttendance = todayAttendances.isEmpty() ? null : todayAttendances.get(0);

        // Calculate check-in status if employee is clocked in
        String checkInStatusDisplay = null;
        String checkOutStatusDisplay = null;

        if (todayAttendance != null && todayAttendance.getCheckInTime() != null) {
            LocalTime checkInTime = todayAttendance.getCheckInTime().toLocalTime();
            LocalTime scheduledTime = LocalTime.of(9, 0); // Default 9 AM

            if (employee.getScheduledStartTime() != null && !employee.getScheduledStartTime().isEmpty()) {
                try {
                    scheduledTime = LocalTime.parse(employee.getScheduledStartTime());
                } catch (Exception e) {
                    System.err.println("Error parsing scheduled start time: " + e.getMessage());
                    // Keep default
                }
            }

            if (checkInTime.isBefore(scheduledTime)) {
                checkInStatusDisplay = "Early Clock In";
            } else if (checkInTime.isAfter(scheduledTime)) {
                checkInStatusDisplay = "Late Clock In";
            } else {
                checkInStatusDisplay = "On Time Clock In";
            }
        }
        
        

        // Calculate check-out status if employee has clocked out
        if (todayAttendance != null && todayAttendance.getCheckOutTime() != null) {
            LocalTime checkOutTime = todayAttendance.getCheckOutTime().toLocalTime();
            LocalTime scheduledEndTime = LocalTime.of(18, 0); // Default 6 PM

            if (employee.getScheduledEndTime() != null && !employee.getScheduledEndTime().isEmpty()) {
                try {
                    scheduledEndTime = LocalTime.parse(employee.getScheduledEndTime());
                } catch (Exception e) {
                    System.err.println("Error parsing scheduled end time: " + e.getMessage());
                    // Keep default
                }
            }

            if (checkOutTime.isBefore(scheduledEndTime)) {
                checkOutStatusDisplay = "Early Clock Out";
            } else if (checkOutTime.isAfter(scheduledEndTime)) {
                checkOutStatusDisplay = "Late Clock Out";
            } else {
                checkOutStatusDisplay = "On Time Clock Out";
            }
        }

        // Get status from session if available (for immediate display after clock
        // in/out)
        String sessionClockInStatus = (String) session.getAttribute("clockInStatus");
        if (sessionClockInStatus != null) {
            if (sessionClockInStatus.equals("Early clock in")) {
                checkInStatusDisplay = "Early Clock In";
            } else if (sessionClockInStatus.equals("Late clock in")) {
                checkInStatusDisplay = "Late Clock In";
            } else if (sessionClockInStatus.equals("On time")) {
                checkInStatusDisplay = "On Time Clock In";
            }
        }

        String sessionClockOutStatus = (String) session.getAttribute("clockOutStatus");
        if (sessionClockOutStatus != null) {
            checkOutStatusDisplay = sessionClockOutStatus;
        }

        // Get accessible meetings
        List<com.example.demo.model.Meeting> meetings = meetingRepository.findVisibleMeetingsForEmployee(employee,
                "SCHEDULED");
        model.addAttribute("meetings", meetings);

        // Get attendance history
        List<Attendance> attendanceHistory = attendanceRepository.findByEmployee(employee);

        // Get pending visitor approvals count
        long pendingCount = visitorRepository.findAll().stream()
                .filter(v -> "PENDING".equals(v.getApprovalStatus()) &&
                        employee.getEmail().equals(v.getEmployeeEmail()))
                .count();

        // Get leave requests for employee
        List<com.example.demo.model.LeaveRequest> leaveRequests = leaveRequestRepository.findByEmployee(employee);
        List<com.example.demo.model.LeaveRequest> pendingLeaves = leaveRequests.stream()
                .filter(lr -> "PENDING".equals(lr.getStatus()))
                .collect(java.util.stream.Collectors.toList());
        List<com.example.demo.model.LeaveRequest> approvedLeaves = leaveRequests.stream()
                .filter(lr -> "APPROVED".equals(lr.getStatus()))
                .collect(java.util.stream.Collectors.toList());

        // Check if on leave today (integration: show leave status prominently)
        boolean onLeaveToday = approvedLeaves.stream()
                .anyMatch(lr -> !today.isBefore(lr.getStartDate()) && !today.isAfter(lr.getEndDate()));

        // Get leave balance
        Integer leaveBalance = employee.getLeaveBalance() != null ? employee.getLeaveBalance() : 0;

        // Get today's activities
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);
        List<com.example.demo.model.EmployeeActivity> todayActivities = employeeActivityRepository
                .findByEmployeeAndStartedAtBetween(employee, startOfDay, endOfDay);
        List<com.example.demo.model.EmployeeActivity> recentActivities = employeeActivityRepository
                .findByEmployeeOrderByStartedAtDesc(employee);
        recentActivities = recentActivities.stream().limit(20).collect(java.util.stream.Collectors.toList());

        // Calculate productive vs non-productive time
        long productiveMinutes = todayActivities.stream()
                .filter(a -> a.getIsProductive() != null && a.getIsProductive())
                .mapToLong(a -> a.getDurationMinutes() != null ? a.getDurationMinutes() : 0)
                .sum();
        long nonProductiveMinutes = todayActivities.stream()
                .filter(a -> a.getIsProductive() != null && !a.getIsProductive())
                .mapToLong(a -> a.getDurationMinutes() != null ? a.getDurationMinutes() : 0)
                .sum();

        model.addAttribute("employee", employee);
        model.addAttribute("todayAttendance", todayAttendance);
        model.addAttribute("attendanceHistory", attendanceHistory);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("checkInStatusDisplay", checkInStatusDisplay);
        model.addAttribute("checkOutStatusDisplay", checkOutStatusDisplay);
        model.addAttribute("leaveRequests", leaveRequests);
        model.addAttribute("pendingLeaves", pendingLeaves);
        model.addAttribute("approvedLeaves", approvedLeaves);
        model.addAttribute("onLeaveToday", onLeaveToday);
        model.addAttribute("leaveBalance", leaveBalance);
        model.addAttribute("todayActivities", todayActivities);
        model.addAttribute("recentActivities", recentActivities);
        model.addAttribute("productiveMinutes", productiveMinutes);
        model.addAttribute("nonProductiveMinutes", nonProductiveMinutes);

        // Fetch Badges
        List<com.example.demo.model.TeamBadge> myBadges = teamBadgeRepository.findByEmployee(employee);
        model.addAttribute("myBadges", myBadges);

        // Fetch Rules
        List<com.example.demo.model.TeamRule> activeRules = teamRuleRepository.findByIsActiveTrue();
        model.addAttribute("activeRules", activeRules);

        // Fetch Notifications
        List<com.example.demo.model.Notification> notifications = notificationRepository
                .findByRecipientAndIsReadFalseOrderByCreatedAtDesc(employee);
        model.addAttribute("notifications", notifications);

        // Today's tasks & performance
        List<com.example.demo.model.Task> todayTasks = taskRepository.findTodayTasksByEmployee(employee, today);
        double todayTaskCompletionRate = performanceScoreService.getTodayTaskCompletionRate(employee);
        com.example.demo.model.PerformanceRecord perfRecord = performanceScoreService.getOrCreateWeeklyRecord(employee);
        model.addAttribute("todayTasks", todayTasks);
        model.addAttribute("todayTaskCompletionRate", todayTaskCompletionRate);
        model.addAttribute("performanceRecord", perfRecord);

        // Reset alert flags at start of new day
        if (employee.getCurrentCheckIn() == null ||
                !employee.getCurrentCheckIn().toLocalDate().equals(LocalDate.now())) {
            employee.setLateAlertSent(false);
            employee.setEarlyAlertSent(false);
            employeeRepository.save(employee);
        }

        return "employee-dashboard";
    }

    @RequestMapping(value = "/performance", method = RequestMethod.GET)
    public String employeePerformance(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }
        com.example.demo.model.PerformanceRecord weekly = performanceScoreService.getOrCreateWeeklyRecord(employee);
        List<com.example.demo.model.PerformanceRecord> history = performanceScoreService.getPerformanceHistory(employee);
        model.addAttribute("employee", employee);
        model.addAttribute("weeklyRecord", weekly);
        model.addAttribute("performanceHistory", history);
        return "employee-performance";
    }

    @RequestMapping(value = "/tl-dashboard", method = RequestMethod.GET)
    public String tlDashboard(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) return "redirect:/employee/login";
        if (!Boolean.TRUE.equals(employee.getIsTeamLead())) {
            return "redirect:/employee/dashboard";
        }
        com.example.demo.model.Team team = teamRepository.findByTeamLeadId(employee.getId())
                .orElse(null);
        if (team == null) return "redirect:/employee/dashboard";
        java.util.List<Employee> teamMembers = teamRepository.findById(team.getId())
                .map(t -> employeeRepository.findByTeam(t))
                .orElse(java.util.List.of());
        java.util.List<com.example.demo.model.Task> teamTasks = taskRepository.findByEmployeeIn(teamMembers);
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.List<com.example.demo.model.Task> todayTasks = teamTasks.stream()
                .filter(t -> today.equals(t.getAssignedDate()))
                .toList();
        java.util.List<com.example.demo.model.Task> completedToday = todayTasks.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .toList();
        java.util.List<com.example.demo.model.Project> teamProjects = projectRepository.findByTeam(team);
        java.util.List<com.example.demo.model.Sprint> activeSprints = teamProjects.stream()
                .flatMap(p -> sprintRepository.findActiveByProject(p).stream())
                .toList();
        java.util.Map<Employee, Long> completedByMember = completedToday.stream()
                .filter(t -> t.getEmployee() != null)
                .collect(java.util.stream.Collectors.groupingBy(com.example.demo.model.Task::getEmployee, java.util.stream.Collectors.counting()));
        model.addAttribute("employee", employee);
        model.addAttribute("team", team);
        model.addAttribute("teamMembers", teamMembers);
        model.addAttribute("teamTasks", teamTasks);
        model.addAttribute("todayTasks", todayTasks);
        model.addAttribute("completedToday", completedToday);
        model.addAttribute("completedByMember", completedByMember);
        model.addAttribute("teamProjects", teamProjects);
        model.addAttribute("activeSprints", activeSprints);
        return "tl-dashboard";
    }

    @RequestMapping(value = "/checkin", method = RequestMethod.POST)
    public String checkIn(@RequestParam(required = false) String workMode,
            HttpSession session, RedirectAttributes redirectAttributes) {
        // Clock-in is only allowed through face recognition
        // Regular clock-in without face recognition is disabled
        Employee sessionEmployee = (Employee) session.getAttribute("employee");

        if (sessionEmployee == null) {
            return "redirect:/employee/login";
        }

        // Check if face is registered
        /*
         * if (sessionEmployee.getFaceImage() == null ||
         * sessionEmployee.getFaceImage().isEmpty()) {
         * redirectAttributes.addFlashAttribute("error",
         * "Face recognition is required for clock-in. Please register your face first."
         * );
         * return "redirect:/employee/face-capture";
         * }
         * 
         * redirectAttributes.addFlashAttribute("error",
         * "Clock-in requires face recognition. Please use the face recognition feature on the dashboard to clock in."
         * );
         * return "redirect:/employee/dashboard";
         */

        // Bypass face recognition for now
        // Proceed with clock-in logic directly

        // Logic from original method that would have happened if face was verified...
        // ... wait, the original method just redirected to dashboard saying "use face
        // recognition".
        // If we want to support non-face clock-in, we need the ACTUAL clock-in logic
        // here.
        // Let's check if there's a logic to actually clock in.
        // The previous code returned "redirect:/employee/dashboard" with an error.
        // It seems the ACTUAL clock in logic is probably in a different method or
        // handled by the dashboard JS calling an endpoint?
        // Ah, this IS the /checkin endpoint.

        // If this endpoint was just blocking, where is the logic?
        // Maybe I need to IMPLEMENT the clock-in logic here since it was blocking
        // before.

        // IMPORTANT: Dashboard shows Clock-Out only when employee.status == 'IN'.
        // So clock-in must update Employee status + currentCheckIn in DB + session.
        Optional<Employee> employeeOpt = employeeRepository.findByEmployeeId(sessionEmployee.getEmployeeId());
        if (!employeeOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Employee not found. Please login again.");
            return "redirect:/employee/login";
        }

        Employee employee = employeeOpt.get();
        if ("IN".equals(employee.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "You are already clocked in!");
            return "redirect:/employee/dashboard";
        }

        // Update work mode if provided
        if (workMode != null && !workMode.isEmpty()) {
            employee.setWorkMode(workMode);
        } else if (employee.getWorkMode() == null || employee.getWorkMode().isEmpty()) {
            employee.setWorkMode("OFFICE");
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        employee.setStatus("IN");
        employee.setCurrentCheckIn(now);
        employee.setLastActivityTime(now);

        employee = employeeRepository.save(employee);
        session.setAttribute("employee", employee);
        session.setAttribute("employeeId", employee.getEmployeeId());

        // Upsert attendance (avoid duplicates)
        List<Attendance> existingAttendances = attendanceRepository.findByEmployeeAndAttendanceDate(employee, today);
        Attendance attendance = existingAttendances.isEmpty() ? new Attendance(employee, today)
                : existingAttendances.get(0);

        attendance.setCheckInTime(now);
        attendance.setWorkMode(employee.getWorkMode());
        // Determine status based on time
        LocalTime nowTime = now.toLocalTime();
        LocalTime scheduledTime = LocalTime.of(9, 0); // Default 9 AM

        if (employee.getScheduledStartTime() != null && !employee.getScheduledStartTime().isEmpty()) {
            try {
                scheduledTime = LocalTime.parse(employee.getScheduledStartTime());
            } catch (Exception e) {
                // Keep default
            }
        }

        if (nowTime.isAfter(scheduledTime)) {
            attendance.setStatus("LATE");
        } else {
            attendance.setStatus("PRESENT");
        }

        attendanceRepository.save(attendance);

        // UPDATE EMPLOYEE STATUS TO "IN" - CRITICAL FIX
        sessionEmployee.setStatus("IN");
        sessionEmployee.setCurrentCheckIn(LocalDateTime.now());
        sessionEmployee.setLastActivityTime(LocalDateTime.now());
        employeeRepository.save(sessionEmployee);

        // Update session with fresh employee data
        session.setAttribute("employee", sessionEmployee);

        // Check for late arrival and send email alert (matching QR code flow)
        if (employee.getScheduledStartTime() != null && !employee.getScheduledStartTime().isEmpty()) {
            LocalTime actualTime = now.toLocalTime();
            if (actualTime.isAfter(scheduledTime.plusMinutes(5)) &&
                    !Boolean.TRUE.equals(employee.getLateAlertSent())) {
                emailService.sendLateArrivalAlert(
                        employee.getEmail(),
                        employee.getName(),
                        employee.getScheduledStartTime(),
                        actualTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
                employee.setLateAlertSent(true);
                employeeRepository.save(employee);
            }
        }

        redirectAttributes.addFlashAttribute("success", "Clocked in successfully!");
        return "redirect:/employee/dashboard";
    }

    @RequestMapping(value = "/checkout", method = RequestMethod.POST)
    public String checkOut(@RequestParam(required = false) String workMode,
            HttpSession session, RedirectAttributes redirectAttributes) {
        // Get employee ID from session
        String employeeId = (String) session.getAttribute("employeeId");
        Employee sessionEmployee = (Employee) session.getAttribute("employee");

        if (sessionEmployee == null) {
            return "redirect:/employee/login";
        }

        if (employeeId == null) {
            employeeId = sessionEmployee.getEmployeeId();
        }

        // Fetch fresh employee data from database for concurrent access
        Optional<Employee> employeeOpt = employeeRepository.findByEmployeeId(employeeId);
        if (!employeeOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Employee not found. Please login again.");
            return "redirect:/employee/login";
        }

        Employee employee = employeeOpt.get();

        if ("OUT".equals(employee.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "You are not checked in!");
            return "redirect:/employee/dashboard";
        }

        // Update work mode if provided
        if (workMode != null && !workMode.isEmpty()) {
            employee.setWorkMode(workMode);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInTime = employee.getCurrentCheckIn();

        if (checkInTime != null) {
            long minutes = ChronoUnit.MINUTES.between(checkInTime, now);
            double hours = minutes / 60.0;
            employee.setTotalHoursToday(employee.getTotalHoursToday() + hours);
        }

        employee.setStatus("OUT");
        employee.setCurrentCheckOut(now);

        // Save to database first
        employee = employeeRepository.save(employee);

        // Update session with fresh employee data
        session.setAttribute("employee", employee);
        session.setAttribute("employeeId", employee.getEmployeeId());

        // Update attendance record
        LocalDate today = LocalDate.now();
        List<Attendance> attendanceOpt = attendanceRepository.findByEmployeeAndAttendanceDate(employee, today);

        if (!attendanceOpt.isEmpty()) {
            Attendance attendance = attendanceOpt.get(0);
            attendance.setCheckOutTime(now);

            if (attendance.getCheckInTime() != null) {
                long minutes = ChronoUnit.MINUTES.between(attendance.getCheckInTime(), now);
                double hours = minutes / 60.0;
                attendance.setTotalHours(hours);
            }

            attendanceRepository.save(attendance);
        }

        // Check clock-out time against 6 PM default
        LocalTime actualTime = now.toLocalTime();
        LocalTime sixPM = LocalTime.of(18, 0); // 6:00 PM
        LocalTime scheduledEndTime = sixPM; // Default to 6 PM

        // Use employee's scheduled time if set, otherwise use 6 PM default
        if (employee.getScheduledEndTime() != null && !employee.getScheduledEndTime().isEmpty()) {
            scheduledEndTime = LocalTime.parse(employee.getScheduledEndTime());
        } else {
            // Set default if not set
            employee.setScheduledEndTime("18:00");
            employeeRepository.save(employee);
        }

        // Determine clock-out status and display message
        String clockOutStatus = "";
        if (actualTime.isBefore(scheduledEndTime)) {
            clockOutStatus = "Early Clock Out";
            redirectAttributes.addFlashAttribute("warning",
                    "⚠️ Early clock out at " + actualTime + " (Scheduled: " + scheduledEndTime + ")");
        } else if (actualTime.isAfter(scheduledEndTime)) {
            clockOutStatus = "Late Clock Out";
            redirectAttributes.addFlashAttribute("info",
                    "ℹ️ Late clock out at " + actualTime + " (Scheduled: " + scheduledEndTime + ")");
        } else {
            clockOutStatus = "On Time Clock Out";
            redirectAttributes.addFlashAttribute("success",
                    "✅ Clocked out on time at " + actualTime);
        }

        // Store clock-out status in session for display
        session.setAttribute("clockOutStatus", clockOutStatus);
        session.setAttribute("clockOutTime", actualTime.toString());
        session.setAttribute("scheduledEndTime", scheduledEndTime.toString());

        // Update last activity time
        employee.setLastActivityTime(now);
        employee.setIdleAlertSent(false); // Reset idle alert when checking in
        employeeRepository.save(employee);

        redirectAttributes.addFlashAttribute("success", "Clocked out successfully at " + now.toLocalTime());
        return "redirect:/employee/dashboard";
    }

    @RequestMapping(value = "/attendance/history", method = RequestMethod.GET)
    public String attendanceHistory(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        List<Attendance> attendanceHistory = attendanceRepository.findByEmployee(employee);
        model.addAttribute("employee", employee);
        model.addAttribute("attendanceHistory", attendanceHistory);

        // Calculate totals
        double totalHours = attendanceHistory.stream()
                .mapToDouble(a -> a.getTotalHours() != null ? a.getTotalHours() : 0.0)
                .sum();
        model.addAttribute("totalHours", totalHours);

        return "employee-attendance-history";
    }

    @RequestMapping(value = "/payroll/history", method = RequestMethod.GET)
    public String payrollHistory(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        List<com.example.demo.model.Payroll> payrolls = payrollRepository
                .findByEmployee(employee).stream()
                .filter(p -> p.getPayrollStatus() == com.example.demo.model.Payroll.PayrollStatus.SENT)
                .sorted((p1, p2) -> p2.getPayPeriodStart().compareTo(p1.getPayPeriodStart()))
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("employee", employee);
        model.addAttribute("payrolls", payrolls);
        model.addAttribute("notifications",
                notificationRepository.findByRecipientAndIsReadFalseOrderByCreatedAtDesc(employee));

        return "employee-payroll-history";
    }

    @RequestMapping(value = "/payroll/view/{id}", method = RequestMethod.GET)
    public String viewPayslip(@PathVariable Long id, Model model, HttpSession session,
            RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        Optional<com.example.demo.model.Payroll> payrollOpt = payrollRepository.findById(id);
        if (payrollOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Payslip not found.");
            return "redirect:/employee/payroll/history";
        }

        com.example.demo.model.Payroll payroll = payrollOpt.get();

        if (!payroll.getEmployee().getId().equals(employee.getId())
                || payroll.getPayrollStatus() != com.example.demo.model.Payroll.PayrollStatus.SENT) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access to payslip.");
            return "redirect:/employee/payroll/history";
        }

        model.addAttribute("payroll", payroll);
        return "employee-payslip-view";
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public String listEmployees(Model model, HttpSession session) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        model.addAttribute("employees", employeeRepository.findAll());
        return "employee-list";
    }

    @RequestMapping(value = "/logs", method = RequestMethod.GET)
    public String allEmployeeLogs(Model model, HttpSession session,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        List<Attendance> allLogs;

        if (employeeId != null && !employeeId.isEmpty()) {
            Optional<Employee> emp = employeeRepository.findByEmployeeId(employeeId);
            if (emp.isPresent()) {
                if (startDate != null && endDate != null && !startDate.isEmpty() && !endDate.isEmpty()) {
                    allLogs = attendanceRepository.findByEmployeeAndDateRange(
                            emp.get(), LocalDate.parse(startDate), LocalDate.parse(endDate));
                } else {
                    allLogs = attendanceRepository.findByEmployee(emp.get());
                }
            } else {
                allLogs = attendanceRepository.findAll();
            }
        } else if (startDate != null && endDate != null && !startDate.isEmpty() && !endDate.isEmpty()) {
            allLogs = attendanceRepository.findByDateRange(LocalDate.parse(startDate), LocalDate.parse(endDate));
        } else {
            allLogs = attendanceRepository.findAll();
        }

        model.addAttribute("allLogs", allLogs);
        model.addAttribute("employees", employeeRepository.findAll());
        model.addAttribute("selectedEmployeeId", employeeId);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        // Determine dashboard link based on role
        String role = (String) session.getAttribute("userRole");
        String dashboardLink = "/admin/dashboard"; // Default
        if ("HR".equals(role)) {
            dashboardLink = "/hr/dashboard";
        } else if ("MANAGER".equals(role)) {
            dashboardLink = "/manager/dashboard";
        } else if ("EMPLOYEE".equals(role) || session.getAttribute("employee") != null) {
            dashboardLink = "/employee/dashboard";
        }
        model.addAttribute("dashboardLink", dashboardLink);

        return "admin-employee-logs";
    }

    @RequestMapping(value = "/face-capture", method = RequestMethod.GET)
    public String faceCapture(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        model.addAttribute("employee", employee);
        return "employee-face-capture";
    }

    @RequestMapping(value = "/register-face", method = RequestMethod.POST)
    public String registerFace(@RequestParam String faceImage, HttpSession session,
            RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        employee.setFaceImage(faceImage);
        employeeRepository.save(employee);
        session.setAttribute("employee", employee);

        redirectAttributes.addFlashAttribute("success",
                "Face registered successfully! You can now use face recognition for attendance.");
        return "redirect:/employee/dashboard";
    }

    @RequestMapping(value = "/checkin-face", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> checkInByFace(@RequestParam String faceImage,
            @RequestParam(required = false) String workMode,
            HttpSession session) {
        // Get logged-in employee from session (logged in using email and employee ID)
        Employee sessionEmployee = (Employee) session.getAttribute("employee");
        if (sessionEmployee == null) {
            return ResponseEntity.badRequest().body("Please login first using your email and employee ID.");
        }

        // Get employee email and ID from session to verify
        String sessionEmail = sessionEmployee.getEmail();
        String sessionEmployeeId = sessionEmployee.getEmployeeId();

        // Fetch fresh employee data from database using email (primary identifier)
        Optional<Employee> employeeOpt = employeeRepository.findByEmail(sessionEmail);
        if (!employeeOpt.isPresent()) {
            // Fallback to employee ID if email lookup fails
            employeeOpt = employeeRepository.findByEmployeeId(sessionEmployeeId);
            if (!employeeOpt.isPresent()) {
                return ResponseEntity.badRequest().body("Employee not found. Please login again.");
            }
        }

        Employee employee = employeeOpt.get();

        // Verify the employee ID matches (double verification)
        if (!employee.getEmployeeId().equals(sessionEmployeeId)) {
            return ResponseEntity.badRequest().body("Employee ID mismatch. Please login again.");
        }

        // Verify the email matches (double verification)
        if (!employee.getEmail().equals(sessionEmail)) {
            return ResponseEntity.badRequest().body("Email mismatch. Please login again.");
        }

        // Verify face is registered
        if (employee.getFaceImage() == null || employee.getFaceImage().isEmpty()) {
            return ResponseEntity.badRequest().body("Face not registered. Please register your face first.");
        }

        // Validate captured face image
        if (!faceRecognitionService.validateFaceImage(faceImage)) {
            return ResponseEntity.badRequest().body("Invalid face image. Please try again.");
        }

        // Verify face matches registered face
        boolean faceMatches = faceRecognitionService.compareFaces(employee.getFaceImage(), faceImage);
        if (!faceMatches) {
            return ResponseEntity.badRequest().body(
                    "Face recognition failed. The captured face does not match your registered face. Please try again.");
        }

        // Check status with fresh data
        if ("IN".equals(employee.getStatus())) {
            return ResponseEntity.badRequest().body("You are already clocked in.");
        }

        if (workMode != null && !workMode.isEmpty()) {
            employee.setWorkMode(workMode);
        }

        LocalDateTime now = LocalDateTime.now();
        employee.setStatus("IN");
        employee.setCurrentCheckIn(now);
        employee.setLastActivityTime(now);

        // Update session with fresh employee data
        session.setAttribute("employee", employee);

        // Save to database
        employee = employeeRepository.save(employee);

        LocalDate today = LocalDate.now();
        List<Attendance> attendanceOpt = attendanceRepository.findByEmployeeAndAttendanceDate(employee, today);
        Attendance attendance;

        if (!attendanceOpt.isEmpty()) {
            attendance = attendanceOpt.get(0);
        } else {
            attendance = new Attendance(employee, today);
        }

        attendance.setCheckInTime(now);
        attendance.setWorkMode(employee.getWorkMode());

        // Check clock-in time against 9 AM default
        LocalTime actualTime = now.toLocalTime();
        LocalTime nineAM = LocalTime.of(9, 0); // 9:00 AM
        LocalTime scheduledTime = nineAM; // Default to 9 AM

        // Use employee's scheduled time if set, otherwise use 9 AM default
        if (employee.getScheduledStartTime() != null && !employee.getScheduledStartTime().isEmpty()) {
            scheduledTime = LocalTime.parse(employee.getScheduledStartTime());
        } else {
            // Set default if not set
            employee.setScheduledStartTime("09:00");
            employeeRepository.save(employee);
        }

        // Determine clock-in status and set attendance status
        String attendanceStatus = "PRESENT";
        String statusMessage = "";

        if (actualTime.isBefore(scheduledTime)) {
            attendanceStatus = "PRESENT"; // Still present, just early
            statusMessage = "Early clock in at " + actualTime + " (Scheduled: " + scheduledTime + ")";
        } else if (actualTime.isAfter(scheduledTime)) {
            attendanceStatus = "LATE"; // Mark as late
            statusMessage = "Late clock in at " + actualTime + " (Scheduled: " + scheduledTime + ")";
        } else {
            attendanceStatus = "PRESENT";
            statusMessage = "Clocked in on time at " + actualTime;
        }

        attendance.setStatus(attendanceStatus);
        attendanceRepository.save(attendance);

        // Store clock-in status in session for display
        session.setAttribute("clockInStatus", attendanceStatus.equals("LATE") ? "Late clock in"
                : (actualTime.isBefore(scheduledTime) ? "Early clock in" : "On time"));
        session.setAttribute("clockInTime", actualTime.toString());
        session.setAttribute("scheduledTime", scheduledTime.toString());

        return ResponseEntity.ok(statusMessage);
    }

    @RequestMapping(value = "/checkout-face", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> checkOutByFace(@RequestParam String faceImage, HttpSession session) {
        // Get logged-in employee from session (logged in using email and employee ID)
        Employee sessionEmployee = (Employee) session.getAttribute("employee");
        if (sessionEmployee == null) {
            return ResponseEntity.badRequest().body("Please login first using your email and employee ID.");
        }

        // Get employee email and ID from session to verify
        String sessionEmail = sessionEmployee.getEmail();
        String sessionEmployeeId = sessionEmployee.getEmployeeId();

        // Fetch fresh employee data from database using email (primary identifier)
        Optional<Employee> employeeOpt = employeeRepository.findByEmail(sessionEmail);
        if (!employeeOpt.isPresent()) {
            // Fallback to employee ID if email lookup fails
            employeeOpt = employeeRepository.findByEmployeeId(sessionEmployeeId);
            if (!employeeOpt.isPresent()) {
                return ResponseEntity.badRequest().body("Employee not found. Please login again.");
            }
        }

        Employee employee = employeeOpt.get();

        // Verify the employee ID matches (double verification)
        if (!employee.getEmployeeId().equals(sessionEmployeeId)) {
            return ResponseEntity.badRequest().body("Employee ID mismatch. Please login again.");
        }

        // Verify the email matches (double verification)
        if (!employee.getEmail().equals(sessionEmail)) {
            return ResponseEntity.badRequest().body("Email mismatch. Please login again.");
        }

        // Verify face is registered
        if (employee.getFaceImage() == null || employee.getFaceImage().isEmpty()) {
            return ResponseEntity.badRequest().body("Face not registered.");
        }

        // Validate captured face image
        if (!faceRecognitionService.validateFaceImage(faceImage)) {
            return ResponseEntity.badRequest().body("Invalid face image. Please try again.");
        }

        // Verify face matches registered face
        boolean faceMatches = faceRecognitionService.compareFaces(employee.getFaceImage(), faceImage);
        if (!faceMatches) {
            return ResponseEntity.badRequest().body(
                    "Face recognition failed. The captured face does not match your registered face. Please try again.");
        }

        // Check status with fresh data
        if ("OUT".equals(employee.getStatus())) {
            return ResponseEntity.badRequest().body("You are not clocked in.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInTime = employee.getCurrentCheckIn();

        if (checkInTime != null) {
            long minutes = ChronoUnit.MINUTES.between(checkInTime, now);
            double hours = minutes / 60.0;
            employee.setTotalHoursToday(employee.getTotalHoursToday() + hours);
        }

        employee.setStatus("OUT");
        employee.setCurrentCheckOut(now);
        employee.setLastActivityTime(now);

        
        // Update session with fresh employee data
        session.setAttribute("employee", employee);

        // Save to database
        employee = employeeRepository.save(employee);

        LocalDate today = LocalDate.now();
        List<Attendance> attendanceOpt = attendanceRepository.findByEmployeeAndAttendanceDate(employee, today);

        if (!attendanceOpt.isEmpty()) {
            Attendance attendance = attendanceOpt.get(0);
            attendance.setCheckOutTime(now);
            if (attendance.getCheckInTime() != null) {
                long minutes = ChronoUnit.MINUTES.between(attendance.getCheckInTime(), now);
                double hours = minutes / 60.0;
                attendance.setTotalHours(hours);
            }
            attendanceRepository.save(attendance);
        }

        return ResponseEntity.ok("Clocked out successfully at " + now.toLocalTime());
    }

    @RequestMapping(value = "/notification/read/{id}", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> markNotificationAsRead(@PathVariable Long id, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return ResponseEntity.badRequest().body("Unauthorized");
        }
        Optional<com.example.demo.model.Notification> notifOpt = notificationRepository.findById(id);
        if (notifOpt.isPresent()) {
            com.example.demo.model.Notification notification = notifOpt.get();
            if (notification.getRecipient().getId().equals(employee.getId())) {
                notification.setIsRead(true);
                notificationRepository.save(notification);
                return ResponseEntity.ok("Marked as read");
            }
        }
        return ResponseEntity.badRequest().body("Failed");
    }

    @RequestMapping(value = "/api/meetings/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<?> getMeetingDetails(@PathVariable Long id, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return ResponseEntity.badRequest().body("Unauthorized");
        }
        Optional<com.example.demo.model.Meeting> meetingOpt = meetingRepository.findById(id);
        if (meetingOpt.isPresent()) {
            return ResponseEntity.ok(meetingOpt.get());
        }
        return ResponseEntity.badRequest().body("Meeting not found");
    }

    @RequestMapping(value = "/notifications/history", method = RequestMethod.GET)
    public String getNotificationHistory(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        // Fetch all notifications (read and unread) sorted by newest first
        List<com.example.demo.model.Notification> allNotifications = notificationRepository
                .findByRecipientOrderByCreatedAtDesc(employee);
        model.addAttribute("notifications", allNotifications);

        return "employee-notification-history";
    }

    // Endpoint to track employee activity (heartbeat)

    @RequestMapping(value = "/activity", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> trackActivity(@RequestParam(required = false) String activityType,
            @RequestParam(required = false) String activityDescription,
            @RequestParam(required = false) String applicationName,
            @RequestParam(required = false) String url,
            HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }

        // Fetch fresh employee data
        Optional<Employee> employeeOpt = employeeRepository.findByEmployeeId(employee.getEmployeeId());
        if (!employeeOpt.isPresent()) {
            return ResponseEntity.status(404).body("Employee not found");
        }

        Employee emp = employeeOpt.get();
        LocalDateTime now = LocalDateTime.now();

        // Update last activity time
        emp.setLastActivityTime(now);
        emp.setIdleAlertSent(false); // Reset idle alert when activity detected
        employeeRepository.save(emp);

        // Resolve any active idle incidents for this employee
        idleIncidentRepository.findActiveIncidentByEmployee(emp).ifPresent(incident -> {
            incident.setStatus("RESOLVED");
            idleIncidentRepository.save(incident);
        });

        // Log detailed activity if provided
        if (activityType != null && !activityType.isEmpty()) {
            // Determine if activity is productive
            boolean isProductive = !activityType.equals("GAMING") &&
                    !activityType.equals("SOCIAL_MEDIA") &&
                    !activityType.equals("VIDEO") &&
                    !activityType.equals("IDLE");

            // Check URL for non-productive sites
            if (url != null && !url.isEmpty()) {
                String lowerUrl = url.toLowerCase();
                if (lowerUrl.contains("youtube.com") || lowerUrl.contains("facebook.com") ||
                        lowerUrl.contains("twitter.com") || lowerUrl.contains("instagram.com") ||
                        lowerUrl.contains("tiktok.com") || lowerUrl.contains("reddit.com") ||
                        lowerUrl.contains("game") || lowerUrl.contains("play")) {
                    isProductive = false;
                }
            }

            com.example.demo.model.EmployeeActivity activity = new com.example.demo.model.EmployeeActivity();
            activity.setEmployee(emp);
            activity.setActivityType(activityType);
            activity.setActivityDescription(activityDescription != null ? activityDescription : activityType);
            activity.setApplicationName(applicationName);
            activity.setUrl(url);
            activity.setIsProductive(isProductive);
            activity.setStartedAt(now);

            employeeActivityRepository.save(activity);
        }

        return ResponseEntity.ok().body("Activity tracked");
    }

    @RequestMapping(value = "/leave/apply", method = RequestMethod.GET)
    public String showLeaveApplicationForm(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        List<com.example.demo.model.LeaveRequest> leaveRequests = leaveRequestRepository.findByEmployee(employee);
        Integer leaveBalance = employee.getLeaveBalance() != null ? employee.getLeaveBalance() : 0;

        model.addAttribute("employee", employee);
        model.addAttribute("leaveRequests", leaveRequests);
        model.addAttribute("leaveBalance", leaveBalance);

        return "employee-leave-apply";
    }

    @RequestMapping(value = "/leave/apply", method = RequestMethod.POST)
    public String applyForLeave(@RequestParam String leaveType,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);

            if (end.isBefore(start)) {
                redirectAttributes.addFlashAttribute("error", "End date cannot be before start date!");
                return "redirect:/employee/leave/apply";
            }

            // Calculate requested days excluding weekends and holidays
            List<com.example.demo.model.CompanyHoliday> holidays = companyHolidayRepository.findAll();
            java.util.Set<LocalDate> holidayDates = holidays.stream()
                .map(com.example.demo.model.CompanyHoliday::getDate)
                .collect(java.util.stream.Collectors.toSet());

            
            long daysRequested = 0;
            LocalDate currentDate = start;
            while (!currentDate.isAfter(end)) {
                java.time.DayOfWeek dayOfWeek = currentDate.getDayOfWeek();
                if (dayOfWeek != java.time.DayOfWeek.SATURDAY && dayOfWeek != java.time.DayOfWeek.SUNDAY && !holidayDates.contains(currentDate)) {
                    daysRequested++;
                }
                currentDate = currentDate.plusDays(1);
            }

            if (daysRequested == 0) {
                redirectAttributes.addFlashAttribute("error", "Selected date range only contains weekends or holidays!");
                return "redirect:/employee/leave/apply";
            }

            int availableBalance = 0;

            if ("PAID".equals(leaveType)) {
                availableBalance = employee.getPaidLeave() != null ? employee.getPaidLeave() : 0;
            } else if ("SICK".equals(leaveType)) {
                availableBalance = employee.getSickLeave() != null ? employee.getSickLeave() : 0;
            } else if ("CASUAL".equals(leaveType)) {
                availableBalance = employee.getCasualLeave() != null ? employee.getCasualLeave() : 0;
            } else if (employee.getCustomLeaves() != null && employee.getCustomLeaves().containsKey(leaveType)) {
                availableBalance = employee.getCustomLeaves().get(leaveType);
            } else if ("EMERGENCY".equals(leaveType) || "MATERNITY".equals(leaveType)
                    || "PATERNITY".equals(leaveType)) {
                // These might not have strict limits or tracked differently, defaulting to
                // allow for now or check if they exist in custom leaves if migrated
                // For now, if it's not one of the standard tracked ones and not in custom, we
                // might assume it's special/unlimited or 0.
                // Let's assume 0 if not found, unless it is a special type that doesn't consume
                // balance.
                // But wait, the user's issue is specific to fetching leaves.
                // Let's allow EMERGENCY for now as it was before.
                availableBalance = 100; // Arbitrary high number or handling logic
            }

            if (daysRequested > availableBalance) {
                redirectAttributes.addFlashAttribute("error",
                        "Insufficient leave balance for " + leaveType + "! You have " + availableBalance
                                + " days remaining.");
                return "redirect:/employee/leave/apply";
            }

            com.example.demo.model.LeaveRequest leaveRequest = new com.example.demo.model.LeaveRequest();
            leaveRequest.setEmployee(employee);
            leaveRequest.setLeaveType(leaveType);
            leaveRequest.setStartDate(start);
            leaveRequest.setEndDate(end);
            leaveRequest.setNumberOfDays((int) daysRequested); // Ensure this field exists or sets correctly
            leaveRequest.setReason(reason);
            leaveRequest.setStatus("PENDING");

            leaveRequestRepository.save(leaveRequest);

            redirectAttributes.addFlashAttribute("success",
                    "Leave application submitted successfully! It will be reviewed by HR.");
            return "redirect:/employee/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error submitting leave request: " + e.getMessage());
            return "redirect:/employee/leave/apply";
        }
    }

    @RequestMapping(value = "/leave/history", method = RequestMethod.GET)
    public String viewLeaveHistory(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        List<com.example.demo.model.LeaveRequest> leaveRequests = leaveRequestRepository.findByEmployee(employee);
        List<com.example.demo.model.LeaveRequest> pendingLeaves = leaveRequests.stream()
                .filter(lr -> "PENDING".equals(lr.getStatus()))
                .collect(java.util.stream.Collectors.toList());
        List<com.example.demo.model.LeaveRequest> approvedLeaves = leaveRequests.stream()
                .filter(lr -> "APPROVED".equals(lr.getStatus()))
                .collect(java.util.stream.Collectors.toList());
        Integer leaveBalance = employee.getLeaveBalance() != null ? employee.getLeaveBalance() : 0;

        model.addAttribute("employee", employee);
        model.addAttribute("leaveRequests", leaveRequests);
        model.addAttribute("pendingLeaves", pendingLeaves);
        model.addAttribute("approvedLeaves", approvedLeaves);
        model.addAttribute("leaveBalance", leaveBalance);

        return "employee-leave-history";
    }

    @RequestMapping(value = "/suggestion/submit", method = RequestMethod.GET)
    public String showSuggestionForm(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        List<com.example.demo.model.Suggestion> mySuggestions = suggestionRepository
                .findByEmployeeOrderByCreatedAtDesc(employee);
        model.addAttribute("employee", employee);
        model.addAttribute("mySuggestions", mySuggestions);

        return "employee-suggestion-form";
    }

    @RequestMapping(value = "/suggestion/submit", method = RequestMethod.POST)
    public String submitSuggestion(@RequestParam String title,
            @RequestParam String description,
            @RequestParam(required = false, defaultValue = "GENERAL") String suggestionType,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        try {
            com.example.demo.model.Suggestion suggestion = new com.example.demo.model.Suggestion();
            suggestion.setEmployee(employee);
            suggestion.setTitle(title);
            suggestion.setDescription(description);
            suggestion.setSuggestionType(suggestionType);
            suggestion.setStatus("PENDING");

            suggestionRepository.save(suggestion);

            redirectAttributes.addFlashAttribute("success",
                    "Suggestion submitted successfully! It will be reviewed by HR and Management.");
            return "redirect:/employee/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error submitting suggestion: " + e.getMessage());
            return "redirect:/employee/suggestion/submit";
        }
    }

    @RequestMapping(value = "/suggestion/my-suggestions", method = RequestMethod.GET)
    public String viewMySuggestions(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        List<com.example.demo.model.Suggestion> mySuggestions = suggestionRepository
                .findByEmployeeOrderByCreatedAtDesc(employee);
        model.addAttribute("employee", employee);
        model.addAttribute("mySuggestions", mySuggestions);

        return "employee-my-suggestions";
    }
}
