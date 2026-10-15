package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Controller
@RequestMapping("/manager")
public class ManagerController {

    @Autowired
    private EmployeeRepository employeeRepository;



    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskCommentRepository taskCommentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private com.example.demo.repository.NotificationRepository notificationRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TeamBadgeRepository teamBadgeRepository;

    @Autowired
    private TeamRuleRepository teamRuleRepository;

    @Autowired
    private IdleIncidentRepository idleIncidentRepository;

    @Autowired
    private SuggestionRepository suggestionRepository;

    @Autowired
    private EmployeeActivityRepository employeeActivityRepository;

    @org.springframework.beans.factory.annotation.Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Autowired
    private com.example.demo.service.PerformanceScoreService performanceScoreService;

    @Autowired
    private com.example.demo.service.EmailService emailService;

    @ModelAttribute
    public void populateCommonManagerAttributes(Model model, HttpSession session) {
        try {
            List<com.example.demo.model.IdleIncident> activeIdleIncidents = idleIncidentRepository.findActiveIncidents();
            model.addAttribute("idleIncidents", activeIdleIncidents != null ? activeIdleIncidents : Collections.emptyList());
            model.addAttribute("idleCount", activeIdleIncidents != null ? activeIdleIncidents.size() : 0);
        } catch (Exception e) {
            model.addAttribute("idleIncidents", Collections.emptyList());
            model.addAttribute("idleCount", 0);
        }
    }

    public Map<Employee, String> calculateEmployeeAvailability(List<Employee> allEmployees) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<LeaveRequest> activeLeaves = leaveRequestRepository.findActiveLeavesOnDate(today);
        List<Meeting> todayMeetings = meetingRepository.findByDateRange(
                now.withHour(0).withMinute(0),
                now.withHour(23).withMinute(59));
        List<Task> allTasks = taskRepository.findAll();

