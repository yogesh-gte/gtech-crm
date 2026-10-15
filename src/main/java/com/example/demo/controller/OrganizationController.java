package com.example.demo.controller;

import com.example.demo.model.Department;
import com.example.demo.model.Employee;
import com.example.demo.model.Project;
import com.example.demo.model.Team;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TeamRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/organization")
public class OrganizationController {

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private boolean isAdminLoggedIn(HttpSession session) {
        return session.getAttribute("user") != null;
    }

    @GetMapping
    public String organizationDashboard(Model model, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        List<Department> departments = departmentRepository.findByIsActiveTrueOrderByNameAsc();
        List<Team> teams = teamRepository.findByIsActiveTrueOrderByNameAsc();
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("departments", departments);
        model.addAttribute("teams", teams);
        List<Project> projects = projectRepository.findAll();
        model.addAttribute("departmentCount", departments.size());
        model.addAttribute("teamCount", teams.size());
        model.addAttribute("projectCount", projects.size());
        model.addAttribute("projects", projects);
        return "admin-organization";
    }

    // --- Department CRUD ---
    @GetMapping("/departments")
    public String listDepartments(Model model, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        List<Department> departments = departmentRepository.findAll();
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("departments", departments);
        return "admin-departments";
    }

    @GetMapping("/departments/add")
    public String showAddDepartment(Model model, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("department", new Department());
        return "admin-department-form";
    }

