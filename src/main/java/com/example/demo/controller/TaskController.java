package com.example.demo.controller;

import com.example.demo.model.Employee;
import com.example.demo.model.Task;
import com.example.demo.model.TaskComment;
import com.example.demo.model.User;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.TaskCommentRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/employee/tasks")
public class TaskController {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TaskCommentRepository taskCommentRepository;

    @Autowired
    private com.example.demo.repository.NotificationRepository notificationRepository;

    @Autowired
    private com.example.demo.repository.UserRepository userRepository;

    @RequestMapping(method = RequestMethod.GET)
    public String myTasks(Model model, HttpSession session) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        LocalDate today = LocalDate.now();
        List<Task> todayTasks = taskRepository.findTodayTasksByEmployee(employee, today);
        List<Task> allTasks = taskRepository.findByEmployeeAndStatusNot(employee, "CANCELLED");

        model.addAttribute("employee", employee);
        model.addAttribute("todayTasks", todayTasks);
        model.addAttribute("allTasks", allTasks);
        model.addAttribute("task", new Task());

        return "employee-tasks";
    }

    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public String addTask(@ModelAttribute Task task,
                         @RequestParam(required = false) String dueDate,
                         HttpSession session, 
                         RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        if (task.getTitle() == null || task.getTitle().isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Task title is required.");
            return "redirect:/employee/tasks";
        }
        task.setEmployee(employee);
        task.setAssignedDate(LocalDate.now());
        
        // Parse due date if provided
        if (dueDate != null && !dueDate.isEmpty()) {
            try {
                task.setDueDate(LocalDate.parse(dueDate));
            } catch (Exception e) {
                // Invalid date format, ignore
            }
        }
        
        taskRepository.save(task);

        // In-app notification for employee
        try {
            com.example.demo.model.Notification notif = new com.example.demo.model.Notification();
            notif.setRecipient(employee);
            notif.setMessage("New task created: " + task.getTitle() + (task.getDueDate() != null ? " (Due: " + task.getDueDate() + ")" : ""));
            notif.setCreatedAt(LocalDateTime.now());
            notif.setIsRead(false);
            notificationRepository.save(notif);
        } catch (Exception notifEx) {
            System.err.println("Failed to create task notification: " + notifEx.getMessage());
        }

        // Update last activity time
        employee.setLastActivityTime(LocalDateTime.now());
        employeeRepository.save(employee);

        redirectAttributes.addFlashAttribute("success", "Task added successfully!");
        return "redirect:/employee/tasks";
    }

    @RequestMapping(value = "/update/{id}", method = RequestMethod.POST)
    public String updateTask(@PathVariable Long id,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) Integer progress,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        Optional<Task> taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty() || !taskOpt.get().getEmployee().getId().equals(employee.getId())) {
            redirectAttributes.addFlashAttribute("error", "Task not found!");
            return "redirect:/employee/tasks";
        }

        Task task = taskOpt.get();
        if (status != null && !status.isEmpty()) {
            task.setStatus(status);
        }
        if (progress != null) {
            task.setProgressPercentage(progress);
        }

        taskRepository.save(task);

        // Update last activity time
        employee.setLastActivityTime(LocalDateTime.now());
        employeeRepository.save(employee);

        // Notify managers about task update (LOG-MGR-008)
        try {
            List<User> managerUsers = userRepository.findByRole("MANAGER");
            for (User mgr : managerUsers) {
                com.example.demo.model.Notification notif = new com.example.demo.model.Notification();
                notif.setUserRecipient(mgr);
                notif.setMessage("Task Update: " + employee.getName() + " updated '" + task.getTitle() + "' (" + (status != null ? status : "Progress: " + (progress != null ? progress : 0) + "%") + ")");
                notif.setActionUrl("/manager/tasks/details/" + task.getId());
                notif.setCreatedAt(LocalDateTime.now());
                notif.setIsRead(false);
                notificationRepository.save(notif);
            }
        } catch (Exception notifEx) {
            System.err.println("Failed to send manager notification for task update: " + notifEx.getMessage());
        }

        redirectAttributes.addFlashAttribute("success", "Task updated successfully!");
        return "redirect:/employee/tasks";
    }

    @RequestMapping(value = "/delete/{id}", method = RequestMethod.GET)
    public String deleteTask(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        Employee employee = (Employee) session.getAttribute("employee");
        if (user == null && employee == null) {
            return "redirect:/employee/login";
        }

        // LOG-EMP-012: Regular employees are not authorized to delete assigned tasks
        boolean isAuthorized = (user != null && ("ADMIN".equals(user.getRole()) || "MANAGER".equals(user.getRole())))
                || (employee != null && Boolean.TRUE.equals(employee.getIsTeamLead()));

        if (!isAuthorized) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized: Employees cannot delete assigned tasks. Please contact your manager or Team Lead.");
            return "redirect:/employee/tasks";
        }

        Optional<Task> taskOpt = taskRepository.findById(id);
        if (taskOpt.isPresent()) {
            taskRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("success", "Task deleted successfully!");
        } else {
            redirectAttributes.addFlashAttribute("error", "Task not found!");
        }

        return "redirect:/employee/tasks";
    }

    @RequestMapping(value = "/details/{id}", method = RequestMethod.GET)
    public String taskDetails(@PathVariable Long id, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        Optional<Task> taskOpt = taskRepository.findById(id);
        if (taskOpt.isEmpty() || !taskOpt.get().getEmployee().getId().equals(employee.getId())) {
            redirectAttributes.addFlashAttribute("error", "Task not found!");
            return "redirect:/employee/tasks";
        }

        Task task = taskOpt.get();
        List<TaskComment> comments = taskCommentRepository.findByTaskOrderByCreatedAtAsc(task);
        
        model.addAttribute("task", task);
        model.addAttribute("comments", comments);
        model.addAttribute("employee", employee);
        
        return "employee-task-details";
    }

    @RequestMapping(value = "/comment/add", method = RequestMethod.POST)
    public String addComment(@RequestParam Long taskId, @RequestParam String content, HttpSession session, RedirectAttributes redirectAttributes) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        Optional<Task> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty() || !taskOpt.get().getEmployee().getId().equals(employee.getId())) {
            redirectAttributes.addFlashAttribute("error", "Task not found!");
            return "redirect:/employee/tasks";
        }

        if (content != null && !content.trim().isEmpty()) {
            TaskComment comment = new TaskComment(taskOpt.get(), employee.getName(), "EMPLOYEE", content);
            taskCommentRepository.save(comment);

            // Notify managers about new task comment (LOG-MGR-008)
            try {
                List<User> managerUsers = userRepository.findByRole("MANAGER");
                for (User mgr : managerUsers) {
                    com.example.demo.model.Notification notif = new com.example.demo.model.Notification();
                    notif.setUserRecipient(mgr);
                    notif.setMessage("New Comment: " + employee.getName() + " commented on task '" + taskOpt.get().getTitle() + "'");
                    notif.setActionUrl("/manager/tasks/details/" + taskId);
                    notif.setCreatedAt(LocalDateTime.now());
                    notif.setIsRead(false);
                    notificationRepository.save(notif);
                }
            } catch (Exception notifEx) {
                System.err.println("Failed to send manager notification for comment: " + notifEx.getMessage());
            }
        }

        return "redirect:/employee/tasks/details/" + taskId;
    }
}