        Map<Employee, String> employeeAvailability = new HashMap<>();
        for (Employee emp : allEmployees) {
            boolean onLeave = activeLeaves != null && activeLeaves.stream()
                    .anyMatch(l -> l.getEmployee() != null && l.getEmployee().getId().equals(emp.getId()));

            boolean inMeeting = todayMeetings != null && todayMeetings.stream().anyMatch(m -> {
                if ("COMPLETED".equalsIgnoreCase(m.getStatus()) || "CANCELLED".equalsIgnoreCase(m.getStatus())) {
                    return false;
                }
                boolean isTimeNow = "ONGOING".equalsIgnoreCase(m.getStatus()) ||
                        (m.getStartTime() != null && !now.isBefore(m.getStartTime()) &&
                        (m.getEndTime() == null || !now.isAfter(m.getEndTime())));
                if (!isTimeNow) {
                    return false;
                }
                boolean isOrganizer = m.getOrganizer() != null && m.getOrganizer().getId().equals(emp.getId());
                boolean isAttendee = m.getAttendees() != null && m.getAttendees().stream()
                        .anyMatch(a -> a != null && a.getId().equals(emp.getId()));
                return isOrganizer || isAttendee;
            });

            long highPriorityTasks = allTasks.stream()
                    .filter(t -> t.getEmployee() != null && t.getEmployee().getId().equals(emp.getId()) &&
                            "HIGH".equalsIgnoreCase(t.getPriority()) && !"COMPLETED".equalsIgnoreCase(t.getStatus()))
                    .count();

            if (onLeave) {
                employeeAvailability.put(emp, "ON_LEAVE");
            } else if (inMeeting) {
                employeeAvailability.put(emp, "IN_MEETING");
            } else if (highPriorityTasks > 0) {
                employeeAvailability.put(emp, "BUSY");
            } else {
                employeeAvailability.put(emp, "AVAILABLE");
            }
        }
        return employeeAvailability;
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String showManagerLoginForm(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user != null && "MANAGER".equals(user.getRole())) {
            return "redirect:/manager/dashboard";
        }
        return "manager-login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public String managerLogin(@RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (!"MANAGER".equals(user.getRole())) {
                redirectAttributes.addFlashAttribute("error", "Access denied. Manager login only.");
                return "redirect:/manager/login";
            }
            if (user.getPassword().equals(password)) {
                session.setAttribute("user", user);
                session.setAttribute("userName", user.getName());
                session.setAttribute("userRole", user.getRole());
                return "redirect:/manager/dashboard";
            } else {
                redirectAttributes.addFlashAttribute("error", "Invalid email or password!");
                return "redirect:/manager/login";
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "Invalid email or password!");
            return "redirect:/manager/login";
        }
    }

    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)
    public String managerDashboard(@RequestParam(required = false, defaultValue = "planning") String tab,
            Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        // Add active tab to model
        model.addAttribute("activeTab", tab);

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // Get all employees
        List<Employee> allEmployees = employeeRepository.findAll();
        List<Employee> presentEmployees = employeeRepository.findCurrentlyPresentEmployees();

        // 1. Team Workload Distribution
        List<Task> allTasks = taskRepository.findAll();
        Map<Employee, Long> taskCountByEmployee = allTasks.stream()
                .filter(t -> !"COMPLETED".equals(t.getStatus()))
                .collect(Collectors.groupingBy(Task::getEmployee, Collectors.counting()));

        List<Employee> overloadedEmployees = new ArrayList<>();
        List<Employee> balancedEmployees = new ArrayList<>();
        List<Employee> underutilizedEmployees = new ArrayList<>();

        long avgTasks = taskCountByEmployee.isEmpty() ? 0
                : (long) taskCountByEmployee.values().stream().mapToLong(Long::longValue).average().orElse(0);

        for (Employee emp : allEmployees) {
            long taskCount = taskCountByEmployee.getOrDefault(emp, 0L);
            if (taskCount > avgTasks * 1.5) {
                overloadedEmployees.add(emp);
            } else if (taskCount < avgTasks * 0.5) {
                underutilizedEmployees.add(emp);
            } else {
                balancedEmployees.add(emp);
            }
        }

        // 2. Daily Team Planning Board - Tasks by priority and status
        List<Task> pendingTasks = allTasks.stream()
                .filter(t -> "PENDING".equals(t.getStatus()))
                .sorted(Comparator.comparing(Task::getPriority).reversed())
                .collect(Collectors.toList());

        List<Task> inProgressTasks = allTasks.stream()
                .filter(t -> "IN_PROGRESS".equals(t.getStatus()))
                .collect(Collectors.toList());

        List<Task> completedTasks = allTasks.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .collect(Collectors.toList());

        List<Task> overdueTasks = allTasks.stream()
                .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today)
                        && !"COMPLETED".equals(t.getStatus()))
                .collect(Collectors.toList());

        // 3. Smart Task Reallocation Suggestions
        List<Map<String, Object>> reallocationSuggestions = new ArrayList<>();
        for (Task task : overdueTasks) {
            if (task.getEmployee() != null) {
                Map<String, Object> suggestion = new HashMap<>();
                suggestion.put("task", task);
                suggestion.put("reason", "Task deadline is near");
                suggestion.put("suggestedEmployee",
                        underutilizedEmployees.isEmpty() ? null : underutilizedEmployees.get(0));
                reallocationSuggestions.add(suggestion);
            }
        }

        // 4. Goals & Milestones
        List<Goal> teamGoals = goalRepository.findByGoalType("TEAM");
        List<Goal> onTrackGoals = teamGoals.stream().filter(g -> "ON_TRACK".equals(g.getStatus()))
                .collect(Collectors.toList());
        List<Goal> atRiskGoals = teamGoals.stream().filter(g -> "AT_RISK".equals(g.getStatus()))
                .collect(Collectors.toList());
        List<Goal> behindScheduleGoals = teamGoals.stream().filter(g -> "BEHIND_SCHEDULE".equals(g.getStatus()))
                .collect(Collectors.toList());

        // 5. Team Forecast
        List<Task> upcomingTasks = allTasks.stream()
                .filter(t -> t.getDueDate() != null && t.getDueDate().isAfter(today)
                        && !"COMPLETED".equals(t.getStatus()))
                .sorted(Comparator.comparing(Task::getDueDate))
                .limit(10)
                .collect(Collectors.toList());

        // 6. Feedback Console
        List<Feedback> allFeedbacks = feedbackRepository.findAll();
        Map<Employee, List<Feedback>> feedbacksByEmployee = allFeedbacks.stream()
                .collect(Collectors.groupingBy(Feedback::getEmployee));

        List<Feedback> upcomingFollowUps = allFeedbacks.stream()
                .filter(f -> f.getFollowUpDate() != null &&
                        f.getFollowUpDate().isAfter(now) &&
                        f.getFollowUpDate().isBefore(now.plusDays(7)))
                .sorted(Comparator.comparing(Feedback::getFollowUpDate))
                .collect(Collectors.toList());

        // 7. Team Availability Planner (LOG-MGR-003)
        List<LeaveRequest> activeLeaves = leaveRequestRepository.findActiveLeavesOnDate(today);
        List<Meeting> todayMeetings = meetingRepository.findByDateRange(
                now.withHour(0).withMinute(0),
                now.withHour(23).withMinute(59));

        Map<Employee, String> employeeAvailability = calculateEmployeeAvailability(allEmployees);

        // 8. Behavioral Insights
        List<Attendance> recentAttendance = attendanceRepository.findByDateRange(today.minusDays(30), today);
        Map<Employee, Double> avgHoursByEmployee = recentAttendance.stream()
                .collect(Collectors.groupingBy(
                        Attendance::getEmployee,
                        Collectors.averagingDouble(Attendance::getTotalHours)));

        // 9. Motivation & Recognition
        List<TeamBadge> allBadges = teamBadgeRepository.findAll();
        List<TeamBadge> recentBadges = allBadges.stream()
                .filter(b -> b.getIsVisible() != null && b.getIsVisible())
                .sorted(Comparator.comparing(TeamBadge::getAwardedDate).reversed())
                .limit(10)
                .collect(Collectors.toList());

        // 10. Custom Team Rules
        List<TeamRule> activeRules = teamRuleRepository.findByIsActiveTrue();

        // Add all to model
        model.addAttribute("userName", user.getName());
        model.addAttribute("userRole", user.getRole());

        // Workload Distribution
        model.addAttribute("overloadedEmployees", overloadedEmployees);
        model.addAttribute("balancedEmployees", balancedEmployees);
        model.addAttribute("underutilizedEmployees", underutilizedEmployees);
        model.addAttribute("avgTasks", avgTasks);

        // Daily Planning
        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("inProgressTasks", inProgressTasks);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("overdueTasks", overdueTasks);
        model.addAttribute("allTasks", allTasks);

        // Task Reallocation
        model.addAttribute("reallocationSuggestions", reallocationSuggestions);

        // Goals
        model.addAttribute("teamGoals", teamGoals);
        model.addAttribute("onTrackGoals", onTrackGoals);
        model.addAttribute("atRiskGoals", atRiskGoals);
        model.addAttribute("behindScheduleGoals", behindScheduleGoals);

        // Forecast
        model.addAttribute("upcomingTasks", upcomingTasks);

        // Feedback
        model.addAttribute("feedbacksByEmployee", feedbacksByEmployee);
        model.addAttribute("upcomingFollowUps", upcomingFollowUps);
        model.addAttribute("allFeedbacks", allFeedbacks);

        // Availability
        model.addAttribute("employeeAvailability", employeeAvailability);
        model.addAttribute("activeLeaves", activeLeaves);
        model.addAttribute("todayMeetings", todayMeetings);

        // Behavioral Insights
        model.addAttribute("avgHoursByEmployee", avgHoursByEmployee);
        model.addAttribute("recentAttendance", recentAttendance);

        // Recognition
        model.addAttribute("recentBadges", recentBadges);

        // Rules
        model.addAttribute("activeRules", activeRules);

        // Fetch Notifications
        List<Notification> notifications = notificationRepository
                .findByUserRecipientAndIsReadFalseOrderByCreatedAtDesc(user);
        model.addAttribute("notifications", notifications);

        // General
        model.addAttribute("allEmployees", allEmployees);
        model.addAttribute("presentEmployees", presentEmployees);

        // LOG-MGR-001: Team leads list for task assignments
        List<Employee> teamLeads = allEmployees.stream()
                .filter(emp -> Boolean.TRUE.equals(emp.getIsTeamLead()) && (emp.getIsActive() == null || emp.getIsActive()))
                .collect(Collectors.toList());
        model.addAttribute("teamLeads", teamLeads);

        // Idle employee notifications
        List<com.example.demo.model.IdleIncident> activeIdleIncidents = idleIncidentRepository.findActiveIncidents();
        model.addAttribute("idleIncidents", activeIdleIncidents);
        model.addAttribute("idleCount", activeIdleIncidents.size());

        // Employee Suggestions
        List<com.example.demo.model.Suggestion> allSuggestions = suggestionRepository.findAllByOrderByCreatedAtDesc();
        List<com.example.demo.model.Suggestion> pendingSuggestions = suggestionRepository
                .findByStatusOrderByCreatedAtDesc("PENDING");
        List<com.example.demo.model.Suggestion> managerSuggestions = suggestionRepository
                .findBySuggestionTypeOrderByCreatedAtDesc("MANAGER");
        model.addAttribute("allSuggestions", allSuggestions);
        model.addAttribute("pendingSuggestions", pendingSuggestions);
        model.addAttribute("managerSuggestions", managerSuggestions);
        model.addAttribute("suggestionCount", pendingSuggestions.size());

        // Non-Productive Activities (Last 24 hours)
        LocalDateTime last24Hours = now.minusHours(24);
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

        model.addAttribute("nonProductiveActivities", nonProductiveActivities);
        model.addAttribute("gamingActivities", gamingActivities);
        model.addAttribute("socialMediaActivities", socialMediaActivities);
        model.addAttribute("videoActivities", videoActivities);
        model.addAttribute("nonProductiveByEmployee", nonProductiveByEmployee);
        model.addAttribute("nonProductiveCount", nonProductiveActivities.size());

        // Add task model for form
        model.addAttribute("task", new Task());

        return "manager-dashboard";
    }

    @RequestMapping(value = "/performance", method = RequestMethod.GET)
    public String managerPerformance(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }
        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(e -> e.getIsActive() == null || e.getIsActive())
                .collect(Collectors.toList());
        List<com.example.demo.model.PerformanceRecord> records = new ArrayList<>();
        for (Employee emp : employees) {
            com.example.demo.model.PerformanceRecord rec = performanceScoreService.getOrCreateWeeklyRecord(emp);
            records.add(rec);
        }
        records.sort((a, b) -> Double.compare(
                (b.getCompositeScore() != null ? b.getCompositeScore() : 0),
                (a.getCompositeScore() != null ? a.getCompositeScore() : 0)));
        List<com.example.demo.model.PerformanceRecord> topPerformers = records.stream().limit(5).collect(Collectors.toList());
        List<com.example.demo.model.PerformanceRecord> lowPerformers = records.stream()
                .skip(Math.max(0, records.size() - 5))
                .collect(Collectors.toList());
        java.util.Collections.reverse(lowPerformers);
        model.addAttribute("userName", user.getName());
        model.addAttribute("teamPerformance", records);
        model.addAttribute("topPerformers", topPerformers);
        model.addAttribute("lowPerformers", lowPerformers);
        return "manager-performance";
    }

    @RequestMapping(value = "/tasks/add", method = RequestMethod.POST)
    public String addTaskForEmployee(@RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String priority,
            @RequestParam(required = false) String dueDate,
            @RequestParam Long employeeId,
            @RequestParam(required = false) MultipartFile attachment,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        if (title == null || title.trim().isEmpty() || title.trim().length() < 3) {
            redirectAttributes.addFlashAttribute("error", "Task title is required and must be at least 3 characters.");
            return "redirect:/manager/dashboard?tab=planning";
        }
        if (priority == null || priority.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Priority is required.");
            return "redirect:/manager/dashboard?tab=planning";
        }
        if (employeeId == null) {
            redirectAttributes.addFlashAttribute("error", "Please select a Team Lead.");
            return "redirect:/manager/dashboard?tab=planning";
        }
        Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
        if (employeeOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Employee not found!");
            return "redirect:/manager/dashboard?tab=planning";
        }

        Employee employee = employeeOpt.get();
        // LOG-MGR-001: Task assignment restricted to Team Leads
        if (!Boolean.TRUE.equals(employee.getIsTeamLead())) {
            redirectAttributes.addFlashAttribute("error", "Task assignment is restricted to Team Leads only.");
            return "redirect:/manager/dashboard?tab=planning";
        }

        Task task = new Task();
        task.setTitle(title.trim());
        task.setDescription(description != null ? description.trim() : null);
        task.setPriority(priority);
        task.setEmployee(employee);
        task.setAssignedDate(LocalDate.now());
        task.setStatus("PENDING");
        task.setProgressPercentage(0);

        // LOG-MGR-006: Parse & validate due date if provided
        if (dueDate != null && !dueDate.trim().isEmpty()) {
            try {
                LocalDate parsedDueDate = LocalDate.parse(dueDate.trim());
                if (parsedDueDate.isBefore(LocalDate.now())) {
                    redirectAttributes.addFlashAttribute("error", "Due date cannot be in the past.");
                    return "redirect:/manager/dashboard?tab=planning";
                }
                task.setDueDate(parsedDueDate);
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Invalid due date format.");
                return "redirect:/manager/dashboard?tab=planning";
            }
        }

        // Handle file upload
        if (attachment != null && !attachment.isEmpty()) {
            try {
                // Use external uploads directory
                Path uploadPath = Paths.get(uploadDir, "tasks");
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                String fileName = UUID.randomUUID().toString() + "_" + attachment.getOriginalFilename();
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(attachment.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                task.setAttachmentUrl("/uploads/tasks/" + fileName);
            } catch (Exception e) {
                System.err.println("Error creating task attachment: " + e.getMessage());
                // Continue creating task without attachment
            }
        }

        taskRepository.save(task);

        // In-app notification for employee
        try {
            Notification notif = new Notification();
            notif.setRecipient(employee);
            notif.setMessage("New task assigned: " + task.getTitle() + (task.getDueDate() != null ? " (Due: " + task.getDueDate() + ")" : ""));
            notif.setCreatedAt(LocalDateTime.now());
            notif.setIsRead(false);
            notificationRepository.save(notif);
        } catch (Exception notifEx) {
            System.err.println("Failed to create task notification: " + notifEx.getMessage());
        }

        // Email notification
        if (employee.getEmail() != null && !employee.getEmail().isEmpty()) {
            String assignedByName = user.getName() != null ? user.getName() : "Manager";
            String dueStr = task.getDueDate() != null ? task.getDueDate().toString() : null;
            emailService.sendTaskAssignedNotification(employee.getEmail(), employee.getName(),
                    task.getTitle(), assignedByName, dueStr);
        }

        redirectAttributes.addFlashAttribute("success", "Task assigned to " + employee.getName() + " successfully!");
        return "redirect:/manager/dashboard?tab=planning";
    }

    @RequestMapping(value = "/tasks/details/{id}", method = RequestMethod.GET)
    public String taskDetails(@PathVariable Long id, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        Optional<Task> taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Task not found!");
            return "redirect:/manager/dashboard?tab=planning";
        }

        Task task = taskOpt.get();
        List<TaskComment> comments = taskCommentRepository.findByTaskOrderByCreatedAtAsc(task);
        
        model.addAttribute("task", task);
        model.addAttribute("comments", comments);
        model.addAttribute("manager", user);
        
        return "manager-task-details";
    }

    @RequestMapping(value = "/tasks/comment/add", method = RequestMethod.POST)
    public String addComment(@RequestParam Long taskId, @RequestParam String content, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Task not found!");
            return "redirect:/manager/dashboard?tab=planning";
        }

        if (content != null && !content.trim().isEmpty()) {
            TaskComment comment = new TaskComment(taskOpt.get(), user.getName(), "MANAGER", content);
            taskCommentRepository.save(comment);
        }

        return "redirect:/manager/tasks/details/" + taskId;
    }

    @RequestMapping(value = "/feedback/add", method = RequestMethod.GET)
    public String showAddFeedbackForm(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        List<Employee> allEmployees = employeeRepository.findAll();
        model.addAttribute("employees", allEmployees);

        return "manager-add-feedback";
    }

    @RequestMapping(value = "/feedback/add", method = RequestMethod.POST)
    public String addFeedback(@RequestParam Long employeeId,
            @RequestParam String notes,
            @RequestParam(required = false) String improvementSuggestions,
            @RequestParam(required = false) String followUpDate,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        if (employeeId == null) {
            redirectAttributes.addFlashAttribute("error", "Please select an employee.");
            return "redirect:/manager/feedback/add";
        }
        if (notes == null || notes.trim().isEmpty() || notes.trim().length() < 5) {
            redirectAttributes.addFlashAttribute("error", "Feedback notes are required (minimum 5 characters).");
            return "redirect:/manager/feedback/add";
        }

        try {
            Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
            if (!employeeOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Employee not found!");
                return "redirect:/manager/feedback/add";
            }

            Feedback feedback = new Feedback();
            feedback.setEmployee(employeeOpt.get());
            Optional<User> managerUser = userRepository.findById(user.getId());
            managerUser.ifPresent(feedback::setManager);
            feedback.setNotes(notes.trim());
            feedback.setImprovementSuggestions(improvementSuggestions != null ? improvementSuggestions.trim() : null);

            if (followUpDate != null && !followUpDate.trim().isEmpty()) {
                LocalDate fDate = LocalDate.parse(followUpDate.trim());
                if (fDate.isBefore(LocalDate.now())) {
                    redirectAttributes.addFlashAttribute("error", "Follow-up date cannot be in the past.");
                    return "redirect:/manager/feedback/add";
                }
                feedback.setFollowUpDate(fDate.atStartOfDay());
            }

            feedbackRepository.save(feedback);

            redirectAttributes.addFlashAttribute("success", "Feedback added successfully!");
            return "redirect:/manager/dashboard?tab=feedback";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error adding feedback: " + e.getMessage());
            return "redirect:/manager/feedback/add";
        }
    }

    @RequestMapping(value = "/badge/award", method = RequestMethod.GET)
    public String showAwardBadgeForm(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        List<Employee> allEmployees = employeeRepository.findAll();
        model.addAttribute("employees", allEmployees);

        return "manager-award-badge";
    }

    @RequestMapping(value = "/badge/award", method = RequestMethod.POST)
    public String awardBadge(@RequestParam Long employeeId,
            @RequestParam String badgeName,
            @RequestParam(required = false) String description,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        if (employeeId == null) {
            redirectAttributes.addFlashAttribute("error", "Please select an employee.");
            return "redirect:/manager/badge/award";
        }
        if (badgeName == null || badgeName.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Badge name is required.");
            return "redirect:/manager/badge/award";
        }

        try {
            Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
            if (!employeeOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Employee not found!");
                return "redirect:/manager/badge/award";
            }

            TeamBadge badge = new TeamBadge();
            badge.setEmployee(employeeOpt.get());
            badge.setBadgeName(badgeName.trim());
            badge.setAppreciationNote(description != null ? description.trim() : "");
            Optional<User> managerUser = userRepository.findById(user.getId());
            managerUser.ifPresent(badge::setAwardedBy);
            badge.setAwardedDate(LocalDateTime.now());
            badge.setIsVisible(true);

            teamBadgeRepository.save(badge);

            redirectAttributes.addFlashAttribute("success", "Badge awarded successfully!");
            return "redirect:/manager/dashboard?tab=recognition";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error awarding badge: " + e.getMessage());
            return "redirect:/manager/badge/award";
        }
    }

    @RequestMapping(value = "/feedback/view/{id}", method = RequestMethod.GET)
    public String viewFeedback(@PathVariable Long id, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        Optional<Feedback> feedbackOpt = feedbackRepository.findById(id);
        if (!feedbackOpt.isPresent()) {
            return "redirect:/manager/dashboard?tab=feedback";
        }

        model.addAttribute("feedback", feedbackOpt.get());
        return "manager-view-feedback";
    }

    @RequestMapping(value = "/rule/create", method = RequestMethod.GET)
    public String showCreateRuleForm(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        return "manager-create-rule";
    }

    @RequestMapping(value = "/rule/create", method = RequestMethod.POST)
    public String createRule(@RequestParam String ruleName,
            @RequestParam String description,
            @RequestParam String ruleType,
            @RequestParam(required = false) String value,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        if (ruleName == null || ruleName.trim().isEmpty() || ruleName.trim().length() < 3) {
            redirectAttributes.addFlashAttribute("error", "Rule name is required (minimum 3 characters).");
            return "redirect:/manager/rule/create";
        }
        if (description == null || description.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Description is required.");
            return "redirect:/manager/rule/create";
        }
        if (ruleType == null || ruleType.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Rule type is required.");
            return "redirect:/manager/rule/create";
        }

        try {
            TeamRule rule = new TeamRule();
            rule.setRuleName(ruleName.trim());
            rule.setDescription(description.trim());
            rule.setRuleType(ruleType.trim());
            rule.setRuleValue(value != null ? value.trim() : null);
            rule.setIsActive(true);

            Optional<User> managerUser = userRepository.findById(user.getId());
            managerUser.ifPresent(rule::setCreatedBy);

            teamRuleRepository.save(rule);

            redirectAttributes.addFlashAttribute("success", "Rule created successfully!");
            return "redirect:/manager/dashboard?tab=rules";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating rule: " + e.getMessage());
            return "redirect:/manager/rule/create";
        }
    }

    @RequestMapping(value = "/rule/edit/{id}", method = RequestMethod.GET)
    public String showEditRuleForm(@PathVariable Long id, Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        Optional<TeamRule> ruleOpt = teamRuleRepository.findById(id);
        if (!ruleOpt.isPresent()) {
            return "redirect:/manager/dashboard?tab=rules";
        }

        model.addAttribute("rule", ruleOpt.get());

        return "manager-edit-rule";
    }

    @RequestMapping(value = "/rule/update/{id}", method = RequestMethod.POST)
    public String updateRule(@PathVariable Long id,
            @RequestParam String ruleName,
            @RequestParam String description,
            @RequestParam String ruleType,
            @RequestParam(required = false) String value,
            @RequestParam(required = false, defaultValue = "false") String isActive,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        if (ruleName == null || ruleName.trim().isEmpty() || ruleName.trim().length() < 3) {
            redirectAttributes.addFlashAttribute("error", "Rule name is required (minimum 3 characters).");
            return "redirect:/manager/rule/edit/" + id;
        }
        if (description == null || description.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Description is required.");
            return "redirect:/manager/rule/edit/" + id;
        }
        if (ruleType == null || ruleType.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Rule type is required.");
            return "redirect:/manager/rule/edit/" + id;
        }

        try {
            Optional<TeamRule> ruleOpt = teamRuleRepository.findById(id);
            if (!ruleOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Rule not found!");
                return "redirect:/manager/dashboard";
            }

            TeamRule rule = ruleOpt.get();
            rule.setRuleName(ruleName.trim());
            rule.setDescription(description.trim());
            rule.setRuleType(ruleType.trim());
            rule.setRuleValue(value != null ? value.trim() : null);
            // Checkbox sends "true" if checked, null if unchecked
            rule.setIsActive("true".equals(isActive));

            teamRuleRepository.save(rule);

            redirectAttributes.addFlashAttribute("success", "Rule updated successfully!");
            return "redirect:/manager/dashboard?tab=rules";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating rule: " + e.getMessage());
            return "redirect:/manager/rule/edit/" + id;
        }
    }

    @RequestMapping(value = "/rule/delete/{id}", method = RequestMethod.POST)
    public String deleteRule(@PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        try {
            Optional<TeamRule> ruleOpt = teamRuleRepository.findById(id);
            if (!ruleOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Rule not found!");
                return "redirect:/manager/dashboard?tab=rules";
            }

            teamRuleRepository.delete(ruleOpt.get());

            redirectAttributes.addFlashAttribute("success", "Rule deleted successfully!");
            return "redirect:/manager/dashboard?tab=rules";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting rule: " + e.getMessage());
            return "redirect:/manager/dashboard?tab=rules";
        }
    }

    @RequestMapping(value = "/suggestion/respond/{id}", method = RequestMethod.POST)
    public String respondToSuggestion(@PathVariable Long id,
            @RequestParam String response,
            @RequestParam String status,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        if (response == null || response.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Response message cannot be empty.");
            return "redirect:/manager/dashboard?tab=suggestions";
        }
        if (status == null || status.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Status is required.");
            return "redirect:/manager/dashboard?tab=suggestions";
        }

        try {
            Optional<com.example.demo.model.Suggestion> suggestionOpt = suggestionRepository.findById(id);
            if (!suggestionOpt.isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Suggestion not found!");
                return "redirect:/manager/dashboard";
            }

            com.example.demo.model.Suggestion suggestion = suggestionOpt.get();
            suggestion.setResponse(response);
            suggestion.setStatus(status);
            suggestion.setRespondedBy(user.getName());
            suggestion.setRespondedAt(LocalDateTime.now());

            suggestionRepository.save(suggestion);

            redirectAttributes.addFlashAttribute("success", "Response submitted successfully!");
            return "redirect:/manager/dashboard?tab=suggestions";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error responding to suggestion: " + e.getMessage());
            return "redirect:/manager/dashboard?tab=suggestions";
        }

    }

    @RequestMapping(value = "/meetings/create", method = RequestMethod.GET)
    public String showCreateMeetingForm(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        List<Employee> allEmployees = employeeRepository.findAll();
        model.addAttribute("employees", allEmployees);
        // LOG-MGR-003: Provide real-time team availability for meeting scheduling
        model.addAttribute("employeeAvailability", calculateEmployeeAvailability(allEmployees));

        return "manager-create-meeting";
    }

    @RequestMapping(value = "/meetings/create", method = RequestMethod.POST)
    public String createTeamMeeting(@RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String date,
            @RequestParam String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String meetingType,
            @RequestParam(required = false) List<Long> employeeIds,
            @RequestParam(required = false, defaultValue = "false") boolean addEveryone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        // LOG-MGR-006: Meeting form validations
        if (title == null || title.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Meeting title is required.");
            return "redirect:/manager/meetings/create";
        }
        if (date == null || date.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Meeting date is required.");
            return "redirect:/manager/meetings/create";
        }
        LocalDate meetingDate;
        try {
            meetingDate = LocalDate.parse(date.trim());
            if (meetingDate.isBefore(LocalDate.now())) {
                redirectAttributes.addFlashAttribute("error", "Meeting date cannot be in the past.");
                return "redirect:/manager/meetings/create";
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Invalid date format.");
            return "redirect:/manager/meetings/create";
        }
        if (startTime == null || startTime.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Start time is required.");
            return "redirect:/manager/meetings/create";
        }
        if (!addEveryone && (employeeIds == null || employeeIds.isEmpty())) {
            redirectAttributes.addFlashAttribute("error", "Please select at least one attendee or check 'Invite All Active Employees'.");
            return "redirect:/manager/meetings/create";
        }

        try {
            Meeting meeting = new Meeting();
            meeting.setTitle(title.trim());
            meeting.setDescription(description != null ? description.trim() : null);
            meeting.setStatus("SCHEDULED");
            if (meetingType != null && !meetingType.isEmpty()) {
                meeting.setMeetingType(meetingType);
            }

            // Handle Date and Time
            LocalDateTime startDateTime = meetingDate.atTime(java.time.LocalTime.parse(startTime.trim()));
            meeting.setStartTime(startDateTime);

            if (endTime != null && !endTime.trim().isEmpty()) {
                LocalDateTime endDateTime = meetingDate.atTime(java.time.LocalTime.parse(endTime.trim()));
                if (!endDateTime.isAfter(startDateTime)) {
                    redirectAttributes.addFlashAttribute("error", "End time must be after start time.");
                    return "redirect:/manager/meetings/create";
                }
                meeting.setEndTime(endDateTime);
            }

            // Handle Attendees
            List<Employee> attendees = new ArrayList<>();
            if (addEveryone) {
                attendees = employeeRepository.findAll().stream()
                        .filter(emp -> emp.getIsActive() == null || emp.getIsActive())
                        .collect(Collectors.toList());
            } else if (employeeIds != null && !employeeIds.isEmpty()) {
                attendees = employeeRepository.findAllById(employeeIds);
            }
            meeting.setAttendees(attendees);

            meetingRepository.save(meeting);

            // Create Notifications for Attendees
            for (Employee attendee : attendees) {
                String message = "New Meeting: " + title + " on " + meetingDate + " at " + startTime;
                com.example.demo.model.Notification notification = new com.example.demo.model.Notification(attendee,
                        message, meeting.getId());
                notificationRepository.save(notification);
            }

            redirectAttributes.addFlashAttribute("success",
                    "Team meeting scheduled successfully with " + attendees.size() + " attendees!");
            return "redirect:/manager/dashboard?tab=planning";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating meeting: " + e.getMessage());
            return "redirect:/manager/meetings/create";
        }
    }

    @RequestMapping(value = "/notification/read/{id}", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> markNotificationAsRead(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return ResponseEntity.badRequest().body("Unauthorized");
        }

        Optional<Notification> notifOpt = notificationRepository.findById(id);
        if (notifOpt.isPresent()) {
            Notification notification = notifOpt.get();
            // Ensure this notification belongs to the current user
            if (notification.getUserRecipient() != null
                    && notification.getUserRecipient().getId().equals(user.getId())) {
                notification.setIsRead(true);
                notificationRepository.save(notification);
                return ResponseEntity.ok("Marked as read");
            }
        }
        return ResponseEntity.badRequest().body("Notification not found or unauthorized");
    }

    @RequestMapping(value = "/api/meetings/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<?> getMeetingDetails(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return ResponseEntity.badRequest().body("Unauthorized");
        }
        Optional<Meeting> meetingOpt = meetingRepository.findById(id);
        if (meetingOpt.isPresent()) {
            return ResponseEntity.ok(meetingOpt.get());
        }
        return ResponseEntity.badRequest().body("Meeting not found");
    }

    @GetMapping("/notifications/history")
    public String viewNotificationHistory(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"MANAGER".equals(user.getRole())) {
            return "redirect:/manager/login";
        }

        List<Notification> notifications = notificationRepository.findByUserRecipientOrderByCreatedAtDesc(user);
        model.addAttribute("notifications", notifications);
        model.addAttribute("userName", user.getName());

        return "manager-notifications";
    }
}
