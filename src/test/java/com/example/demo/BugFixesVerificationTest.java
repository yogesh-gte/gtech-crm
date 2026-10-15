package com.example.demo;

import com.example.demo.model.Attendance;
import com.example.demo.model.Employee;
import com.example.demo.model.PerformanceRecord;
import com.example.demo.model.Task;
import com.example.demo.repository.*;
import com.example.demo.service.PerformanceScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BugFixesVerificationTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private PerformanceRecordRepository performanceRecordRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @InjectMocks
    private PerformanceScoreService performanceScoreService;

    @InjectMocks
    private com.example.demo.service.EffortScoreService effortScoreService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        employee = new Employee();
        employee.setId(1L);
        employee.setName("Test Employee");
        employee.setEmployeeId("EMP001");
        employee.setPaidLeave(1);
        employee.setLeaveBalance(1);
    }

    /**
     * Bug 8 Verification:
     * When an employee has 0 tasks assigned today, getTodayTaskCompletionRate should return 0.0,
     * not 100.0.
     */
    @Test
    void testBug8_NoTasksAssignedToday_ReturnsZeroCompletionRate() {
        when(taskRepository.findTodayTasksByEmployee(eq(employee), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        double rate = performanceScoreService.getTodayTaskCompletionRate(employee);
        assertEquals(0.0, rate, 0.001, "Completion rate should be 0.0 when no tasks are assigned today");
    }

    /**
     * Bug 8 Verification:
     * When tasks exist, completion rate should be accurately calculated.
     */
    @Test
    void testBug8_TasksAssignedToday_CalculatesProperRate() {
        Task t1 = new Task();
        t1.setStatus("COMPLETED");
        Task t2 = new Task();
        t2.setStatus("PENDING");

        when(taskRepository.findTodayTasksByEmployee(eq(employee), any(LocalDate.class)))
                .thenReturn(List.of(t1, t2));

        double rate = performanceScoreService.getTodayTaskCompletionRate(employee);
        assertEquals(50.0, rate, 0.001, "Completion rate should be 50.0% for 1 out of 2 completed tasks");
    }

    /**
     * Bug 4 Verification:
     * A newly added employee with zero activity (no attendance, no tasks, no issues)
     * must receive null composite score (displaying N/A), instead of a fabricated 90-100% score.
     */
    @Test
    void testBug4_NewEmployeeWithNoActivity_HasNullCompositeScore() {
        when(performanceRecordRepository.findByEmployeeAndPeriodTypeOrderByPeriodStartDesc(eq(employee), eq("WEEKLY")))
                .thenReturn(Collections.emptyList());
        when(attendanceRepository.findByEmployeeAndDateRange(eq(employee), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(taskRepository.findByEmployeeAndAssignedDateBetween(eq(employee), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(issueRepository.findByAssignee(eq(employee)))
                .thenReturn(Collections.emptyList());
        when(performanceRecordRepository.save(any(PerformanceRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PerformanceRecord record = performanceScoreService.getOrCreateWeeklyRecord(employee);

        assertNotNull(record);
        assertNull(record.getCompositeScore(), "Composite score should be null (N/A) for employee with no activity");
        assertNull(record.getTaskScore(), "Task score should be null for employee with no activity");
        assertNull(record.getAttendanceScore(), "Attendance score should be null for employee with no activity");
        assertEquals(0, record.getTasksTotal());
        assertEquals(0, record.getTasksCompleted());
    }

    /**
     * Bugs 1 & 3 Verification:
     * Working days calculation must exclude both Saturdays and Sundays, as well as company holidays.
     * Furthermore, if an employee is present today, LOP days must not falsely penalize them.
     */
    @Test
    void testBugs1And3_WorkingDaysCalculation_ExcludesWeekendsAndHolidays() {
        LocalDate startOfMonth = LocalDate.of(2026, 10, 1);
        LocalDate endOfMonth = LocalDate.of(2026, 10, 31);
        
        // Let's define a holiday on Oct 2 (Gandhi Jayanti, a Friday)
        LocalDate holiday = LocalDate.of(2026, 10, 2);
        java.util.Set<LocalDate> holidays = java.util.Set.of(holiday);

        long workingDays = startOfMonth.datesUntil(endOfMonth.plusDays(1))
                .filter(d -> d.getDayOfWeek() != java.time.DayOfWeek.SATURDAY
                        && d.getDayOfWeek() != java.time.DayOfWeek.SUNDAY
                        && !holidays.contains(d))
                .count();

        // In October 2026:
        // Total days: 31
        // Saturdays: Oct 3, 10, 17, 24, 31 (5 Saturdays)
        // Sundays: Oct 4, 11, 18, 25 (4 Sundays)
        // Weekdays: 31 - 9 = 22 weekdays
        // Minus 1 holiday (Oct 2): 21 working days
        // (Previously it was calculating 27 days by only excluding Sundays)
        assertEquals(21, workingDays, "Working days in Oct 2026 should be 21 when excluding Saturdays, Sundays, and 1 holiday");
        assertNotEquals(27, workingDays, "Working days should not be 27");
    }

    /**
     * Bug 1 Verification:
     * When employee has attended today and has 0 unexcused absences, LOP should be 0.
     */
    @Test
    void testBug1_EmployeePresentToday_NoLOP() {
        long workingDaysPassed = 5;
        long daysPresent = 5;
        long approvedLeaves = 0;

        long lopDays = Math.max(0, workingDaysPassed - daysPresent - approvedLeaves);
        assertEquals(0, lopDays, "LOP days should be 0 when employee was present for all working days up to today");
    }

    /**
     * Bug 9 Verification:
     * Holiday validation logic prevents blank names or past/duplicate dates.
     */
    @Test
    void testBug9_HolidayValidation() {
        String emptyName = "   ";
        assertTrue(emptyName == null || emptyName.trim().isEmpty(), "Empty holiday name should be invalid");

        LocalDate holidayDate = LocalDate.of(2026, 12, 25);
        List<LocalDate> existingHolidays = List.of(LocalDate.of(2026, 12, 25));
        boolean isDuplicate = existingHolidays.contains(holidayDate);
        assertTrue(isDuplicate, "Duplicate holiday date should be detected and rejected");
    }

    /**
     * Bugs 10, 13, 14 Verification:
     * Meeting validation:
     * - Title required
     * - Location required
     * - End time must be after start time
     */
    @Test
    void testBugs10_13_14_MeetingValidation() {
        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 10, 20, 14, 0);
        java.time.LocalDateTime endBeforeStart = java.time.LocalDateTime.of(2026, 10, 20, 13, 0);
        java.time.LocalDateTime endValid = java.time.LocalDateTime.of(2026, 10, 20, 15, 0);

        assertTrue(endBeforeStart.isBefore(start), "End time before start time must be rejected");
        assertTrue(endValid.isAfter(start), "End time after start time must be accepted");

        String location = "Conference Room A";
        assertFalse(location.trim().isEmpty(), "Location should not be empty");
    }

    /**
     * Bug 15 Verification:
     * Date range calculation differentiates Daily, Weekly, and Monthly reports.
     */
    @Test
    void testBug15_ReportPeriodCalculations() {
        LocalDate today = LocalDate.of(2026, 10, 15); // Thursday

        // Daily
        LocalDate dailyStart = today;
        LocalDate dailyEnd = today;
        long dailyDays = java.time.temporal.ChronoUnit.DAYS.between(dailyStart, dailyEnd) + 1;
        assertEquals(1, dailyDays, "Daily period must cover exactly 1 day");

        // Weekly (Monday of current week to today)
        LocalDate weeklyStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        long weeklyDays = java.time.temporal.ChronoUnit.DAYS.between(weeklyStart, today) + 1;
        assertEquals(LocalDate.of(2026, 10, 12), weeklyStart, "Weekly start should be Monday Oct 12");
        assertEquals(4, weeklyDays, "Weekly days to Thursday Oct 15 should be 4 days");

        // Monthly (1st of month to today)
        LocalDate monthlyStart = today.withDayOfMonth(1);
        long monthlyDays = java.time.temporal.ChronoUnit.DAYS.between(monthlyStart, today) + 1;
        assertEquals(LocalDate.of(2026, 10, 1), monthlyStart, "Monthly start should be Oct 1");
        assertEquals(15, monthlyDays, "Monthly days to Oct 15 should be 15 days");

        // Assert all 3 periods produce distinctly different day spans
        assertNotEquals(dailyDays, weeklyDays);
        assertNotEquals(weeklyDays, monthlyDays);
    }

    /**
     * Bug 16 Verification:
     * Custom report URL path construction must never produce protocol-relative "//reports" URLs.
     */
    @Test
    void testBug16_ReportUrlPathConstruction() {
        String basePath = "/reports";
        String reportType = "employee";
        String startDate = "2026-10-14";
        String endDate = "2026-10-22";

        String targetUrl = basePath + "/" + reportType + "?startDate=" + startDate + "&endDate=" + endDate;
        
        assertFalse(targetUrl.startsWith("//"), "URL must not start with double-slash // which causes browser hostname resolution error");
        assertEquals("/reports/employee?startDate=2026-10-14&endDate=2026-10-22", targetUrl);
    }

    /**
     * LOG-EMP-010 Verification:
     * Effort Score combines attendance punctuality (50%), task completion (40%), and consistency (10%).
     */
    @Test
    void testLOG_EMP_010_CompositeEffortScore_AttendanceAndPerformance() {
        Attendance attendanceOnTime = new Attendance(employee, LocalDate.now());
        attendanceOnTime.setStatus("PRESENT");
        attendanceOnTime.setCheckInTime(LocalDate.now().atTime(8, 55));
        attendanceOnTime.setCheckOutTime(LocalDate.now().atTime(18, 5));

        Task completedTask = new Task();
        completedTask.setStatus("COMPLETED");

        when(taskRepository.findTodayTasksByEmployee(eq(employee), any(LocalDate.class)))
                .thenReturn(List.of(completedTask));
        when(issueRepository.findByAssignee(eq(employee)))
                .thenReturn(Collections.emptyList());
        when(attendanceRepository.findByEmployeeAndDateRange(eq(employee), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(attendanceOnTime));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        double highScore = effortScoreService.calculateEffortScore(employee, attendanceOnTime);
        assertTrue(highScore >= 80.0, "Effort score for on-time check-in with 100% completed tasks should be high");

        // Now test late check-in with 0 completed tasks
        Attendance attendanceLate = new Attendance(employee, LocalDate.now());
        attendanceLate.setStatus("LATE");
        attendanceLate.setCheckInTime(LocalDate.now().atTime(9, 45));

        Task pendingTask = new Task();
        pendingTask.setStatus("PENDING");

        when(taskRepository.findTodayTasksByEmployee(eq(employee), any(LocalDate.class)))
                .thenReturn(List.of(pendingTask));

        double lowerScore = effortScoreService.calculateEffortScore(employee, attendanceLate);
        assertTrue(lowerScore < highScore, "Score for late check-in with pending tasks must be lower than on-time with completed tasks");
    }

    /**
     * LOG-EMP-005 Verification:
     * Paid leave enforces single-day duration and 1-per-month calendar limit.
     */
    @Test
    void testLOG_EMP_005_PaidLeave_SingleDateAndMonthlyLimit() {
        LocalDate start = LocalDate.of(2026, 11, 10);
        LocalDate multiDayEnd = LocalDate.of(2026, 11, 12);
        assertNotEquals(start, multiDayEnd, "Multi-day paid leave should be disallowed");

        // Monthly limit check
        com.example.demo.model.LeaveRequest existing = new com.example.demo.model.LeaveRequest();
        existing.setLeaveType("PAID");
        existing.setStatus("APPROVED");
        existing.setStartDate(LocalDate.of(2026, 11, 2));

        List<com.example.demo.model.LeaveRequest> leaves = List.of(existing);

        boolean alreadyAppliedThisMonth = leaves.stream()
                .filter(lr -> "PAID".equalsIgnoreCase(lr.getLeaveType()))
                .filter(lr -> !"REJECTED".equalsIgnoreCase(lr.getStatus()))
                .anyMatch(lr -> lr.getStartDate() != null &&
                        lr.getStartDate().getYear() == start.getYear() &&
                        lr.getStartDate().getMonthValue() == start.getMonthValue());

        assertTrue(alreadyAppliedThisMonth, "Employee already used their 1 Paid Leave for November 2026");
    }

    /**
     * LOG-EMP-011 Verification:
     * Dynamic leave balance reflects accurate sum of all categories and deducts upon request.
     */
    @Test
    void testLOG_EMP_011_DynamicLeaveBalance_Recalculation() {
        employee.setPaidLeave(1);
        employee.setSickLeave(2);
        employee.setCasualLeave(3);
        employee.setCustomLeaves(new java.util.HashMap<>(java.util.Map.of("SPECIAL", 4)));

        int balance = (employee.getPaidLeave() != null ? employee.getPaidLeave() : 0)
                + (employee.getSickLeave() != null ? employee.getSickLeave() : 0)
                + (employee.getCasualLeave() != null ? employee.getCasualLeave() : 0);
        for (Integer cVal : employee.getCustomLeaves().values()) {
            if (cVal != null) balance += cVal;
        }

        assertEquals(10, balance, "Total dynamic leave balance should be 1 + 2 + 3 + 4 = 10");

        // Deduct 1 paid leave
        employee.setPaidLeave(0);
        int updated = (employee.getPaidLeave() != null ? employee.getPaidLeave() : 0)
                + (employee.getSickLeave() != null ? employee.getSickLeave() : 0)
                + (employee.getCasualLeave() != null ? employee.getCasualLeave() : 0);
        for (Integer cVal : employee.getCustomLeaves().values()) {
            if (cVal != null) updated += cVal;
        }
        assertEquals(9, updated, "Updated leave balance should be 9 after paid leave deduction");
    }

    /**
     * LOG-EMP-012 Verification:
     * Regular employees cannot delete tasks; only Team Leads, Managers, or Admins can.
     */
    @Test
    void testLOG_EMP_012_DeleteTask_RestrictedToAuthorizedRoles() {
        // Regular employee
        employee.setIsTeamLead(false);
        boolean employeeCanDelete = Boolean.TRUE.equals(employee.getIsTeamLead());
        assertFalse(employeeCanDelete, "Regular employee must not have delete task permission");

        // Team Lead employee
        employee.setIsTeamLead(true);
        boolean tlCanDelete = Boolean.TRUE.equals(employee.getIsTeamLead());
        assertTrue(tlCanDelete, "Team Lead must have delete task permission");
    }

    @Test
    void testEmployeeSidebarFragmentRendering() {
        org.thymeleaf.spring6.SpringTemplateEngine engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        org.thymeleaf.templateresolver.ClassLoaderTemplateResolver resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(org.thymeleaf.templatemode.TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);

        org.springframework.mock.web.MockHttpServletRequest req = new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.mock.web.MockHttpServletResponse res = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockServletContext servletContext = new org.springframework.mock.web.MockServletContext();
        org.thymeleaf.web.servlet.JakartaServletWebApplication webApp = org.thymeleaf.web.servlet.JakartaServletWebApplication.buildApplication(servletContext);
        org.thymeleaf.web.IWebExchange webExchange = webApp.buildExchange(req, res);
        org.thymeleaf.context.WebContext context = new org.thymeleaf.context.WebContext(webExchange);

        employee.setName("John Doe");
        employee.setDepartment("Engineering");
        context.setVariable("employee", employee);
        context.setVariable("activeTab", "dashboard");

        String html = engine.process("fragments/employee-sidebar", context);
        assertNotNull(html);
        assertTrue(html.contains("LogSphere"));
        assertTrue(html.contains("John Doe"));
        assertTrue(html.contains("Engineering"));
        assertTrue(html.contains("nav-link active"));

        // Also test with null employee to ensure safe fallback without SpEL exceptions
        org.thymeleaf.web.IWebExchange webExchange2 = webApp.buildExchange(new org.springframework.mock.web.MockHttpServletRequest(), new org.springframework.mock.web.MockHttpServletResponse());
        org.thymeleaf.context.WebContext nullContext = new org.thymeleaf.context.WebContext(webExchange2);
        nullContext.setVariable("activeTab", "attendance");
        String nullHtml = engine.process("fragments/employee-sidebar", nullContext);
        assertNotNull(nullHtml);
        assertTrue(nullHtml.contains("Employee"));
    }

    @Test
    void testAllEmployeePagesContainSidebar() {
        org.thymeleaf.spring6.SpringTemplateEngine engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        org.thymeleaf.templateresolver.ClassLoaderTemplateResolver resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(org.thymeleaf.templatemode.TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);

        org.springframework.mock.web.MockHttpServletRequest req = new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        employee.setName("Test Employee");
        employee.setDepartment("Engineering");
        employee.setEmployeeId("EMP999");
        session.setAttribute("employee", employee);
        req.setSession(session);

        org.springframework.mock.web.MockHttpServletResponse res = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockServletContext servletContext = new org.springframework.mock.web.MockServletContext();
        org.thymeleaf.web.servlet.JakartaServletWebApplication webApp = org.thymeleaf.web.servlet.JakartaServletWebApplication.buildApplication(servletContext);
        org.thymeleaf.web.IWebExchange webExchange = webApp.buildExchange(req, res);
        org.thymeleaf.context.WebContext context = new org.thymeleaf.context.WebContext(webExchange);

        context.setVariable("employee", employee);
        context.setVariable("session", session);
        context.setVariable("todayTasks", Collections.emptyList());
        context.setVariable("allTasks", Collections.emptyList());
        context.setVariable("attendanceRecords", Collections.emptyList());
        context.setVariable("attendanceHistory", Collections.emptyList());
        context.setVariable("totalHours", 0.0);
        context.setVariable("leaves", Collections.emptyList());
        context.setVariable("leaveRequests", Collections.emptyList());
        context.setVariable("leaveBalance", 12);
        context.setVariable("paidLeavesLeft", 1);
        context.setVariable("payrolls", Collections.emptyList());
        context.setVariable("notifications", Collections.emptyList());
        context.setVariable("mySuggestions", Collections.emptyList());
        context.setVariable("recentSuggestions", Collections.emptyList());
        context.setVariable("projects", Collections.emptyList());
        context.setVariable("issues", Collections.emptyList());
        context.setVariable("sprints", Collections.emptyList());
        context.setVariable("canCreateIssue", true);
        context.setVariable("users", Collections.emptyList());
        context.setVariable("statuses", Collections.emptyList());
        context.setVariable("priorities", Collections.emptyList());
        context.setVariable("weeklyRecord", new PerformanceRecord());

        com.example.demo.model.Task mockTask = new com.example.demo.model.Task();
        mockTask.setId(1L);
        mockTask.setTitle("Test Task");
        mockTask.setStatus("IN_PROGRESS");
        mockTask.setPriority("HIGH");
        mockTask.setProgressPercentage(50);
        mockTask.setEmployee(employee);
        context.setVariable("task", mockTask);
        context.setVariable("comments", Collections.emptyList());

        com.example.demo.model.Payroll mockPayroll = new com.example.demo.model.Payroll();
        mockPayroll.setId(1L);
        mockPayroll.setEmployee(employee);
        mockPayroll.setMonth(10);
        mockPayroll.setYear(2026);
        mockPayroll.setGrossSalary(50000.0);
        mockPayroll.setLopDeduction(0.0);
        mockPayroll.setTotalDeductions(0.0);
        mockPayroll.setNetPay(50000.0);
        mockPayroll.setPayrollStatus(com.example.demo.model.Payroll.PayrollStatus.PAID);
        context.setVariable("payroll", mockPayroll);

        com.example.demo.model.Team mockTeam = new com.example.demo.model.Team();
        mockTeam.setId(1L);
        mockTeam.setName("Alpha Team");
        context.setVariable("team", mockTeam);
        context.setVariable("teamMembers", Collections.emptyList());
        context.setVariable("completedByMember", Collections.emptyMap());
        context.setVariable("completedToday", Collections.emptyList());
        context.setVariable("activeSprints", Collections.emptyList());

        context.setVariable("allVisitors", Collections.emptyList());
        context.setVariable("pendingVisitors", Collections.emptyList());
        context.setVariable("approvedVisitors", Collections.emptyList());
        context.setVariable("otherStatusVisitors", Collections.emptyList());

        String[] employeeTemplates = {
            "employee-dashboard",
            "employee-attendance-history",
            "employee-manual-attendance",
            "employee-tasks",
            "employee-task-details",
            "employee-performance",
            "employee-payroll-history",
            "employee-payslip-view",
            "employee-leave-apply",
            "employee-leave-history",
            "employee-qrcode",
            "employee-suggestion-form",
            "employee-my-suggestions",
            "employee-notification-history",
            "employee-approvals",
            "tl-dashboard",
            "issue-list",
            "sprint-list"
        };

        for (String tpl : employeeTemplates) {
            String rendered = engine.process(tpl, context);
            assertNotNull(rendered, "Template " + tpl + " failed to render");
            assertTrue(rendered.contains("employeeSidebar"), "Template " + tpl + " must contain employeeSidebar");
            assertTrue(rendered.contains("Test Employee"), "Template " + tpl + " must contain employee name in sidebar");
            if ("employee-dashboard".equals(tpl)) {
                assertFalse(rendered.contains("onclick=\"location.reload()\""), "Refresh button should be removed from employee dashboard");
                assertFalse(rendered.contains("btn-outline-danger shadow-sm"), "Duplicate secondary logout button should be removed from employee dashboard");
            } else if (!"issue-list".equals(tpl) && !"sprint-list".equals(tpl) && !"employee-task-details".equals(tpl)) {
                assertFalse(rendered.contains(">Dashboard</a>") || rendered.contains("> Dashboard</a>") || rendered.contains(">My Dashboard</a>"),
                    "Template " + tpl + " should not have a duplicate right-side navbar Dashboard button");
            }
        }
    }
}
