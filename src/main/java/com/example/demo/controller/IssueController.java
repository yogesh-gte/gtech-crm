package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/issues")
public class IssueController {

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private IssueCommentRepository issueCommentRepository;

    @Autowired
    private IssueActivityRepository issueActivityRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private IssueAttachmentRepository issueAttachmentRepository;

    @Autowired
    private com.example.demo.service.EmailService emailService;

    @Autowired
    private com.example.demo.service.FileStorageService fileStorageService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("user") != null || session.getAttribute("employee") != null;
    }

    private boolean canCreateIssue(HttpSession session) {
        // Only Admin, Manager, HR (user login) can create. Employees (employee login) cannot.
        if (session.getAttribute("user") == null) {
            return false; // Employee login has "employee", not "user"
        }
        String role = (String) session.getAttribute("userRole");
        return role != null && (role.equals("ADMIN") || role.equals("MANAGER") || role.equals("HR"));
    }

    private boolean canEditOrUpdateIssue(Issue issue, HttpSession session) {
        String role = (String) session.getAttribute("userRole");
        if (role != null && (role.equals("ADMIN") || role.equals("MANAGER") || role.equals("HR"))) {
            return true;
        }
        Object emp = session.getAttribute("employee");
        if (emp instanceof Employee e) {
            return (issue.getAssignee() != null && issue.getAssignee().getId().equals(e.getId()))
                    || (issue.getReporter() != null && issue.getReporter().getId().equals(e.getId()));
        }
        return false;
    }

    private String getCurrentUserName(HttpSession session) {
        Object name = session.getAttribute("userName") != null ? session.getAttribute("userName") : session.getAttribute("employeeName");
        return name != null ? name.toString() : "Unknown";
    }

    private String getCurrentUserRole(HttpSession session) {
        Object role = session.getAttribute("userRole");
        return role != null ? role.toString() : "EMPLOYEE";
    }

    private void logActivity(Issue issue, String activityType, String fieldName, String oldVal, String newVal, HttpSession session) {
        IssueActivity act = new IssueActivity(issue, activityType, fieldName, oldVal, newVal, getCurrentUserName(session), getCurrentUserRole(session));
        issueActivityRepository.save(act);
    }

    private void notifyAssigneeAndReporter(Issue issue, String message, String changedBy) {
        if (issue.getAssignee() != null) {
            Notification n = new Notification();
            n.setRecipient(issue.getAssignee());
            n.setMessage(message);
            notificationRepository.save(n);
        }
        if (issue.getReporter() != null && (issue.getAssignee() == null || !issue.getAssignee().getId().equals(issue.getReporter().getId()))) {
            Notification n = new Notification();
            n.setRecipient(issue.getReporter());
            n.setMessage(message);
            notificationRepository.save(n);
        }
    }


    @GetMapping
    public String listIssues(@RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Model model, HttpSession session) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        List<Issue> issues;
        if (projectId != null) {
            Optional<Project> proj = projectRepository.findById(projectId);
            issues = proj.map(issueRepository::findByProjectOrderByCreatedAtDesc).orElse(List.of());
        } else {
            issues = issueRepository.findAllWithDetails();
        }
        if (issueType != null && !issueType.isBlank()) {
            issues = issues.stream().filter(i -> issueType.equals(i.getIssueType())).toList();
        }
        if (status != null && !status.isBlank()) {
            issues = issues.stream().filter(i -> status.equals(i.getStatus())).toList();
        }
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase().trim();
            issues = issues.stream().filter(i ->
                (i.getIssueKey() != null && i.getIssueKey().toLowerCase().contains(q))
                || (i.getTitle() != null && i.getTitle().toLowerCase().contains(q))
                || (i.getDescription() != null && i.getDescription().toLowerCase().contains(q))
            ).toList();
        }
        List<Project> projects = projectRepository.findAll();
        model.addAttribute("userName", session.getAttribute("userName") != null ? session.getAttribute("userName") : session.getAttribute("employeeName"));
        model.addAttribute("issues", issues);
        model.addAttribute("projects", projects);
        model.addAttribute("selectedProjectId", projectId);
        model.addAttribute("selectedIssueType", issueType);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("searchQuery", search);
        model.addAttribute("canCreateIssue", canCreateIssue(session));
        java.util.Set<Long> editableIds = new java.util.HashSet<>();
        Object emp = session.getAttribute("employee");
        if (emp instanceof Employee e) {
            for (Issue i : issues) {
                if ((i.getAssignee() != null && i.getAssignee().getId().equals(e.getId()))
                        || (i.getReporter() != null && i.getReporter().getId().equals(e.getId()))) {
                    editableIds.add(i.getId());
                }
            }
        }
        model.addAttribute("editableIssueIds", editableIds);
        return "issue-list";
    }

    @GetMapping("/audit")
    public String auditLog(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        List<IssueActivity> activities = issueActivityRepository.findTop500ByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, 500));
        model.addAttribute("userName", session.getAttribute("userName") != null ? session.getAttribute("userName") : session.getAttribute("employeeName"));
        model.addAttribute("activities", activities);
        return "issue-audit";
    }

    @GetMapping("/add")
    public String showAddIssue(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        if (!canCreateIssue(session)) {
            redirectAttributes.addFlashAttribute("error", "Only Admin, Manager or HR can create issues.");
            return "redirect:/issues";
        }
        model.addAttribute("userName", session.getAttribute("userName") != null ? session.getAttribute("userName") : session.getAttribute("employeeName"));
        model.addAttribute("issue", new Issue());
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("employees", employeeRepository.findAll().stream()
                .filter(e -> e.getIsActive() == null || e.getIsActive()).toList());
        model.addAttribute("parentIssues", List.<Issue>of());
        return "issue-form";
    }

    @GetMapping("/edit/{id}")
    public String showEditIssue(@PathVariable Long id, Model model, HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        Optional<Issue> opt = issueRepository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Issue not found");
            return "redirect:/issues";
        }
        Issue issue = opt.get();
        if (!canEditOrUpdateIssue(issue, session)) {
            redirectAttributes.addFlashAttribute("error", "You do not have permission to edit this issue");
            return "redirect:/issues/view/" + id;
        }
        model.addAttribute("userName", session.getAttribute("userName") != null ? session.getAttribute("userName") : session.getAttribute("employeeName"));
        model.addAttribute("issue", issue);
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("employees", employeeRepository.findAll().stream()
                .filter(e -> e.getIsActive() == null || e.getIsActive()).toList());
        List<Issue> parentIssues = issue.getProject() != null
                ? issueRepository.findByProject(issue.getProject()).stream()
                        .filter(i -> !"SUBTASK".equals(i.getIssueType())).toList()
                : List.of();
        model.addAttribute("parentIssues", parentIssues);
        return "issue-form";
    }

    @Transactional
    @PostMapping("/save")
    public String saveIssue(@RequestParam(required = false) Long id,
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String issueType,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String stepsToReproduce,
            @RequestParam(required = false) String environment,
            @RequestParam Long projectId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long reporterId,
            @RequestParam(required = false) Long parentIssueId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String resolution,
            @RequestParam(required = false) String dueDateStr,
            RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        if (id == null && !canCreateIssue(session)) {
            redirectAttributes.addFlashAttribute("error", "Only Admin, Manager or HR can create issues.");
            return "redirect:/issues";
        }
        if (title == null || title.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Issue title is required.");
            return id != null ? "redirect:/issues/edit/" + id : "redirect:/issues/add";
        }
        if (projectId == null) {
            redirectAttributes.addFlashAttribute("error", "Project is required.");
            return id != null ? "redirect:/issues/edit/" + id : "redirect:/issues/add";
        }
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Selected project was not found.");
            return id != null ? "redirect:/issues/edit/" + id : "redirect:/issues/add";
        }
        Project project = projectOpt.get();
        Issue issue;
        if (id != null) {
            issue = issueRepository.findById(id).orElse(null);
            if (issue == null) {
                redirectAttributes.addFlashAttribute("error", "Issue not found");
                return "redirect:/issues";
            }
            if (!canEditOrUpdateIssue(issue, session)) {
                redirectAttributes.addFlashAttribute("error", "You do not have permission to edit this issue");
                return "redirect:/issues/view/" + id;
            }
        } else {
            issue = new Issue();
            String issueKey = generateIssueKey(project);
            issue.setIssueKey(issueKey);
            // Bugs start in TRIAGE for triage flow
            if ("BUG".equals(issueType)) {
                issue.setStatus("TRIAGE");
            }
        }
        issue.setTitle(title.trim());
        issue.setDescription(description != null ? description.trim() : null);
        issue.setIssueType(issueType);
        issue.setPriority(priority != null && !priority.isBlank() ? priority : "MEDIUM");
        issue.setSeverity(severity != null && !severity.isBlank() ? severity : null);
        issue.setStepsToReproduce(stepsToReproduce != null && !stepsToReproduce.isBlank() ? stepsToReproduce.trim() : null);
        issue.setEnvironment(environment != null && !environment.isBlank() ? environment.trim() : null);
        issue.setProject(project);
        Employee oldAssignee = issue.getAssignee();
        Employee newAssignee = assigneeId != null ? employeeRepository.findById(assigneeId).orElse(null) : null;
        issue.setAssignee(newAssignee);
        issue.setReporter(reporterId != null ? employeeRepository.findById(reporterId).orElse(null) : null);
        issue.setParentIssue(parentIssueId != null ? issueRepository.findById(parentIssueId).orElse(null) : null);
        String oldStatus = issue.getStatus();
        if (status != null && !status.isBlank()) issue.setStatus(status);
        // New bugs must stay in TRIAGE - form may send OPEN, don't override
        if (id == null && "BUG".equals(issueType)) {
            issue.setStatus("TRIAGE");
        }
        if (resolution != null && !resolution.isBlank()) issue.setResolution(resolution);
        else if (!"DONE".equals(issue.getStatus()) && !"CLOSED".equals(issue.getStatus())) issue.setResolution(null);
        if (dueDateStr != null && !dueDateStr.isBlank()) {
            try {
                issue.setDueDate(LocalDate.parse(dueDateStr));
            } catch (Exception ignored) {}
        }
        issueRepository.save(issue);
        if (id != null) {
            if (oldStatus != null && !oldStatus.equals(issue.getStatus())) {
                logActivity(issue, "STATUS_CHANGE", "status", oldStatus, issue.getStatus(), session);
            }
            boolean assigneeChanged = (oldAssignee == null) != (newAssignee == null)
                    || (oldAssignee != null && newAssignee != null && !oldAssignee.getId().equals(newAssignee.getId()));
            if (assigneeChanged) {
                logActivity(issue, "ASSIGNEE_CHANGE", "assignee",
                        oldAssignee != null ? oldAssignee.getName() : null,
                        newAssignee != null ? newAssignee.getName() : null, session);
                String assignMsg = "Issue " + issue.getIssueKey() + " assigned to " + (newAssignee != null ? newAssignee.getName() : "Unassigned") + " by " + getCurrentUserName(session);
                notifyAssigneeAndReporter(issue, assignMsg, getCurrentUserName(session));
                if (newAssignee != null && newAssignee.getEmail() != null && !newAssignee.getEmail().isEmpty()) {
                    emailService.sendIssueAssignedNotification(newAssignee.getEmail(), newAssignee.getName(),
                            issue.getIssueKey(), issue.getTitle(), getCurrentUserName(session));
                }
            }
        } else {
            logActivity(issue, "CREATED", null, null, null, session);
            String createdMsg = "New issue " + issue.getIssueKey() + " created: " + issue.getTitle();
            notifyAssigneeAndReporter(issue, createdMsg, getCurrentUserName(session));
            if (issue.getAssignee() != null && issue.getAssignee().getEmail() != null && !issue.getAssignee().getEmail().isEmpty()) {
                emailService.sendIssueAssignedNotification(issue.getAssignee().getEmail(), issue.getAssignee().getName(),
                        issue.getIssueKey(), issue.getTitle(), getCurrentUserName(session));
            }
        }
        redirectAttributes.addFlashAttribute("success", "Issue saved successfully");
        return "redirect:/issues/view/" + issue.getId();
    }

    private String generateIssueKey(Project project) {
        String prefix = project.getProjectCode() != null && !project.getProjectCode().isBlank()
                ? project.getProjectCode() : "PRJ";
        long count = issueRepository.countByProject(project);
        return prefix + "-" + (count + 1);
    }

    @GetMapping("/view/{id}")
    public String viewIssue(@PathVariable Long id, Model model, HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        Optional<Issue> opt = issueRepository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Issue not found");
            return "redirect:/issues";
        }
        Issue issue = opt.get();
        List<IssueComment> comments = issueCommentRepository.findByIssueOrderByCreatedAtAsc(issue);
        List<IssueActivity> activities = issueActivityRepository.findByIssueOrderByCreatedAtDesc(issue);
        List<IssueAttachment> attachments = issueAttachmentRepository.findByIssue(issue);
        model.addAttribute("canUpdateStatus", canEditOrUpdateIssue(issue, session));
        model.addAttribute("userName", session.getAttribute("userName") != null ? session.getAttribute("userName") : session.getAttribute("employeeName"));
        model.addAttribute("issue", issue);
        model.addAttribute("comments", comments);
        model.addAttribute("activities", activities);
        model.addAttribute("attachments", attachments);
        model.addAttribute("userRole", session.getAttribute("userRole") != null ? session.getAttribute("userRole") : "EMPLOYEE");
        model.addAttribute("employee", session.getAttribute("employee"));
        return "issue-view";
    }

    @Transactional
    @PostMapping("/comment")
    public String addComment(@RequestParam Long issueId,
            @RequestParam String content,
            RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        Issue issue = issueRepository.findById(issueId).orElse(null);
        if (issue == null) {
            redirectAttributes.addFlashAttribute("error", "Issue not found");
            return "redirect:/issues";
        }
        String authorName = (String) (session.getAttribute("userName") != null ? session.getAttribute("userName") : session.getAttribute("employeeName"));
        String authorRole = (String) (session.getAttribute("userRole") != null ? session.getAttribute("userRole") : "EMPLOYEE");
        if (authorName == null) authorName = "Unknown";
        if (authorRole == null) authorRole = "EMPLOYEE";
        IssueComment comment = new IssueComment(issue, authorName, authorRole, content.trim());
        issueCommentRepository.save(comment);
        String preview = content.trim().substring(0, Math.min(100, content.trim().length())) + (content.trim().length() > 100 ? "..." : "");
        logActivity(issue, "COMMENT_ADDED", null, null, preview, session);
        String commentMsg = "New comment on " + issue.getIssueKey() + " by " + authorName;
        notifyAssigneeAndReporter(issue, commentMsg, authorName);
        if (issue.getAssignee() != null && issue.getAssignee().getEmail() != null && !issue.getAssignee().getEmail().isEmpty()) {
            emailService.sendIssueCommentNotification(issue.getAssignee().getEmail(), issue.getAssignee().getName(),
                    issue.getIssueKey(), content.trim(), authorName);
        }
        if (issue.getReporter() != null && (issue.getAssignee() == null || !issue.getAssignee().getId().equals(issue.getReporter().getId()))
                && issue.getReporter().getEmail() != null && !issue.getReporter().getEmail().isEmpty()) {
            emailService.sendIssueCommentNotification(issue.getReporter().getEmail(), issue.getReporter().getName(),
                    issue.getIssueKey(), content.trim(), authorName);
        }
        redirectAttributes.addFlashAttribute("success", "Comment added");
        return "redirect:/issues/view/" + issueId;
    }

    @Transactional
    @PostMapping("/status")
    public String updateStatus(@RequestParam Long issueId,
            @RequestParam String status,
            RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isLoggedIn(session)) {
            return "redirect:/login";
        }
        Issue issue = issueRepository.findById(issueId).orElse(null);
        if (issue == null) {
            redirectAttributes.addFlashAttribute("error", "Issue not found");
            return "redirect:/issues";
        }
        if (!canEditOrUpdateIssue(issue, session)) {
            redirectAttributes.addFlashAttribute("error", "You do not have permission to update this issue's status");
            return "redirect:/issues/view/" + issueId;
        }
        String oldStatus = issue.getStatus();
        issue.setStatus(status);
        issueRepository.save(issue);
        logActivity(issue, "STATUS_CHANGE", "status", oldStatus, status, session);
        String msg = "Issue " + issue.getIssueKey() + " status changed: " + oldStatus + " → " + status + " by " + getCurrentUserName(session);
        notifyAssigneeAndReporter(issue, msg, getCurrentUserName(session));
        if (issue.getAssignee() != null && issue.getAssignee().getEmail() != null && !issue.getAssignee().getEmail().isEmpty()) {
            emailService.sendIssueStatusChangeNotification(issue.getAssignee().getEmail(), issue.getAssignee().getName(),
                    issue.getIssueKey(), oldStatus, status, getCurrentUserName(session));
        }
        if (issue.getReporter() != null && (issue.getAssignee() == null || !issue.getAssignee().getId().equals(issue.getReporter().getId()))
                && issue.getReporter().getEmail() != null && !issue.getReporter().getEmail().isEmpty()) {
            emailService.sendIssueStatusChangeNotification(issue.getReporter().getEmail(), issue.getReporter().getName(),
                    issue.getIssueKey(), oldStatus, status, getCurrentUserName(session));
        }
        redirectAttributes.addFlashAttribute("success", "Status updated");
        return "redirect:/issues/view/" + issueId;
    }

    @Transactional
    @PostMapping("/attachment")
    public String addAttachment(@RequestParam Long issueId,
            @RequestParam MultipartFile file,
            RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/login";
        Issue issue = issueRepository.findById(issueId).orElse(null);
        if (issue == null) {
            redirectAttributes.addFlashAttribute("error", "Issue not found");
            return "redirect:/issues";
        }
        if (!canEditOrUpdateIssue(issue, session)) {
            redirectAttributes.addFlashAttribute("error", "You do not have permission to add attachments");
            return "redirect:/issues/view/" + issueId;
        }
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a file");
            return "redirect:/issues/view/" + issueId;
        }
        try {
            com.example.demo.service.FileStorageService.StoredFileInfo storedInfo = fileStorageService.storeFile(
                    file, "issues", com.example.demo.service.FileStorageService.ALLOWED_ISSUE_EXTENSIONS,
                    com.example.demo.service.FileStorageService.MAX_ISSUE_FILE_SIZE);

            IssueAttachment att = new IssueAttachment();
            att.setIssue(issue);
            att.setFileName(storedInfo.originalFilename());
            att.setFilePath("/attachments/issues/" + storedInfo.storedFilename());
            att.setFileSize(storedInfo.fileSize());
            att.setUploadedBy(getCurrentUserName(session));
            issueAttachmentRepository.save(att);
            redirectAttributes.addFlashAttribute("success", "Attachment added successfully");
        } catch (IllegalArgumentException | SecurityException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid file: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to upload: " + e.getMessage());
        }
        return "redirect:/issues/view/" + issueId;
    }

    @GetMapping("/triage")
    public String bugTriage(@RequestParam(required = false) Long projectId, Model model, HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/login";
        if (!canCreateIssue(session)) {
            return "redirect:/issues";
        }
        List<Issue> bugs = issueRepository.findAllWithDetails().stream()
                .filter(i -> "BUG".equals(i.getIssueType()))
                .filter(i -> "TRIAGE".equals(i.getStatus()) || (i.getAssignee() == null && "OPEN".equals(i.getStatus())))
                .toList();
        if (projectId != null) {
            bugs = bugs.stream().filter(i -> i.getProject() != null && i.getProject().getId().equals(projectId)).toList();
        }
        List<Project> projects = projectRepository.findAll();
        model.addAttribute("userName", session.getAttribute("userName") != null ? session.getAttribute("userName") : session.getAttribute("employeeName"));
        model.addAttribute("bugs", bugs);
        model.addAttribute("projects", projects);
        model.addAttribute("selectedProjectId", projectId);
        model.addAttribute("employees", employeeRepository.findAll().stream()
                .filter(e -> e.getIsActive() == null || e.getIsActive()).toList());
        return "issue-triage";
    }
}
