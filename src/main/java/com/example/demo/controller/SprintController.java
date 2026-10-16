package com.example.demo.controller;

import com.example.demo.model.*;
import com.example.demo.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/sprints")
public class SprintController {

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private IssueRepository issueRepository;

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("user") != null || session.getAttribute("employee") != null;
    }

    @GetMapping
    public String listSprints(@RequestParam(required = false) Long projectId, Model model, HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/login";
        List<Sprint> sprints = projectId != null
                ? sprintRepository.findByProjectIdOrderByStartDateDesc(projectId)
                : sprintRepository.findAll();
        List<Project> projects = projectRepository.findAll();
        model.addAttribute("sprints", sprints);
        model.addAttribute("projects", projects);
        model.addAttribute("selectedProjectId", projectId);
        return "sprint-list";
    }

    @GetMapping("/add")
    public String showAddSprint(@RequestParam(required = false) Long projectId, Model model, HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/login";
        model.addAttribute("sprint", new Sprint());
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("selectedProjectId", projectId);
        return "sprint-form";
    }

    @GetMapping("/edit/{id}")
    public String showEditSprint(@PathVariable Long id, Model model, HttpSession session,
            RedirectAttributes ra) {
        if (!isLoggedIn(session)) return "redirect:/login";
        Optional<Sprint> opt = sprintRepository.findById(id);
        if (opt.isEmpty()) {
            ra.addFlashAttribute("error", "Sprint not found");
            return "redirect:/sprints";
        }
        model.addAttribute("sprint", opt.get());
        model.addAttribute("projects", projectRepository.findAll());
        return "sprint-form";
    }

    @PostMapping("/save")
    public String saveSprint(@RequestParam(required = false) Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String goal,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String startDateStr,
            @RequestParam(required = false) String endDateStr,
            RedirectAttributes ra, HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/login";

        if (name == null || name.trim().isEmpty()) {
            ra.addFlashAttribute("error", "Sprint name is required");
            return id != null ? "redirect:/sprints/edit/" + id : "redirect:/sprints/add";
        }
        if (projectId == null) {
            ra.addFlashAttribute("error", "Please select a project");
            return id != null ? "redirect:/sprints/edit/" + id : "redirect:/sprints/add";
        }
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            ra.addFlashAttribute("error", "Selected project not found");
            return id != null ? "redirect:/sprints/edit/" + id : "redirect:/sprints/add";
        }
        if (startDateStr == null || startDateStr.isBlank() || endDateStr == null || endDateStr.isBlank()) {
            ra.addFlashAttribute("error", "Both Start Date and End Date are required");
            return id != null ? "redirect:/sprints/edit/" + id : "redirect:/sprints/add";
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startDateStr.trim());
            end = LocalDate.parse(endDateStr.trim());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Invalid date format");
            return id != null ? "redirect:/sprints/edit/" + id : "redirect:/sprints/add";
        }

        if (end.isBefore(start)) {
            ra.addFlashAttribute("error", "End date cannot be before start date");
            return id != null ? "redirect:/sprints/edit/" + id : "redirect:/sprints/add";
        }

        Sprint sprint;
        if (id != null) {
            sprint = sprintRepository.findById(id).orElse(new Sprint());
        } else {
            sprint = new Sprint();
        }
        sprint.setName(name.trim());
        sprint.setGoal(goal != null && !goal.isBlank() ? goal.trim() : null);
        sprint.setProject(projectOpt.get());
        sprint.setStartDate(start);
        sprint.setEndDate(end);
        if (sprint.getStatus() == null) sprint.setStatus("PLANNING");
        sprintRepository.save(sprint);
        ra.addFlashAttribute("success", "Sprint saved successfully");
        return "redirect:/sprints/board/" + sprint.getId();
    }

    @GetMapping("/board/{id}")
    public String sprintBoard(@PathVariable Long id, Model model, HttpSession session,
            RedirectAttributes ra) {
        if (!isLoggedIn(session)) return "redirect:/login";
        Optional<Sprint> opt = sprintRepository.findById(id);
        if (opt.isEmpty()) {
            ra.addFlashAttribute("error", "Sprint not found");
            return "redirect:/sprints";
        }
        Sprint sprint = opt.get();
        List<Issue> issues = issueRepository.findBySprintOrderByStatusAscCreatedAtAsc(sprint);
        Map<String, List<Issue>> byStatus = issues.stream()
                .collect(Collectors.groupingBy(i -> i.getStatus() != null ? i.getStatus() : "OPEN"));
        List<Issue> triageList = byStatus.getOrDefault("TRIAGE", List.of());
        List<Issue> openList = new java.util.ArrayList<>(byStatus.getOrDefault("OPEN", List.of()));
        openList.addAll(0, triageList); // TRIAGE and OPEN both in To Do column
        List<Issue> inProgressList = byStatus.getOrDefault("IN_PROGRESS", List.of());
        List<Issue> inReviewList = byStatus.getOrDefault("IN_REVIEW", List.of());
        List<Issue> testingList = byStatus.getOrDefault("TESTING", List.of());
        List<Issue> doneList = byStatus.getOrDefault("DONE", List.of());
        List<Issue> closedList = byStatus.getOrDefault("CLOSED", List.of());
        List<Issue> backlog = issueRepository.findByProjectAndSprintIsNull(sprint.getProject());
        model.addAttribute("sprint", sprint);
        model.addAttribute("openList", openList);
        model.addAttribute("inProgressList", inProgressList);
        model.addAttribute("inReviewList", inReviewList);
        model.addAttribute("testingList", testingList);
        model.addAttribute("doneList", doneList);
        model.addAttribute("closedList", closedList);
        model.addAttribute("backlog", backlog);
        return "sprint-board";
    }

    @Transactional
    @PostMapping("/start/{id}")
    public String startSprint(@PathVariable Long id, RedirectAttributes ra, HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/login";
        Sprint sprint = sprintRepository.findById(id).orElse(null);
        if (sprint == null) {
            ra.addFlashAttribute("error", "Sprint not found");
            return "redirect:/sprints";
        }
        sprint.setStatus("ACTIVE");
        sprintRepository.save(sprint);
        ra.addFlashAttribute("success", "Sprint started");
        return "redirect:/sprints/board/" + id;
    }

    @Transactional
    @PostMapping("/close/{id}")
    public String closeSprint(@PathVariable Long id, RedirectAttributes ra, HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/login";
        Sprint sprint = sprintRepository.findById(id).orElse(null);
        if (sprint == null) {
            ra.addFlashAttribute("error", "Sprint not found");
            return "redirect:/sprints";
        }
        sprint.setStatus("CLOSED");
        sprintRepository.save(sprint);
        ra.addFlashAttribute("success", "Sprint closed");
        return "redirect:/sprints/board/" + id;
    }

    @Transactional
    @PostMapping("/add-issue")
    public String addIssueToSprint(@RequestParam Long sprintId, @RequestParam Long issueId,
            RedirectAttributes ra, HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/login";
        Issue issue = issueRepository.findById(issueId).orElse(null);
        Sprint sprint = sprintRepository.findById(sprintId).orElse(null);
        if (issue == null || sprint == null) {
            ra.addFlashAttribute("error", "Issue or sprint not found");
            return "redirect:/sprints/board/" + sprintId;
        }
        if (!issue.getProject().getId().equals(sprint.getProject().getId())) {
            ra.addFlashAttribute("error", "Issue must be from same project");
            return "redirect:/sprints/board/" + sprintId;
        }
        issue.setSprint(sprint);
        issueRepository.save(issue);
        ra.addFlashAttribute("success", "Issue added to sprint");
        return "redirect:/sprints/board/" + sprintId;
    }

    @Transactional
    @PostMapping("/remove-issue")
    public String removeIssueFromSprint(@RequestParam Long sprintId, @RequestParam Long issueId,
            RedirectAttributes ra, HttpSession session) {
        if (!isLoggedIn(session)) return "redirect:/login";
        Issue issue = issueRepository.findById(issueId).orElse(null);
        if (issue == null) {
            ra.addFlashAttribute("error", "Issue not found");
            return "redirect:/sprints/board/" + sprintId;
        }
        issue.setSprint(null);
        issueRepository.save(issue);
        ra.addFlashAttribute("success", "Issue removed from sprint");
        return "redirect:/sprints/board/" + sprintId;
    }
}