    @GetMapping("/departments/edit/{id}")
    public String showEditDepartment(@PathVariable Long id, Model model, HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        Optional<Department> opt = departmentRepository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Department not found");
            return "redirect:/admin/organization/departments";
        }
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("department", opt.get());
        return "admin-department-form";
    }

    @PostMapping("/departments/save")
    public String saveDepartment(@RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Department name is required");
            return id != null ? "redirect:/admin/organization/departments/edit/" + id : "redirect:/admin/organization/departments/add";
        }
        Department dept;
        if (id != null) {
            dept = departmentRepository.findById(id).orElse(new Department());
        } else {
            dept = new Department();
        }
        dept.setName(name.trim());
        dept.setCode(code != null && !code.isBlank() ? code.trim().toUpperCase() : null);
        dept.setDescription(description != null && !description.isBlank() ? description.trim() : null);
        if (dept.getCode() != null) {
            var existingByCode = departmentRepository.findByCode(dept.getCode());
            if (existingByCode.isPresent() && !existingByCode.get().getId().equals(id)) {
                redirectAttributes.addFlashAttribute("error", "Department code already exists");
                return id != null ? "redirect:/admin/organization/departments/edit/" + id : "redirect:/admin/organization/departments/add";
            }
        }
        departmentRepository.save(dept);
        redirectAttributes.addFlashAttribute("success", "Department saved successfully");
        return "redirect:/admin/organization/departments";
    }

    @PostMapping("/departments/delete/{id}")
    public String deleteDepartment(@PathVariable Long id, RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        Optional<Department> opt = departmentRepository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Department not found");
            return "redirect:/admin/organization/departments";
        }
        Department dept = opt.get();
        if (teamRepository.countByDepartment(dept) > 0) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete department with teams. Remove teams first.");
            return "redirect:/admin/organization/departments";
        }
        dept.setIsActive(false);
        departmentRepository.save(dept);
        redirectAttributes.addFlashAttribute("success", "Department deactivated");
        return "redirect:/admin/organization/departments";
    }

    // --- Team CRUD ---
    @GetMapping("/teams")
    public String listTeams(Model model, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        List<Team> teams = teamRepository.findAllWithTeamLead();
        List<Department> departments = departmentRepository.findByIsActiveTrueOrderByNameAsc();
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("teams", teams);
        model.addAttribute("departments", departments);
        return "admin-teams";
    }

    @GetMapping("/teams/add")
    public String showAddTeam(Model model, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        List<Department> departments = departmentRepository.findByIsActiveTrueOrderByNameAsc();
        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(e -> e.getIsActive() == null || e.getIsActive())
                .toList();
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("team", new Team());
        model.addAttribute("departments", departments);
        model.addAttribute("employees", employees);
        return "admin-team-form";
    }

    @GetMapping("/teams/edit/{id}")
    public String showEditTeam(@PathVariable Long id, Model model, HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        Optional<Team> opt = teamRepository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Team not found");
            return "redirect:/admin/organization/teams";
        }
        List<Department> departments = departmentRepository.findByIsActiveTrueOrderByNameAsc();
        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(e -> e.getIsActive() == null || e.getIsActive())
                .toList();
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("team", opt.get());
        model.addAttribute("departments", departments);
        model.addAttribute("employees", employees);
        return "admin-team-form";
    }

    @PostMapping("/teams/save")
    public String saveTeam(@RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long teamLeadId,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Team name is required");
            return id != null ? "redirect:/admin/organization/teams/edit/" + id : "redirect:/admin/organization/teams/add";
        }
        if (departmentId == null) {
            redirectAttributes.addFlashAttribute("error", "Department selection is required");
            return id != null ? "redirect:/admin/organization/teams/edit/" + id : "redirect:/admin/organization/teams/add";
        }
        Team team;
        if (id != null) {
            team = teamRepository.findById(id).orElse(new Team());
        } else {
            team = new Team();
        }
        Department dept = departmentRepository.findById(departmentId).orElse(null);
        if (dept == null) {
            redirectAttributes.addFlashAttribute("error", "Department not found");
            return id != null ? "redirect:/admin/organization/teams/edit/" + id : "redirect:/admin/organization/teams/add";
        }
        team.setName(name.trim());
        team.setDepartment(dept);
        team.setDescription(description != null && !description.isBlank() ? description.trim() : null);
        if (teamLeadId != null) {
            Employee tl = employeeRepository.findById(teamLeadId).orElse(null);
            team.setTeamLead(tl);
        } else {
            if (team.getTeamLead() != null) {
                Employee prevTl = team.getTeamLead();
                prevTl.setIsTeamLead(false);
                prevTl.setTeam(null);
                employeeRepository.save(prevTl);
            }
            team.setTeamLead(null);
        }
        teamRepository.save(team);
        if (teamLeadId != null) {
            Employee tl = employeeRepository.findById(teamLeadId).orElse(null);
            if (tl != null) {
                tl.setIsTeamLead(true);
                tl.setTeam(team);
                if (tl.getDepartment() == null || tl.getDepartment().isEmpty()) {
                    tl.setDepartment(dept.getName());
                }
                employeeRepository.save(tl);
            }
        }
        redirectAttributes.addFlashAttribute("success", "Team saved successfully");
        return "redirect:/admin/organization/teams";
    }

    @PostMapping("/teams/delete/{id}")
    public String deleteTeam(@PathVariable Long id, RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        Optional<Team> opt = teamRepository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Team not found");
            return "redirect:/admin/organization/teams";
        }
        Team team = opt.get();
        List<Employee> members = employeeRepository.findByTeam(team);
        for (Employee emp : members) {
            emp.setTeam(null);
            if (Boolean.TRUE.equals(emp.getIsTeamLead()) && team.getTeamLead() != null
                    && team.getTeamLead().getId().equals(emp.getId())) {
                emp.setIsTeamLead(false);
            }
            employeeRepository.save(emp);
        }
        if (team.getTeamLead() != null) {
            team.getTeamLead().setIsTeamLead(false);
            team.getTeamLead().setTeam(null);
            employeeRepository.save(team.getTeamLead());
        }
        team.setIsActive(false);
        teamRepository.save(team);
        redirectAttributes.addFlashAttribute("success", "Team deactivated");
        return "redirect:/admin/organization/teams";
    }

    // --- Project CRUD ---
    @GetMapping("/projects")
    public String listProjects(Model model, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        List<Project> projects = projectRepository.findAllWithDetails();
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("projects", projects);
        return "admin-projects";
    }

    @GetMapping("/projects/add")
    public String showAddProject(Model model, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("project", new Project());
        model.addAttribute("teams", teamRepository.findByIsActiveTrueOrderByNameAsc());
        model.addAttribute("employees", employeeRepository.findAll().stream()
                .filter(e -> e.getIsActive() == null || e.getIsActive()).toList());
        return "admin-project-form";
    }

    @GetMapping("/projects/edit/{id}")
    public String showEditProject(@PathVariable Long id, Model model, HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        Optional<Project> opt = projectRepository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Project not found");
            return "redirect:/admin/organization/projects";
        }
        model.addAttribute("userName", session.getAttribute("userName"));
        model.addAttribute("project", opt.get());
        model.addAttribute("teams", teamRepository.findByIsActiveTrueOrderByNameAsc());
        model.addAttribute("employees", employeeRepository.findAll().stream()
                .filter(e -> e.getIsActive() == null || e.getIsActive()).toList());
        return "admin-project-form";
    }

    @PostMapping("/projects/save")
    public String saveProject(@RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String startDateStr,
            @RequestParam(required = false) String endDateStr,
            @RequestParam(required = false) String status,
            RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Project name is required");
            return id != null ? "redirect:/admin/organization/projects/edit/" + id : "redirect:/admin/organization/projects/add";
        }

        LocalDate parsedStartDate = null;
        LocalDate parsedEndDate = null;

        if (startDateStr != null && !startDateStr.isBlank()) {
            try {
                parsedStartDate = LocalDate.parse(startDateStr.trim());
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Invalid start date format");
                return id != null ? "redirect:/admin/organization/projects/edit/" + id : "redirect:/admin/organization/projects/add";
            }
        }
        if (endDateStr != null && !endDateStr.isBlank()) {
            try {
                parsedEndDate = LocalDate.parse(endDateStr.trim());
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Invalid end date format");
                return id != null ? "redirect:/admin/organization/projects/edit/" + id : "redirect:/admin/organization/projects/add";
            }
        }

        if (parsedStartDate != null && parsedEndDate != null && parsedStartDate.isAfter(parsedEndDate)) {
            redirectAttributes.addFlashAttribute("error", "Start date cannot be after end date");
            return id != null ? "redirect:/admin/organization/projects/edit/" + id : "redirect:/admin/organization/projects/add";
        }

        Project project;
        if (id != null) {
            project = projectRepository.findById(id).orElse(new Project());
        } else {
            project = new Project();
        }
        project.setName(name.trim());
        project.setProjectCode(projectCode != null && !projectCode.isBlank() ? projectCode.trim().toUpperCase() : null);
        project.setDescription(description != null && !description.isBlank() ? description.trim() : null);
        if (teamId != null) {
            project.setTeam(teamRepository.findById(teamId).orElse(null));
        } else {
            project.setTeam(null);
        }
        if (ownerId != null) {
            project.setOwner(employeeRepository.findById(ownerId).orElse(null));
        } else {
            project.setOwner(null);
        }
        project.setStartDate(parsedStartDate);
        project.setEndDate(parsedEndDate);
        project.setStatus(status != null && !status.isBlank() ? status : "ACTIVE");
        projectRepository.save(project);
        redirectAttributes.addFlashAttribute("success", "Project saved successfully");
        return "redirect:/admin/organization/projects";
    }

    @PostMapping("/projects/delete/{id}")
    public String deleteProject(@PathVariable Long id, RedirectAttributes redirectAttributes, HttpSession session) {
        if (!isAdminLoggedIn(session)) {
            return "redirect:/login";
        }
        Optional<Project> opt = projectRepository.findById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Project not found");
            return "redirect:/admin/organization/projects";
        }
        Project project = opt.get();
        project.setStatus("CANCELLED");
        projectRepository.save(project);
        redirectAttributes.addFlashAttribute("success", "Project cancelled");
        return "redirect:/admin/organization/projects";
    }
}
