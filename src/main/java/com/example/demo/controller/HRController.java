package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/hr")
public class HRController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private VisitorRepository visitorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private PayrollRepository payrollRepository;

    @Autowired
    private JobOpeningRepository jobOpeningRepository;

    @Autowired
    private CandidateRepository candidateRepository;

    @Autowired
    private com.example.demo.service.EmailService emailService;

    @Autowired
    private com.example.demo.repository.IdleIncidentRepository idleIncidentRepository;

    @Autowired
    private com.example.demo.repository.SuggestionRepository suggestionRepository;

    @Autowired
    private com.example.demo.repository.EmployeeActivityRepository employeeActivityRepository;

    @Autowired
    private com.example.demo.repository.MeetingRepository meetingRepository;

    @Autowired
    private com.example.demo.repository.NotificationRepository notificationRepository;

    @Autowired
    private com.example.demo.repository.CompanyHolidayRepository companyHolidayRepository;

    @Autowired
    private com.example.demo.service.PerformanceScoreService performanceScoreService;

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String showHRLoginForm(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user != null && "HR".equals(user.getRole())) {
            return "redirect:/hr/dashboard";
        }
        return "hr-login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String hrLogin(@RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (!"HR".equals(user.getRole())) {
                redirectAttributes.addFlashAttribute("error", "Access denied. HR login only.");
                return "redirect:/hr/login";
            }
            if (user.getPassword().equals(password)) {
                session.setAttribute("user", user);
                session.setAttribute("userName", user.getName());
                session.setAttribute("userRole", user.getRole());
                return "redirect:/hr/dashboard";
            } else {
                redirectAttributes.addFlashAttribute("error", "Invalid email or password!");
                return "redirect:/hr/login";
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Invalid email or password!");
            return "redirect:/hr/login";
        }
    }

    @RequestMapping(value = "/logout", method = RequestMethod.GET)
    public String hrLogout(HttpSession session) {
        session.invalidate();
        return "redirect:/hr/login";
    }

    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)
    public String hrDashboard(@RequestParam(required = false) String employeeSearch, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate startOfYear = today.withDayOfYear(1);

        // 1. Employee Overview
        List<Employee> allEmployees = employeeRepository.findAll();

        // Filter employees if search is provided
        List<Employee> visibleEmployees = allEmployees;
        if (employeeSearch != null && !employeeSearch.trim().isEmpty()) {
            String search = employeeSearch.toLowerCase().trim();
            visibleEmployees = allEmployees.stream()
                    .filter(e -> (e.getName() != null && e.getName().toLowerCase().contains(search)) ||
                            (e.getEmployeeId() != null && e.getEmployeeId().toLowerCase().contains(search)))
                    .collect(Collectors.toList());
            model.addAttribute("employeeSearch", employeeSearch);
        }
        long totalEmployees = allEmployees.size();
        long activeEmployees = allEmployees.stream().filter(e -> e.getIsActive() != null && e.getIsActive()).count();
        long inactiveEmployees = totalEmployees - activeEmployees;
        long newJoineesThisMonth = allEmployees.stream()
                .filter(e -> e.getJoinDate() != null &&
                        e.getJoinDate().isAfter(startOfMonth.minusDays(1)) &&
                        e.getJoinDate().isBefore(endOfMonth.plusDays(1)))
                .count();
        long employeesOnProbation = allEmployees.stream()
                .filter(e -> e.getOnProbation() != null && e.getOnProbation())
                .count();

        // Department-wise count
        Map<String, Long> departmentCount = allEmployees.stream()
                .collect(Collectors.groupingBy(Employee::getDepartment, Collectors.counting()));

        // 2. Attendance & Time Management
        List<Employee> presentEmployees = employeeRepository.findCurrentlyPresentEmployees();
        long presentCount = presentEmployees.size();
        long absentCount = totalEmployees - presentCount;

        List<Attendance> todayAttendance = attendanceRepository.findByAttendanceDate(today);
        long lateCount = todayAttendance.stream().filter(a -> "LATE".equals(a.getStatus())).count();
        long wfhRequests = allEmployees.stream()
                .filter(e -> "WORK_FROM_HOME".equals(e.getWorkMode()))
                .count();

        // Monthly attendance
        List<Attendance> monthlyAttendance = attendanceRepository.findByDateRange(startOfMonth, endOfMonth);
        Map<String, Long> attendanceStatusCount = monthlyAttendance.stream()
                .collect(Collectors.groupingBy(Attendance::getStatus, Collectors.counting()));

        // Calculate Detailed Attendance Report for Table
        List<Map<String, Object>> attendanceReport = new ArrayList<>();
        int daysPassed = today.getDayOfMonth();

        for (Employee emp : visibleEmployees) {
            Map<String, Object> report = new HashMap<>();
            report.put("id", emp.getEmployeeId()); // Visible ID
            report.put("dbId", emp.getId()); // Database ID for API calls
            report.put("name", emp.getName());

            // Today's Status
            Optional<Attendance> todayRecord = todayAttendance.stream()
                    .filter(a -> a.getEmployee().getId().equals(emp.getId()))
                    .findFirst();

            if (todayRecord.isPresent()) {
                Attendance att = todayRecord.get();
                report.put("login",
                        att.getCheckInTime() != null ? att.getCheckInTime().toLocalTime().toString().substring(0, 5)
                                : "-");
                report.put("logout",
                        att.getCheckOutTime() != null ? att.getCheckOutTime().toLocalTime().toString().substring(0, 5)
                                : "-");
            } else {
                report.put("login", "-");
                report.put("logout", "-");
            }

            // Monthly Counts
            long presentDays = monthlyAttendance.stream()
                    .filter(a -> a.getEmployee().getId().equals(emp.getId()))
                    .count();

            report.put("presentDays", presentDays);
            // Simple absent calculation: Days passed in month - Days Present.
            // Note: This includes weekends in "Absent" count if they aren't marked present.
            // For a robust system, we'd check work schedule, but this meets the immediate
            // requirement.
            report.put("absentDays", Math.max(0, daysPassed - presentDays));

            // Work Mode
            report.put("workMode", emp.getWorkMode() != null ? emp.getWorkMode() : "OFFICE");

            // Total Time Calculation
            if (todayRecord.isPresent() && todayRecord.get().getCheckInTime() != null
                    && todayRecord.get().getCheckOutTime() != null) {
                java.time.Duration duration = java.time.Duration.between(todayRecord.get().getCheckInTime(),
                        todayRecord.get().getCheckOutTime());
                long hours = duration.toHours();
                long minutes = duration.toMinutesPart();
                report.put("totalTime", String.format("%dh %dm", hours, minutes));
            } else {
                report.put("totalTime", "-");
            }

            attendanceReport.add(report);
        }
        model.addAttribute("attendanceReport", attendanceReport);

        // 3. Leave Management
        List<LeaveRequest> pendingLeaves = leaveRequestRepository.findByStatus("PENDING");
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findByStatus("APPROVED");
        List<LeaveRequest> rejectedLeaves = leaveRequestRepository.findByStatus("REJECTED");
        List<LeaveRequest> monthlyLeaves = leaveRequestRepository.findLeavesInDateRange(startOfMonth, endOfMonth);

        // Meetings
        List<Meeting> upcomingMeetings = meetingRepository.findUpcomingMeetings(LocalDateTime.now());
        model.addAttribute("meetings", upcomingMeetings);

        // 4. Task & Performance - Per Employee
        List<Task> allTasks = taskRepository.findAll();
        long totalTasks = allTasks.size();
        long completedTasks = allTasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long overdueTasks = allTasks.stream()
                .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today)
                        && !"COMPLETED".equals(t.getStatus()))
                .count();
        double taskCompletionRate = totalTasks > 0 ? (completedTasks * 100.0 / totalTasks) : 0.0;

        // Calculate task performance per employee
        Map<Employee, Map<String, Long>> employeeTaskStats = new HashMap<>();
        for (Employee emp : allEmployees) {
            List<Task> empTasks = taskRepository.findByEmployee(emp);
            Map<String, Long> stats = new HashMap<>();
            stats.put("total", (long) empTasks.size());
            stats.put("completed", empTasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count());
            stats.put("inProgress", empTasks.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count());
            stats.put("pending", empTasks.stream().filter(t -> "PENDING".equals(t.getStatus())).count());
            stats.put("cancelled", empTasks.stream().filter(t -> "CANCELLED".equals(t.getStatus())).count());

            // Calculate completion rate for this employee
            long empTotal = stats.get("total");
            long empCompleted = stats.get("completed");
            double empCompletionRate = empTotal > 0 ? (empCompleted * 100.0 / empTotal) : 0.0;
            stats.put("completionRate", (long) empCompletionRate);

            if (empTotal > 0) { // Only include employees with tasks
                employeeTaskStats.put(emp, stats);
            }
        }

        // Top performers (by task completion rate and completed tasks)
        List<Map.Entry<Employee, Map<String, Long>>> topPerformersList = employeeTaskStats.entrySet().stream()
                .sorted((e1, e2) -> {
                    // Sort by completion rate first, then by number of completed tasks
                    Long rate1 = e1.getValue().get("completionRate");
                    Long rate2 = e2.getValue().get("completionRate");
                    int rateCompare = rate2.compareTo(rate1);
                    if (rateCompare != 0)
                        return rateCompare;
                    return Long.compare(e2.getValue().get("completed"), e1.getValue().get("completed"));
                })
                .limit(10)
                .collect(Collectors.toList());

        // Create a list of employee task performance data for the view
        List<Map<String, Object>> employeeTaskPerformance = new ArrayList<>();
        for (Map.Entry<Employee, Map<String, Long>> entry : topPerformersList) {
            Map<String, Object> perfData = new HashMap<>();
            perfData.put("employee", entry.getKey());
            perfData.put("stats", entry.getValue());
            employeeTaskPerformance.add(perfData);
        }

        // 5. Recruitment & Onboarding
        List<JobOpening> openJobs = jobOpeningRepository.findByStatus("OPEN");
        List<Candidate> allCandidates = candidateRepository.findAll();
        List<Candidate> selectedCandidates = candidateRepository.findByStatus("SELECTED");
        List<Candidate> rejectedCandidates = candidateRepository.findByStatus("REJECTED");
        List<Candidate> interviewedCandidates = candidateRepository.findByStatus("INTERVIEWED");

        // 6. Payroll & Compensation
        List<Payroll> pendingPayrolls = payrollRepository.findByPayrollStatus(Payroll.PayrollStatus.GENERATED);
        List<Payroll> processedPayrolls = payrollRepository.findByPayrollStatus(Payroll.PayrollStatus.APPROVED);
        List<Payroll> monthlyPayrolls = payrollRepository.findAll();

        // 7. Notifications & Alerts
        List<Employee> expiringContracts = allEmployees.stream()
                .filter(e -> e.getProbationEndDate() != null &&
                        e.getProbationEndDate().isAfter(today) &&
                        e.getProbationEndDate().isBefore(today.plusDays(30)))
                .collect(Collectors.toList());

        // Add all to model
        model.addAttribute("userName", user.getName());
        model.addAttribute("userRole", user.getRole());

        // Employee Overview
        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("activeEmployees", activeEmployees);
        model.addAttribute("inactiveEmployees", inactiveEmployees);
        model.addAttribute("newJoineesThisMonth", newJoineesThisMonth);
        model.addAttribute("employeesOnProbation", employeesOnProbation);
        model.addAttribute("departmentCount", departmentCount);
        model.addAttribute("allEmployees", allEmployees);

        // Attendance
        model.addAttribute("presentCount", presentCount);
        model.addAttribute("absentCount", absentCount);
        model.addAttribute("lateCount", lateCount);
        model.addAttribute("wfhRequests", wfhRequests);
        model.addAttribute("todayAttendance", todayAttendance);
        model.addAttribute("monthlyAttendance", monthlyAttendance);
        model.addAttribute("attendanceStatusCount", attendanceStatusCount);
        model.addAttribute("presentEmployees", presentEmployees);

        // Leave Management
        model.addAttribute("pendingLeaves", pendingLeaves);
        model.addAttribute("approvedLeaves", approvedLeaves);
        model.addAttribute("rejectedLeaves", rejectedLeaves);
        model.addAttribute("monthlyLeaves", monthlyLeaves);

        // Task & Performance
        model.addAttribute("totalTasks", totalTasks);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("overdueTasks", overdueTasks);
        model.addAttribute("taskCompletionRate", taskCompletionRate);
        model.addAttribute("employeeTaskPerformance", employeeTaskPerformance);
        model.addAttribute("employeeTaskStats", employeeTaskStats);
        model.addAttribute("allTasks", allTasks);

        // Recruitment
        model.addAttribute("openJobs", openJobs);
        model.addAttribute("allCandidates", allCandidates);
        model.addAttribute("selectedCandidates", selectedCandidates);
        model.addAttribute("rejectedCandidates", rejectedCandidates);
        model.addAttribute("interviewedCandidates", interviewedCandidates);

        // Payroll
        model.addAttribute("pendingPayrolls", pendingPayrolls);
        model.addAttribute("processedPayrolls", processedPayrolls);
        model.addAttribute("monthlyPayrolls", monthlyPayrolls);

        List<Notification> notifications = notificationRepository
                .findByUserRecipientAndIsReadFalseOrderByCreatedAtDesc(user);

        if (pendingLeaves != null && !pendingLeaves.isEmpty()) {
            boolean hasLeaveNotifs = notifications.stream().anyMatch(n -> n.getMessage() != null && n.getMessage().contains("Leave Request"));
            if (!hasLeaveNotifs) {
                for (LeaveRequest pl : pendingLeaves) {
                    Notification n = new Notification();
                    n.setUserRecipient(user);
                    String empName = (pl.getEmployee() != null && pl.getEmployee().getName() != null) ? pl.getEmployee().getName() : "Employee";
                    n.setMessage("Pending Leave: " + empName + " applied for " + pl.getLeaveType() + " (" + pl.getNumberOfDays() + " day(s))");
                    n.setCreatedAt(pl.getCreatedAt() != null ? pl.getCreatedAt() : LocalDateTime.now());
                    n.setIsRead(false);
                    notificationRepository.save(n);
                }
                notifications = notificationRepository.findByUserRecipientAndIsReadFalseOrderByCreatedAtDesc(user);
            }
        }

        model.addAttribute("notifications", notifications);

        // Visitor stats
        List<Visitor> allVisitors = visitorRepository.findAllOrderByCheckInTimeDesc();
        List<Visitor> checkedInVisitors = visitorRepository.findByStatus("Checked In");
        model.addAttribute("totalVisitors", allVisitors.size());
        model.addAttribute("currentlyIn", checkedInVisitors.size());

        // Idle employee notifications
        List<com.example.demo.model.IdleIncident> activeIdleIncidents = idleIncidentRepository.findActiveIncidents();
        model.addAttribute("idleIncidents", activeIdleIncidents);
        model.addAttribute("idleCount", activeIdleIncidents.size());

        // Employee Suggestions
        List<com.example.demo.model.Suggestion> allSuggestions = suggestionRepository.findAllByOrderByCreatedAtDesc();
        List<com.example.demo.model.Suggestion> pendingSuggestions = suggestionRepository
                .findByStatusOrderByCreatedAtDesc("PENDING");
        List<com.example.demo.model.Suggestion> hrSuggestions = suggestionRepository
                .findBySuggestionTypeOrderByCreatedAtDesc("HR");
        model.addAttribute("allSuggestions", allSuggestions);
        model.addAttribute("pendingSuggestions", pendingSuggestions);
        model.addAttribute("hrSuggestions", hrSuggestions);
        model.addAttribute("suggestionCount", pendingSuggestions.size());

        // Non-Productive Activities (Last 24 hours)
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
        List<com.example.demo.model.EmployeeActivity> nonProductiveActivities = employeeActivityRepository
                .findNonProductiveActivities(last24Hours);
        List<com.example.demo.model.EmployeeActivity> gamingActivities = employeeActivityRepository
                .findByActivityTypeAndStartedAtAfter("GAMING", last24Hours);
        List<com.example.demo.model.EmployeeActivity> socialMediaActivities = employeeActivityRepository
                .findByActivityTypeAndStartedAtAfter("SOCIAL_MEDIA", last24Hours);
        List<com.example.demo.model.EmployeeActivity> videoActivities = employeeActivityRepository
                .findByActivityTypeAndStartedAtAfter("VIDEO", last24Hours);

        // Group by employee
        Map<Employee, List<com.example.demo.model.EmployeeActivity>> nonProductiveByEmployee = nonProductiveActivities
                .stream()
                .collect(Collectors.groupingBy(com.example.demo.model.EmployeeActivity::getEmployee));

        // Calculate total non-productive time per employee
        Map<Employee, Long> nonProductiveTimeByEmployee = new HashMap<>();
        for (Map.Entry<Employee, List<com.example.demo.model.EmployeeActivity>> entry : nonProductiveByEmployee
                .entrySet()) {
            long totalMinutes = entry.getValue().stream()
                    .mapToLong(a -> a.getDurationMinutes() != null ? a.getDurationMinutes() : 0)
                    .sum();
            nonProductiveTimeByEmployee.put(entry.getKey(), totalMinutes);
        }

        model.addAttribute("nonProductiveActivities", nonProductiveActivities);
        model.addAttribute("gamingActivities", gamingActivities);
        model.addAttribute("socialMediaActivities", socialMediaActivities);
        model.addAttribute("videoActivities", videoActivities);
        model.addAttribute("nonProductiveByEmployee", nonProductiveByEmployee);
        model.addAttribute("nonProductiveTimeByEmployee", nonProductiveTimeByEmployee);
        model.addAttribute("nonProductiveCount", nonProductiveActivities.size());

        // Company Holidays
        List<com.example.demo.model.CompanyHoliday> companyHolidays = companyHolidayRepository.findAll();
        // Sort holidays by date
        companyHolidays.sort(Comparator.comparing(com.example.demo.model.CompanyHoliday::getDate));
        model.addAttribute("companyHolidays", companyHolidays);

        return "hr-dashboard";
    }

    @RequestMapping(value = "/performance", method = RequestMethod.GET)
    public String hrPerformance(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }
        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(e -> e.getIsActive() == null || e.getIsActive())
                .collect(java.util.stream.Collectors.toList());
        List<com.example.demo.model.PerformanceRecord> records = new java.util.ArrayList<>();
        for (Employee emp : employees) {
            com.example.demo.model.PerformanceRecord rec = performanceScoreService.getOrCreateWeeklyRecord(emp);
            records.add(rec);
        }
        records.sort((a, b) -> Double.compare(
                (b.getCompositeScore() != null ? b.getCompositeScore() : 0),
                (a.getCompositeScore() != null ? a.getCompositeScore() : 0)));
        List<com.example.demo.model.PerformanceRecord> topPerformers = records.stream().limit(10).collect(java.util.stream.Collectors.toList());
        List<com.example.demo.model.PerformanceRecord> lowPerformers = records.stream()
                .skip(Math.max(0, records.size() - 10))
                .collect(java.util.stream.Collectors.toList());
        java.util.Collections.reverse(lowPerformers);
        model.addAttribute("userName", user.getName());
        model.addAttribute("orgPerformance", records);
        model.addAttribute("topPerformers", topPerformers);
        model.addAttribute("lowPerformers", lowPerformers);
        return "hr-performance";
    }

    @RequestMapping(value = "/holiday/add", method = RequestMethod.POST)
    public String addHoliday(@RequestParam(required = false) String date,
                             @RequestParam(required = false) String name,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }
        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Holiday name is required.");
            redirectAttributes.addAttribute("activeTab", "holiday");
            return "redirect:/hr/dashboard";
        }
        if (date == null || date.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Holiday date is required.");
            redirectAttributes.addAttribute("activeTab", "holiday");
            return "redirect:/hr/dashboard";
        }

        LocalDate holidayDate;
        try {
            holidayDate = LocalDate.parse(date.trim());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Invalid date format. Please use YYYY-MM-DD.");
            redirectAttributes.addAttribute("activeTab", "holiday");
            return "redirect:/hr/dashboard";
        }

        List<com.example.demo.model.CompanyHoliday> existing = companyHolidayRepository.findAll();
        boolean dateExists = existing != null && existing.stream()
                .anyMatch(h -> h.getDate() != null && h.getDate().equals(holidayDate));
        if (dateExists) {
            redirectAttributes.addFlashAttribute("error", "A company holiday already exists on " + holidayDate + ".");
            redirectAttributes.addAttribute("activeTab", "holiday");
            return "redirect:/hr/dashboard";
        }

        boolean nameExists = existing != null && existing.stream()
                .anyMatch(h -> h.getName() != null && h.getName().equalsIgnoreCase(name.trim())
                        && h.getDate() != null && h.getDate().getYear() == holidayDate.getYear());
        if (nameExists) {
            redirectAttributes.addFlashAttribute("error", "A holiday named '" + name.trim() + "' already exists for year " + holidayDate.getYear() + ".");
            redirectAttributes.addAttribute("activeTab", "holiday");
            return "redirect:/hr/dashboard";
        }

        try {
            com.example.demo.model.CompanyHoliday holiday = new com.example.demo.model.CompanyHoliday();
            holiday.setDate(holidayDate);
            holiday.setName(name.trim());
            companyHolidayRepository.save(holiday);
            redirectAttributes.addFlashAttribute("success", "Holiday added successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error adding holiday: " + e.getMessage());
        }
        redirectAttributes.addAttribute("activeTab", "holiday");
        return "redirect:/hr/dashboard";
    }

    @RequestMapping(value = "/holiday/delete/{id}", method = RequestMethod.POST)
    public String deleteHoliday(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }
        try {
            companyHolidayRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Holiday removed successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error removing holiday: " + e.getMessage());
        }
        redirectAttributes.addAttribute("activeTab", "holiday");
        return "redirect:/hr/dashboard";
    }

    @RequestMapping(value = "/leave/approve/{id}", method = RequestMethod.POST)
    public String approveLeave(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        Optional<LeaveRequest> leaveRequestOpt = leaveRequestRepository.findById(id);
        if (!leaveRequestOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Leave request not found!");
            return "redirect:/hr/dashboard";
        }

        LeaveRequest leaveRequest = leaveRequestOpt.get();
        if (!"PENDING".equals(leaveRequest.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "This leave request has already been processed!");
            return "redirect:/hr/dashboard";
        }

        // Update leave request status
        leaveRequest.setStatus("APPROVED");
        leaveRequest.setApprovedBy(user.getName());
        leaveRequest.setApprovalDate(LocalDateTime.now());
        leaveRequestRepository.save(leaveRequest);

        // Leave was already deducted when requested by employee.
        Employee employee = leaveRequest.getEmployee();

        // In-app notification for employee
        try {
            Notification notif = new Notification();
            notif.setRecipient(employee);
            notif.setMessage("Leave Approved: Your " + leaveRequest.getLeaveType() + " leave request for " + leaveRequest.getNumberOfDays() + " day(s) (" + leaveRequest.getStartDate() + " to " + leaveRequest.getEndDate() + ") has been approved.");
            notif.setCreatedAt(LocalDateTime.now());
            notif.setIsRead(false);
            notificationRepository.save(notif);
        } catch (Exception notifEx) {
            System.err.println("Failed to create leave approval notification: " + notifEx.getMessage());
        }

        // Send email notification
        try {
            emailService.sendLeaveApprovalNotification(
                    employee.getEmail(),
                    employee.getName(),
                    leaveRequest.getLeaveType(),
                    leaveRequest.getStartDate(),
                    leaveRequest.getEndDate(),
                    leaveRequest.getNumberOfDays());
        } catch (Exception e) {
            System.err.println("Failed to send leave approval email: " + e.getMessage());
        }

        redirectAttributes.addFlashAttribute("success",
                "Leave request approved successfully! Notification sent to employee.");
        return "redirect:/hr/dashboard";
    }

    @RequestMapping(value = "/leave/reject/{id}", method = RequestMethod.POST)
    public String rejectLeave(@PathVariable Long id,
            @RequestParam(required = false) String rejectionReason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        Optional<LeaveRequest> leaveRequestOpt = leaveRequestRepository.findById(id);
        if (!leaveRequestOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Leave request not found!");
            return "redirect:/hr/dashboard";
        }

        LeaveRequest leaveRequest = leaveRequestOpt.get();
        if (!"PENDING".equals(leaveRequest.getStatus())) {
            redirectAttributes.addFlashAttribute("error", "This leave request has already been processed!");
            return "redirect:/hr/dashboard";
        }

        // Update leave request status
        leaveRequest.setStatus("REJECTED");
        leaveRequest.setApprovedBy(user.getName());
        leaveRequest.setApprovalDate(LocalDateTime.now());
        leaveRequest.setRejectionReason(rejectionReason);
        leaveRequestRepository.save(leaveRequest);

        // Refund employee leave balance on rejection
        Employee employee = leaveRequest.getEmployee();
        String leaveType = leaveRequest.getLeaveType();
        int daysToRefund = leaveRequest.getNumberOfDays() != null ? leaveRequest.getNumberOfDays() : 0;

        if ("PAID".equals(leaveType)) {
            int current = employee.getPaidLeave() != null ? employee.getPaidLeave() : 0;
            employee.setPaidLeave(current + daysToRefund);
        } else if ("SICK".equals(leaveType)) {
            int current = employee.getSickLeave() != null ? employee.getSickLeave() : 0;
            employee.setSickLeave(current + daysToRefund);
        } else if ("CASUAL".equals(leaveType)) {
            int current = employee.getCasualLeave() != null ? employee.getCasualLeave() : 0;
            employee.setCasualLeave(current + daysToRefund);
        } else if (employee.getCustomLeaves() != null && employee.getCustomLeaves().containsKey(leaveType)) {
            int current = employee.getCustomLeaves().get(leaveType);
            employee.getCustomLeaves().put(leaveType, current + daysToRefund);
        }

        Integer currentTotal = employee.getLeaveBalance() != null ? employee.getLeaveBalance() : 0;
        employee.setLeaveBalance(currentTotal + daysToRefund);
        employeeRepository.save(employee);

        // In-app notification for employee
        try {
            Notification notif = new Notification();
            notif.setRecipient(employee);
            notif.setMessage("Leave Rejected: Your " + leaveRequest.getLeaveType() + " leave request was rejected." + (rejectionReason != null && !rejectionReason.isBlank() ? " Reason: " + rejectionReason : ""));
            notif.setCreatedAt(LocalDateTime.now());
            notif.setIsRead(false);
            notificationRepository.save(notif);
        } catch (Exception notifEx) {
            System.err.println("Failed to create leave rejection notification: " + notifEx.getMessage());
        }

        // Send email notification
        try {
            emailService.sendLeaveRejectionNotification(
                    employee.getEmail(),
                    employee.getName(),
                    leaveRequest.getLeaveType(),
                    leaveRequest.getStartDate(),
                    leaveRequest.getEndDate(),
                    rejectionReason);
        } catch (Exception e) {
            System.err.println("Failed to send leave rejection email: " + e.getMessage());
        }

        redirectAttributes.addFlashAttribute("success", "Leave request rejected. Notification sent to employee.");
        return "redirect:/hr/dashboard";
    }

    @RequestMapping(value = "/recruitment/candidate/add", method = RequestMethod.GET)
    public String showAddCandidateForm(@RequestParam(required = false) Long jobId, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        List<JobOpening> openJobs = jobOpeningRepository.findByStatus("OPEN");
        model.addAttribute("openJobs", openJobs);
        model.addAttribute("selectedJobId", jobId);

        return "hr-add-candidate";
    }

    @RequestMapping(value = "/recruitment/candidate/add", method = RequestMethod.POST)
    public String addCandidate(@RequestParam String name,
            @RequestParam String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String dateOfBirth,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) Long jobOpeningId,
            @RequestParam(required = false) String highestQualification,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) String collegeUniversity,
            @RequestParam(required = false) Integer yearOfPassing,
            @RequestParam(required = false) String primarySkills,
            @RequestParam(required = false) String secondarySkills,
            @RequestParam(required = false) Double totalExperience,
            @RequestParam(required = false) Double relevantExperience,
            @RequestParam(required = false) String portfolioLink,
            @RequestParam(required = false) String status,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        try {
            Candidate candidate = new Candidate();
            candidate.setName(name);
            candidate.setEmail(email);
            candidate.setPhone(phone);
            candidate.setGender(gender);

            if (dateOfBirth != null && !dateOfBirth.isEmpty()) {
                candidate.setDateOfBirth(LocalDate.parse(dateOfBirth));
            }

            candidate.setJobTitle(jobTitle);
            candidate.setDepartment(department);
            candidate.setEmploymentType(employmentType);

            if (jobOpeningId != null) {
                Optional<JobOpening> jobOpt = jobOpeningRepository.findById(jobOpeningId);
                jobOpt.ifPresent(candidate::setJobOpening);
            }

            candidate.setHighestQualification(highestQualification);
            candidate.setSpecialization(specialization);
            candidate.setCollegeUniversity(collegeUniversity);
            candidate.setYearOfPassing(yearOfPassing);
            candidate.setPrimarySkills(primarySkills);
            candidate.setSecondarySkills(secondarySkills);
            candidate.setTotalExperience(totalExperience);
            candidate.setRelevantExperience(relevantExperience);
            candidate.setPortfolioLink(portfolioLink);
            candidate.setStatus(status != null ? status : "APPLIED");

            candidateRepository.save(candidate);

            redirectAttributes.addFlashAttribute("success", "Candidate added successfully!");
            return "redirect:/hr/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error adding candidate: " + e.getMessage());
            return "redirect:/hr/recruitment/candidate/add";
        }
    }

    @RequestMapping(value = "/recruitment/candidate/view/{id}", method = RequestMethod.GET)
    public String viewCandidate(@PathVariable Long id, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        Optional<Candidate> candidateOpt = candidateRepository.findById(id);
        if (!candidateOpt.isPresent()) {
            return "redirect:/hr/dashboard";
        }

        model.addAttribute("candidate", candidateOpt.get());
        return "hr-view-candidate";
    }

    @RequestMapping(value = "/recruitment/candidate/edit/{id}", method = RequestMethod.GET)
    public String editCandidate(@PathVariable Long id, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        Optional<Candidate> candidateOpt = candidateRepository.findById(id);
        if (!candidateOpt.isPresent()) {
            return "redirect:/hr/dashboard";
        }

        List<JobOpening> openJobs = jobOpeningRepository.findByStatus("OPEN");
        model.addAttribute("candidate", candidateOpt.get());
        model.addAttribute("openJobs", openJobs);

        return "hr-edit-candidate";
    }

    @RequestMapping(value = "/recruitment/candidate/update/{id}", method = RequestMethod.POST)
    public String updateCandidate(@PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String interviewDate,
            @RequestParam(required = false) String interviewTime,
            @RequestParam(required = false) String interviewerName,
            @RequestParam(required = false) String interviewRound,
            @RequestParam(required = false) String interviewFeedback,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        Optional<Candidate> candidateOpt = candidateRepository.findById(id);
        if (!candidateOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Candidate not found!");
            return "redirect:/hr/dashboard";
        }

        Candidate candidate = candidateOpt.get();

        if (status != null) {
            candidate.setStatus(status);
        }

        if (interviewDate != null && !interviewDate.isEmpty()) {
            try {
                // Handle datetime-local input (format: yyyy-MM-ddTHH:mm)
                if (interviewDate.contains("T")) {
                    candidate.setInterviewDate(LocalDateTime.parse(interviewDate));
                } else {
                    // Fallback for date-only input
                    LocalDateTime interviewDateTime = LocalDateTime
                            .parse(interviewDate + "T" + (interviewTime != null ? interviewTime : "10:00"));
                    candidate.setInterviewDate(interviewDateTime);
                }
            } catch (Exception e) {
                System.err.println("Error parsing interview date: " + e.getMessage());
            }
        }

        candidate.setInterviewTime(interviewTime);
        candidate.setInterviewerName(interviewerName);
        candidate.setInterviewRound(interviewRound);
        candidate.setInterviewFeedback(interviewFeedback);

        candidateRepository.save(candidate);

        redirectAttributes.addFlashAttribute("success", "Candidate updated successfully!");
        return "redirect:/hr/dashboard";
    }

    @RequestMapping(value = "/recruitment/job/create", method = RequestMethod.GET)
    public String showCreateJobForm(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }
        return "hr-create-job";
    }

    @RequestMapping(value = "/recruitment/job/create", method = RequestMethod.POST)
    public String createJobOpening(@RequestParam String title,
            @RequestParam String department,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String requirements,
            @RequestParam(required = false) Integer numberOfPositions,
            @RequestParam(required = false) String experienceRequired,
            @RequestParam(required = false) String closingDate,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        try {
            JobOpening jobOpening = new JobOpening();
            jobOpening.setTitle(title);
            jobOpening.setDepartment(department);
            jobOpening.setDescription(description);
            jobOpening.setRequirements(requirements);
            jobOpening.setNumberOfPositions(numberOfPositions != null ? numberOfPositions : 1);
            jobOpening.setExperienceRequired(experienceRequired);
            jobOpening.setStatus("OPEN");

            if (closingDate != null && !closingDate.isEmpty()) {
                jobOpening.setClosingDate(LocalDate.parse(closingDate));
            }

            jobOpeningRepository.save(jobOpening);

            redirectAttributes.addFlashAttribute("success", "Job opening created successfully!");
            return "redirect:/hr/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating job opening: " + e.getMessage());
            return "redirect:/hr/recruitment/job/create";
        }
    }

    @RequestMapping(value = "/api/payroll/lop-calc", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> calculateLOP(
            @RequestParam Long employeeId,
            @RequestParam Integer month,
            @RequestParam Integer year) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<Employee> empOpt = employeeRepository.findById(employeeId);
            if (!empOpt.isPresent()) {
                response.put("success", false);
                response.put("message", "Employee not found");
                return response;
            }
            Employee employee = empOpt.get();

            // 1. Get Monthly Quota (assigned leaves) from paidLeave field
            int allowedLeaves = employee.getPaidLeave() != null ? employee.getPaidLeave() : 0;

            // 2. Calculate Taken Leaves (PAID type only)
            LocalDate startOfMonth = LocalDate.of(year, month, 1);
            LocalDate endOfMonth = java.time.YearMonth.of(year, month).atEndOfMonth();

            // Fetch all approved leaves for employee
            List<LeaveRequest> allApprovedLeaves = leaveRequestRepository.findByEmployeeAndStatus(employee, "APPROVED");

            double takenLeaves = allApprovedLeaves.stream()
                    .filter(l -> "PAID".equals(l.getLeaveType()))
                    .mapToDouble(l -> {
                        LocalDate start = l.getStartDate().isBefore(startOfMonth) ? startOfMonth : l.getStartDate();
                        LocalDate end = l.getEndDate().isAfter(endOfMonth) ? endOfMonth : l.getEndDate();

                        if (start.isAfter(end))
                            return 0;
                        return java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
                    })
                    .sum();

            // 3. Calculate LOP
            double lopDays = Math.max(0, takenLeaves - allowedLeaves);

            // 4. Calculate Amount
            // 4. Calculate Amount
            Double baseSalary = employee.getSalary() != null ? employee.getSalary() : 0.0;
            int daysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth();

            // Simple calculation
            double perDay = baseSalary / daysInMonth; // Assuming 30 days or actual month days? Using actual.
            double lopAmount = perDay * lopDays;

            response.put("success", true);
            response.put("lopDays", lopDays);
            response.put("lopAmount", Math.round(lopAmount * 100.0) / 100.0);
            response.put("daysInMonth", daysInMonth);

            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error calculating LOP: " + e.getMessage());
            return response;
        }
    }

    @RequestMapping(value = "/payroll/send/{id}", method = RequestMethod.POST)
    public String sendPayslip(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        Optional<Payroll> payrollOpt = payrollRepository.findById(id);
        if (payrollOpt.isPresent()) {
            Payroll payroll = payrollOpt.get();
            payroll.setPayrollStatus(Payroll.PayrollStatus.SENT);
            payrollRepository.save(payroll);

            // Optional: Send email notification here
            // emailService.sendPayslipNotification(payroll.getEmployee().getEmail(), ...);

            redirectAttributes.addFlashAttribute("success", "Payslip sent to employee successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Payroll record not found.");
        }
        return "redirect:/hr/dashboard";
    }

    @RequestMapping(value = "/payroll/add", method = RequestMethod.GET)
    public String showAddPayrollForm(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        // Get only active employees for payroll
        List<Employee> allEmployees = employeeRepository.findAll()
                .stream()
                .filter(emp -> emp.getIsActive() == null || emp.getIsActive())
                .collect(Collectors.toList());
        model.addAttribute("employees", allEmployees);
        System.out.println("Loaded " + allEmployees.size() + " active employees for payroll form");

        return "hr-add-payroll";
    }

    @RequestMapping(value = "/payroll/add", method = RequestMethod.POST)
    public String addPayroll(@RequestParam Long employeeId,
            @RequestParam Integer month,
            @RequestParam Integer year,
            @RequestParam Double baseSalary,
            @RequestParam(required = false) Double lopDeduction,
            @RequestParam(required = false) String status,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        try {
            Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
            if (!employeeOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Employee not found!");
                return "redirect:/hr/payroll/add";
            }

            // Check if payroll already exists for this month/year
            List<Payroll> existing = payrollRepository.findByMonthAndYear(month, year);
            boolean alreadyExists = existing.stream().anyMatch(p -> p.getEmployee().getId().equals(employeeId));
            if (alreadyExists) {
                redirectAttributes.addFlashAttribute("error",
                        "Payroll already exists for this employee for " + month + "/" + year);
                return "redirect:/hr/payroll/add";
            }

            Payroll payroll = new Payroll();
            payroll.setEmployee(employeeOpt.get());
            payroll.setMonth(month);
            payroll.setYear(year);
            // Gross Salary matches Base Salary for now as per simple requirement, unless
            // user adds components.
            // Image has gross_salary separate, but typically Gross = Base + Allowances.
            // HR Add form only asks for Base Salary (and others removed as per plan).
            // We will set Gross Salary = Base Salary.
            payroll.setGrossSalary(baseSalary);

            double lop = lopDeduction != null ? lopDeduction : 0.0;
            payroll.setLopDeduction(lop);

            // Fixed deductions? Image says "PF + Tax + LOP".
            // We'll calculate a simple 10% tax for demo or 0 if not provided.
            // For now, let's assume total_deductions = LOP as user only mentioned LOP in
            // request "i need base salary feature... with lop_deduction".
            // But image says total_deductions = PF + Tax + LOP.
            double pf = 0.0; // Placeholder
            double tax = 0.0; // Placeholder
            double totalDeductions = pf + tax + lop;

            payroll.setTotalDeductions(totalDeductions);

            double net = baseSalary - totalDeductions;
            payroll.setNetPay(net);

            payroll.setPayrollStatus(Payroll.PayrollStatus.GENERATED);
            payroll.setGeneratedOn(new java.util.Date());

            Payroll savedPayroll = payrollRepository.save(payroll);
            System.out.println("Payroll saved with ID: " + savedPayroll.getId());

            redirectAttributes.addFlashAttribute("success",
                    "Payroll added successfully! Net Pay: $" + String.format("%.2f", net));
            return "redirect:/hr/dashboard";
        } catch (Exception e) {
            System.err.println("Error adding payroll: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error adding payroll: " + e.getMessage());
            return "redirect:/hr/payroll/add";
        }
    }

    @RequestMapping(value = "/payroll/view/{id}", method = RequestMethod.GET)
    public String viewPayroll(@PathVariable Long id, Model model, HttpSession session,
            RedirectAttributes redirectAttributes) {
        return generatePayslip(id, model, session, redirectAttributes);
    }

    @RequestMapping(value = "/payroll/edit/{id}", method = RequestMethod.GET)
    public String editPayroll(@PathVariable Long id, Model model, HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }
        Optional<Payroll> payrollOpt = payrollRepository.findById(id);
        if (payrollOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Payroll not found.");
            return "redirect:/hr/dashboard";
        }
        model.addAttribute("payroll", payrollOpt.get());
        return "hr-edit-payroll";
    }

    @RequestMapping(value = "/payroll/update", method = RequestMethod.POST)
    public String updatePayroll(@RequestParam Long id,
            @RequestParam Double grossSalary,
            @RequestParam Double lopDeduction,
            @RequestParam Double totalDeductions,
            @RequestParam Double netPay,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        Optional<Payroll> payrollOpt = payrollRepository.findById(id);
        if (payrollOpt.isPresent()) {
            Payroll payroll = payrollOpt.get();
            payroll.setGrossSalary(grossSalary);
            payroll.setLopDeduction(lopDeduction);
            payroll.setTotalDeductions(totalDeductions);
            payroll.setNetPay(netPay);
            payrollRepository.save(payroll);
            redirectAttributes.addFlashAttribute("success", "Payroll updated successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Payroll record not found.");
        }
        return "redirect:/hr/dashboard";
    }

    @RequestMapping(value = "/payroll/payslip/{id}", method = RequestMethod.GET)
    public String generatePayslip(@PathVariable Long id, Model model, HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        try {
            Optional<Payroll> payrollOpt = payrollRepository.findById(id);
            if (!payrollOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Payroll not found!");
                return "redirect:/hr/dashboard";
            }

            Payroll payroll = payrollOpt.get();

            // Ensure employee is loaded
            if (payroll.getEmployee() == null) {
                redirectAttributes.addFlashAttribute("error", "Employee information not found for this payroll!");
                return "redirect:/hr/dashboard";
            }

            model.addAttribute("payroll", payroll);
            return "hr-payslip";
        } catch (Exception e) {
            System.err.println("Error generating payslip: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error generating payslip: " + e.getMessage());
            return "redirect:/hr/dashboard";
        }
    }

    @RequestMapping(value = "/api/payroll/base-salary/{id}", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getEmployeeBaseSalary(@PathVariable long id, HttpSession session) {
        System.out.println("DEBUG: Fetching base salary for Employee ID: " + id);
        User user = (User) session.getAttribute("user");
        Map<String, Object> response = new HashMap<>();

        if (user == null || !"HR".equals(user.getRole())) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return response;
        }

        try {
            Optional<Employee> employeeOpt = employeeRepository.findById(id);
            if (employeeOpt.isPresent()) {
                Employee emp = employeeOpt.get();
                System.out
                        .println("DEBUG: Employee found: " + emp.getName() + ", Salary in Profile: " + emp.getSalary());

                // 1. Check Employee Profile Salary (Admin Set)
                if (emp.getSalary() != null && emp.getSalary() > 0) {
                    response.put("success", true);
                    response.put("baseSalary", emp.getSalary());
                    return response;
                }

                // 2. Fallback: Fetch by History (Previous Payroll)
                System.out.println("DEBUG: Fetching payrolls for Employee ID: " + id);
                List<Payroll> payrolls = payrollRepository.findByEmployeeOrderByYearDescMonthDesc(emp);
                System.out
                        .println("DEBUG: Found " + (payrolls != null ? payrolls.size() : "null") + " payroll records.");

                if (payrolls != null && !payrolls.isEmpty()) {
                    Double latestBaseSalary = payrolls.get(0).getGrossSalary(); // Assuming Gross was Base
                    System.out.println("DEBUG: Latest base salary from payroll: " + latestBaseSalary);

                    response.put("success", true);
                    response.put("baseSalary", latestBaseSalary);
                } else {
                    System.out.println("DEBUG: No payroll records found.");
                    response.put("success", true); // Soft success
                    response.put("baseSalary", 0.0);
                    response.put("message", "No base salary set");
                }
            } else {
                response.put("success", false);
                response.put("message", "Employee not found");
            }
        } catch (Exception e) {
            System.err.println("ERROR in getEmployeeBaseSalary: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }

        return response;
    }

    @RequestMapping(value = "/payroll/generate-all", method = RequestMethod.GET)
    public String generateAllPayslips(HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        List<Payroll> pendingPayrolls = payrollRepository.findByPayrollStatus(Payroll.PayrollStatus.GENERATED);
        for (Payroll payroll : pendingPayrolls) {
            payroll.setPayrollStatus(Payroll.PayrollStatus.APPROVED);
            payrollRepository.save(payroll);

            if (payroll.getEmployee() != null) {
                try {
                    Notification notif = new Notification();
                    notif.setRecipient(payroll.getEmployee());
                    String periodStr = (payroll.getMonth() != null && payroll.getYear() != null)
                            ? (payroll.getMonth() + "/" + payroll.getYear()) : "this month";
                    notif.setMessage("New Payslip Available: Your payslip for " + periodStr + " is now available to view.");
                    notif.setCreatedAt(LocalDateTime.now());
                    notif.setIsRead(false);
                    notificationRepository.save(notif);
                } catch (Exception ignored) {}
            }
        }
        redirectAttributes.addFlashAttribute("success", "All pending payslips generated successfully!");
        return "redirect:/hr/dashboard";
    }

    @RequestMapping(value = "/api/employees/{id}", method = RequestMethod.GET)
    @ResponseBody
    public Employee getEmployeeDetails(@PathVariable long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return null;
        }
        Employee employee = employeeRepository.findById(id).orElse(null);
        if (employee != null) {
            System.out.println("HR fetching employee: " + employee.getName() + ", Salary: " + employee.getSalary());
        } else {
            System.out.println("HR fetching employee: Not Found for ID " + id);
        }
        return employee;
    }

    @RequestMapping(value = "/suggestion/respond/{id}", method = RequestMethod.POST)
    public String respondToSuggestion(@PathVariable long id,
            @RequestParam String response,
            @RequestParam String status,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        try {
            Optional<com.example.demo.model.Suggestion> suggestionOpt = suggestionRepository.findById(id);
            if (!suggestionOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Suggestion not found!");
                return "redirect:/hr/dashboard";
            }

            com.example.demo.model.Suggestion suggestion = suggestionOpt.get();
            suggestion.setResponse(response);
            suggestion.setStatus(status);
            suggestion.setRespondedBy(user.getName());
            suggestion.setRespondedAt(LocalDateTime.now());

            suggestionRepository.save(suggestion);

            redirectAttributes.addFlashAttribute("success", "Response submitted successfully!");
            return "redirect:/hr/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error responding to suggestion: " + e.getMessage());
            return "redirect:/hr/dashboard";
        }
    }

    @RequestMapping(value = "/meeting/schedule/employee", method = RequestMethod.GET)
    public String showScheduleEmployeeMeetingForm(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        List<Employee> employees = employeeRepository.findAll();
        model.addAttribute("employees", employees);
        model.addAttribute("meeting", new Meeting());
        return "hr-meeting-schedule-employee";
    }

    @RequestMapping(value = "/meeting/schedule/manager", method = RequestMethod.GET)
    public String showScheduleManagerMeetingForm(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        List<User> managers = userRepository.findByRole("MANAGER");
        model.addAttribute("managers", managers);
        model.addAttribute("meeting", new Meeting());
        return "hr-meeting-schedule-manager";
    }

    @RequestMapping(value = "/meeting/create", method = RequestMethod.POST)
    public String createHRMeeting(@ModelAttribute Meeting meeting,
            @RequestParam(required = false) List<Long> employeeIds,
            @RequestParam(required = false) List<Long> managerIds,
            @RequestParam(required = false) Boolean isEveryone,
            @RequestParam(required = false) String targetType,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        String returnUrl = "MANAGER".equals(targetType) ? "redirect:/hr/meeting/schedule/manager" : "redirect:/hr/meeting/schedule/employee";

        if (meeting.getTitle() == null || meeting.getTitle().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Meeting title is required.");
            return returnUrl;
        }

        if (meeting.getLocation() == null || meeting.getLocation().trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Location / Meeting link is required.");
            return returnUrl;
        }

        if (meeting.getStartTime() == null) {
            redirectAttributes.addFlashAttribute("error", "Start time is required.");
            return returnUrl;
        }

        if (meeting.getStartTime().isBefore(LocalDateTime.now().minusMinutes(10))) {
            redirectAttributes.addFlashAttribute("error", "Meeting start time cannot be in the past.");
            return returnUrl;
        }

        if (meeting.getEndTime() != null && !meeting.getEndTime().isAfter(meeting.getStartTime())) {
            redirectAttributes.addFlashAttribute("error", "End time must be after start time.");
            return returnUrl;
        }

        if (!Boolean.TRUE.equals(isEveryone)) {
            if ("EMPLOYEE".equals(targetType) && (employeeIds == null || employeeIds.isEmpty())) {
                redirectAttributes.addFlashAttribute("error", "Please select at least one employee or choose 'Everyone'.");
                return returnUrl;
            }
            if ("MANAGER".equals(targetType) && (managerIds == null || managerIds.isEmpty())) {
                redirectAttributes.addFlashAttribute("error", "Please select at least one manager or choose 'Everyone'.");
                return returnUrl;
            }
        }

        if ("EMPLOYEE".equals(targetType)) {
            if (Boolean.TRUE.equals(isEveryone)) {
                meeting.setIsAllEmployees(true);
                List<Employee> allEmployees = employeeRepository.findAll();
                // We don't necessarily need to link all employees to the meeting object if we
                // just want notifications,
                // but typically having them in the list is good.
                // However, for large companies "All Employees" might be too many for
                // ManyToMany.
                // But for this requirement, I will just send notifications.
                // NOTE: Meeting model has `isAllEmployees` flag, so maybe we rely on that for
                // "View My Meetings".
                // BUT for notifications, we need individual records unless we have a "Global
                // Notification" system.
                // The current Notification model is per-recipient. So I must create N
                // notifications.

            } else if (employeeIds != null && !employeeIds.isEmpty()) {
                List<Employee> selected = employeeRepository.findAllById(employeeIds);
                meeting.setEmployeeParticipants(selected);
            }
        } else if ("MANAGER".equals(targetType)) {
            if (Boolean.TRUE.equals(isEveryone)) {
                meeting.setIsAllManagers(true);
            } else if (managerIds != null && !managerIds.isEmpty()) {
                List<User> selected = userRepository.findAllById(managerIds);
                meeting.setManagerParticipants(selected);
            }
        }

        Meeting savedMeeting = meetingRepository.save(meeting);

        // Create Notifications for Employees
        if ("EMPLOYEE".equals(targetType)) {
            List<Employee> recipients = new ArrayList<>();
            if (Boolean.TRUE.equals(isEveryone)) {
                recipients = employeeRepository.findAll();
            } else if (savedMeeting.getEmployeeParticipants() != null) {
                recipients = savedMeeting.getEmployeeParticipants();
            }

            for (Employee emp : recipients) {
                Notification notification = new Notification();
                notification.setRecipient(emp);
                notification.setMessage("New Meeting: " + savedMeeting.getTitle());
                notification.setMeetingId(savedMeeting.getId());
                notification.setCreatedAt(LocalDateTime.now());
                notification.setIsRead(false);
                notificationRepository.save(notification);
            }
        } else if ("MANAGER".equals(targetType)) {
            List<User> recipients = new ArrayList<>();
            if (Boolean.TRUE.equals(isEveryone)) {
                recipients = userRepository.findByRole("MANAGER");
            } else if (savedMeeting.getManagerParticipants() != null) {
                recipients = savedMeeting.getManagerParticipants();
            }

            for (User mgr : recipients) {
                Notification notification = new Notification();
                notification.setUserRecipient(mgr);
                notification.setMessage("New Meeting: " + savedMeeting.getTitle());
                notification.setMeetingId(savedMeeting.getId());
                notification.setCreatedAt(LocalDateTime.now());
                notification.setIsRead(false);
                notificationRepository.save(notification);
            }
        }

        // Notify All HRs (Requirement: "notify all hrs if the meeting is created")
        List<User> allHrs = userRepository.findByRole("HR");
        for (User hr : allHrs) {
            // Avoid duplicate notification if the HR created it (optional, but user said
            // "all hrs")
            // But usually the creator knows. However, distinct requirements say "notify all
            // hrs".
            // I'll skip the creator to be safe/clean, or just send to all.
            // Let's send to all to be safe with "all hrs".
            // Actually, if I am the creator, I don't need a notification.
            if (hr.getId().equals(user.getId()))
                continue;

            Notification notification = new Notification();
            notification.setUserRecipient(hr);
            notification.setMessage("New HR Meeting Scheduled: " + savedMeeting.getTitle());
            notification.setMeetingId(savedMeeting.getId());
            notification.setCreatedAt(LocalDateTime.now());
            notification.setIsRead(false);
            notificationRepository.save(notification);
        }
        redirectAttributes.addFlashAttribute("success", "Meeting scheduled successfully!");
        return "redirect:/hr/dashboard";
    }

    @RequestMapping(value = "/notification/read/{id}", method = RequestMethod.POST)
    @ResponseBody
    public org.springframework.http.ResponseEntity<String> markNotificationAsRead(@PathVariable long id,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return org.springframework.http.ResponseEntity.badRequest().body("Unauthorized");
        }

        Optional<Notification> notifOpt = notificationRepository.findById(id);
        if (notifOpt.isPresent()) {
            Notification notification = notifOpt.get();
            if (notification.getUserRecipient() != null
                    && notification.getUserRecipient().getId().equals(user.getId())) {
                notification.setIsRead(true);
                notificationRepository.save(notification);
                return org.springframework.http.ResponseEntity.ok("Marked as read");
            }
        }
        return org.springframework.http.ResponseEntity.badRequest().body("Notification not found or unauthorized");
    }

    @RequestMapping(value = "/api/meetings/{id}", method = RequestMethod.GET)
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> getMeetingDetails(@PathVariable long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return org.springframework.http.ResponseEntity.badRequest().body("Unauthorized");
        }
        Optional<Meeting> meetingOpt = meetingRepository.findById(id);
        if (meetingOpt.isPresent()) {
            Meeting m = meetingOpt.get();
            Map<String, Object> response = new HashMap<>();
            response.put("id", m.getId());
            response.put("title", m.getTitle());
            response.put("description", m.getDescription());
            response.put("startTime", m.getStartTime());
            response.put("endTime", m.getEndTime());
            response.put("meetingType", m.getMeetingType());
            response.put("location", m.getLocation());
            return org.springframework.http.ResponseEntity.ok(response);
        }
        return org.springframework.http.ResponseEntity.badRequest().body("Meeting not found");
    }

    @RequestMapping(value = "/notifications/history", method = RequestMethod.GET)
    public String viewNotificationHistory(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"HR".equals(user.getRole())) {
            return "redirect:/hr/login";
        }

        List<Notification> notifications = notificationRepository.findByUserRecipientOrderByCreatedAtDesc(user);
        model.addAttribute("notifications", notifications);
        model.addAttribute("userName", user.getName());

        return "hr-notifications";
    }

    @GetMapping("/attendance/history/{employeeId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAttendanceHistory(@PathVariable Long employeeId) {
        Map<String, Object> response = new HashMap<>();

        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (!employeeOpt.isPresent()) {
            response.put("error", "Employee not found");
            return ResponseEntity.status(404).body(response);
        }

        Employee employee = employeeOpt.get();
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        try {
            // Fetch Data
            List<Attendance> monthlyAttendance = attendanceRepository.findByEmployeeAndDateRange(employee, startOfMonth,
                    endOfMonth);
            List<LeaveRequest> monthlyLeaves = leaveRequestRepository.findLeavesInDateRange(startOfMonth, endOfMonth);

            // Calculate Approved Leaves safely
            long approvedLeavesCount = monthlyLeaves.stream()
                    .filter(l -> l != null
                            && l.getEmployee() != null
                            && l.getEmployee().getId() != null
                            && l.getEmployee().getId().equals(employee.getId())
                            && "APPROVED".equalsIgnoreCase(l.getStatus())
                            && l.getNumberOfDays() != null)
                    .mapToLong(LeaveRequest::getNumberOfDays)
                    .sum();

            // Calculate Metrics
            LocalDate effectiveStart = startOfMonth;
            if (employee.getJoinDate() != null && employee.getJoinDate().isAfter(startOfMonth)) {
                effectiveStart = employee.getJoinDate();
            }

            List<com.example.demo.model.CompanyHoliday> holidays = companyHolidayRepository.findAll();
            Set<LocalDate> holidayDates = (holidays != null)
                    ? holidays.stream().map(com.example.demo.model.CompanyHoliday::getDate).filter(Objects::nonNull).collect(Collectors.toSet())
                    : Collections.emptySet();

            // Total working days in the month (excluding Saturdays, Sundays, and Company Holidays)
            long totalWorkingDays = startOfMonth.datesUntil(endOfMonth.plusDays(1))
                    .filter(d -> d.getDayOfWeek() != java.time.DayOfWeek.SATURDAY
                            && d.getDayOfWeek() != java.time.DayOfWeek.SUNDAY
                            && !holidayDates.contains(d))
                    .count();

            // Working days passed up to today (excluding weekends and holidays)
            long workingDaysPassed = effectiveStart.datesUntil(today.plusDays(1))
                    .filter(d -> d.getDayOfWeek() != java.time.DayOfWeek.SATURDAY
                            && d.getDayOfWeek() != java.time.DayOfWeek.SUNDAY
                            && !holidayDates.contains(d))
                    .count();

            // Check if employee is present today
            boolean isPresentToday = "IN".equalsIgnoreCase(employee.getStatus())
                    || monthlyAttendance.stream().anyMatch(a -> today.equals(a.getAttendanceDate()));

            // Distinct days present
            long daysPresent = monthlyAttendance.stream()
                    .map(Attendance::getAttendanceDate)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            if (isPresentToday && monthlyAttendance.stream().noneMatch(a -> today.equals(a.getAttendanceDate()))) {
                daysPresent++;
            }

            long leaves = approvedLeavesCount;
            long overtimeDays = monthlyAttendance.stream()
                    .filter(a -> a.getTotalHours() != null && a.getTotalHours() > 9).count();

            // LOP = Total Working Days Passed - Present - Leaves (cannot be negative)
            long lopDays = Math.max(0, workingDaysPassed - daysPresent - leaves);

            // Construct Daily Records
            List<Map<String, Object>> records = monthlyAttendance.stream().map(att -> {
                Map<String, Object> record = new HashMap<>();
                record.put("date", att.getAttendanceDate().toString());
                record.put("login",
                        att.getCheckInTime() != null ? att.getCheckInTime().toLocalTime().toString().substring(0, 5)
                                : "-");
                record.put("logout",
                        att.getCheckOutTime() != null ? att.getCheckOutTime().toLocalTime().toString().substring(0, 5)
                                : "-");

                String totalTime = "-";
                if (att.getCheckInTime() != null && att.getCheckOutTime() != null) {
                    java.time.Duration duration = java.time.Duration.between(att.getCheckInTime(),
                            att.getCheckOutTime());
                    long hours = duration.toHours();
                    long minutes = duration.toMinutesPart();
                    totalTime = String.format("%dh %dm", hours, minutes);
                }
                record.put("totalTime", totalTime);
                record.put("status", att.getStatus());
                record.put("workMode", att.getWorkMode() != null ? att.getWorkMode() : "OFFICE");
                return record;
            }).collect(Collectors.toList());
            records.sort((m1, m2) -> ((String) m2.get("date")).compareTo((String) m1.get("date")));

            // Summary Object
            Map<String, Object> summary = new HashMap<>();
            summary.put("id", 1);
            summary.put("employee_id", employee.getId());
            summary.put("employee_code", employee.getEmployeeId());
            summary.put("employee_name", employee.getName());
            summary.put("month", today.getMonthValue());
            summary.put("year", today.getYear());
            summary.put("total_working_days", totalWorkingDays);
            summary.put("days_present", daysPresent);
            summary.put("leaves", leaves);
            summary.put("lop_days", lopDays);
            summary.put("overtime_days", overtimeDays);

            response.put("summary", summary);
            response.put("records", records);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", "Internal Server Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/attendance/export")
    public void exportAllAttendance(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"Attendance_Summary_" + LocalDate.now() + ".csv\"");

        try (java.io.PrintWriter writer = response.getWriter()) {
            // Header
            writer.println("Employee ID,Name,Month,Year,Total Working Days,Days Present,Leaves,LOP Days,Overtime Days");

            LocalDate today = LocalDate.now();
            LocalDate startOfMonth = today.withDayOfMonth(1);
            LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());
            int totalDaysInMonth = today.lengthOfMonth();

            List<com.example.demo.model.CompanyHoliday> holidays = companyHolidayRepository.findAll();
            Set<LocalDate> holidayDates = (holidays != null)
                    ? holidays.stream().map(com.example.demo.model.CompanyHoliday::getDate).filter(Objects::nonNull).collect(Collectors.toSet())
                    : Collections.emptySet();

            // Total working days in month (excluding Saturdays, Sundays, and Company Holidays)
            long totalWorkingDaysMonth = startOfMonth.datesUntil(endOfMonth.plusDays(1))
                    .filter(d -> d.getDayOfWeek() != java.time.DayOfWeek.SATURDAY
                            && d.getDayOfWeek() != java.time.DayOfWeek.SUNDAY
                            && !holidayDates.contains(d))
                    .count();

            List<Employee> allEmployees = employeeRepository.findAll();

            for (Employee emp : allEmployees) {
                try {
                    List<Attendance> monthlyAttendance = attendanceRepository.findByEmployeeAndDateRange(emp,
                            startOfMonth, endOfMonth);
                    List<LeaveRequest> monthlyLeaves = leaveRequestRepository.findLeavesInDateRange(startOfMonth,
                            endOfMonth);

                    // Approved Leaves
                    long leaves = monthlyLeaves.stream()
                            .filter(l -> l != null
                                    && l.getEmployee() != null
                                    && l.getEmployee().getId() != null
                                    && l.getEmployee().getId().equals(emp.getId())
                                    && "APPROVED".equalsIgnoreCase(l.getStatus())
                                    && l.getNumberOfDays() != null)
                            .mapToLong(LeaveRequest::getNumberOfDays)
                            .sum();

                    LocalDate effectiveStart = (emp.getJoinDate() != null && emp.getJoinDate().isAfter(startOfMonth))
                            ? emp.getJoinDate()
                            : startOfMonth;
                    if (effectiveStart.isAfter(today)) {
                        effectiveStart = today;
                    }

                    long workingDaysPassed = effectiveStart.datesUntil(today.plusDays(1))
                            .filter(d -> d.getDayOfWeek() != java.time.DayOfWeek.SATURDAY
                                    && d.getDayOfWeek() != java.time.DayOfWeek.SUNDAY
                                    && !holidayDates.contains(d))
                            .count();

                    boolean isPresentToday = "IN".equalsIgnoreCase(emp.getStatus())
                            || monthlyAttendance.stream().anyMatch(a -> today.equals(a.getAttendanceDate()));

                    long daysPresent = monthlyAttendance.stream()
                            .map(Attendance::getAttendanceDate)
                            .filter(Objects::nonNull)
                            .distinct()
                            .count();

                    if (isPresentToday && monthlyAttendance.stream().noneMatch(a -> today.equals(a.getAttendanceDate()))) {
                        daysPresent++;
                    }

                    long overtimeDays = monthlyAttendance.stream()
                            .filter(a -> a.getTotalHours() != null && a.getTotalHours() > 9).count();
                    long lopDays = Math.max(0, workingDaysPassed - daysPresent - leaves);

                    // Write CSV Row
                    writer.printf("%s,\"%s\",%d,%d,%d,%d,%d,%d,%d%n",
                            emp.getEmployeeId(),
                            emp.getName(),
                            today.getMonthValue(),
                            today.getYear(),
                            totalWorkingDaysMonth,
                            daysPresent,
                            leaves,
                            lopDays,
                            overtimeDays);
                } catch (Exception e) {
                    // Log error for this employee but continue
                    System.err.println(
                            "Error exporting attendance for employee: " + emp.getName() + " - " + e.getMessage());
                    writer.printf("%s,\"%s (Error)\",,,,,,,%n", emp.getEmployeeId(), emp.getName());
                }
            }
        }
    }
}
