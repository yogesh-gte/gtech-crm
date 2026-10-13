package com.example.demo.controller;

import com.example.demo.model.Employee;
import com.example.demo.model.Meeting;
import com.example.demo.model.Visitor;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.MeetingRepository;
import com.example.demo.repository.VisitorRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.model.Notification;
import com.example.demo.service.EmailService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/meetings")
public class MeetingController {

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private VisitorRepository visitorRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @RequestMapping(value = "/create", method = RequestMethod.GET)
    public String showCreateForm(Model model, HttpSession session, @RequestParam(required = false) String type) {
        // Allow both admin and employee access
        if (session.getAttribute("user") == null && session.getAttribute("employee") == null) {
            return "redirect:/login";
        }

        Meeting meeting = new Meeting();
        String pageTitle = "Schedule Meeting";

        if (type != null) {
            switch (type) {
                case "VISITOR":
                    meeting.setMeetingType("VISITOR");
                    meeting.setTitle("Visitor Meeting: ");
                    pageTitle = "Schedule Visitor Meeting";
                    break;
                case "EMPLOYEE":
                    meeting.setMeetingType("INTERNAL");
                    // meeting.setTitle("Internal Meeting: "); // Optional default
                    pageTitle = "Schedule Employee Meeting";
                    break;
                case "HR":
                    meeting.setMeetingType("HR");
                    meeting.setTitle("HR Discussion: ");
                    pageTitle = "Schedule HR Meeting";
                    break;
                case "MANAGER":
                    meeting.setMeetingType("MANAGER");
                    meeting.setTitle("Manager Sync: ");
                    pageTitle = "Schedule Manager Meeting";
                    break;
                default:
                    meeting.setMeetingType("INTERNAL");
                    pageTitle = "Schedule Meeting";
            }
        } else {
            meeting.setMeetingType("INTERNAL");
        }

        model.addAttribute("meeting", meeting);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("employees", employeeRepository.findAll());

        // Get all registered visitors (Registered, Checked In, or Pending Approval)
        // This includes visitors who are registered but not yet checked in
        List<Visitor> allVisitors = visitorRepository.findAll();
        List<Visitor> registeredVisitors = allVisitors.stream()
                .filter(v -> "Registered".equals(v.getStatus()) ||
                        "Checked In".equals(v.getStatus()) ||
                        "Pending Approval".equals(v.getStatus()))
                .sorted((v1, v2) -> {
                    // Sort by name
                    if (v1.getName() != null && v2.getName() != null) {
                        return v1.getName().compareToIgnoreCase(v2.getName());
                    }
                    return 0;
                })
                .toList();

        model.addAttribute("visitors", registeredVisitors);

        // Determine redirect URL based on user type
        Employee employee = (Employee) session.getAttribute("employee");
        String redirectUrl = employee != null ? "/employee/dashboard" : "/admin/dashboard";
        model.addAttribute("redirectUrl", redirectUrl);

        return "meeting-create";
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String createMeeting(@ModelAttribute Meeting meeting,
            @RequestParam(required = false) Long organizerId,
            @RequestParam(required = false) Long visitorId,
            @RequestParam(required = false, defaultValue = "false") boolean inviteEveryone,
            @RequestParam(required = false) List<Long> attendeeIds,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        // Allow both admin and employee access
        if (session.getAttribute("user") == null && session.getAttribute("employee") == null) {
            return "redirect:/login";
        }

        // If employee is creating meeting, set them as organizer
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee != null && organizerId == null) {
            organizerId = employee.getId();
        }

        if (organizerId != null) {
            Optional<Employee> organizer = employeeRepository.findById(organizerId);
            organizer.ifPresent(meeting::setOrganizer);
        }

        Visitor visitor = null;
        if (visitorId != null) {
            Optional<Visitor> visitorOpt = visitorRepository.findById(visitorId);
            if (visitorOpt.isPresent()) {
                visitor = visitorOpt.get();
                meeting.setVisitorId(visitorId);
                meeting.setVisitorName(visitor.getName());
            }
        }

        // Auto-set status based on start time
        if (meeting.getStartTime() != null && meeting.getStartTime().isBefore(LocalDateTime.now())) {
            meeting.setStatus("ONGOING");
        }

        meetingRepository.save(meeting);

        // Send email notifications
        String organizerName = meeting.getOrganizer() != null ? meeting.getOrganizer().getName() : "Management";

        if (inviteEveryone) {
            meeting.setInviteEveryone(true);
            meetingRepository.save(meeting); // Save the flag

            if ("HR".equals(meeting.getMeetingType())) {
                List<com.example.demo.model.User> hrUsers = userRepository.findByRole("HR");
                for (com.example.demo.model.User user : hrUsers) {
                    emailService.sendInternalMeetingInvitation(
                            user.getEmail(), user.getName(), meeting.getTitle(), organizerName,
                            meeting.getStartTime(), meeting.getEndTime(), meeting.getLocation(),
                            meeting.getDescription());
                }
                redirectAttributes.addFlashAttribute("info", "Meeting notifications sent to all HRs.");
            } else if ("MANAGER".equals(meeting.getMeetingType())) {
                List<com.example.demo.model.User> managerUsers = userRepository.findByRole("MANAGER");
                for (com.example.demo.model.User user : managerUsers) {
                    emailService.sendInternalMeetingInvitation(
                            user.getEmail(), user.getName(), meeting.getTitle(), organizerName,
                            meeting.getStartTime(), meeting.getEndTime(), meeting.getLocation(),
                            meeting.getDescription());
                }
                redirectAttributes.addFlashAttribute("info", "Meeting notifications sent to all Managers.");
            } else {
                // Default INTERNAL / EMPLOYEE / others -> Send to All Employees
                List<Employee> allEmployees = employeeRepository.findAll();

                for (Employee emp : allEmployees) {
                    if (emp.getEmail() != null && !emp.getEmail().isEmpty()) {
                        if (meeting.getOrganizer() != null && emp.getId().equals(meeting.getOrganizer().getId())) {
                            continue;
                        }
                        emailService.sendInternalMeetingInvitation(
                                emp.getEmail(), emp.getName(), meeting.getTitle(), organizerName,
                                meeting.getStartTime(), meeting.getEndTime(), meeting.getLocation(),
                                meeting.getDescription());
                    }
                }
                redirectAttributes.addFlashAttribute("info", "Meeting notifications sent to all internal staff.");
            }
        } else {
            // Specific visitor invitation or Specific Employee Selection
            if (visitor != null) {
                emailService.sendMeetingInvitation(
                        visitor.getEmail(),
                        visitor.getName(),
                        meeting.getTitle(),
                        organizerName,
                        meeting.getStartTime(),
                        meeting.getEndTime(),
                        meeting.getLocation(),
                        meeting.getDescription());
            }

            // Handle manually selected internal attendees
            if (attendeeIds != null && !attendeeIds.isEmpty()) {
                List<Employee> selectedAttendees = employeeRepository.findAllById(attendeeIds);
                meeting.setAttendees(selectedAttendees);
                meetingRepository.save(meeting); // Save relationship

                // Send emails to selected attendees
                for (Employee attendee : selectedAttendees) {
                    if (attendee.getEmail() != null && !attendee.getEmail().isEmpty()) {
                        // Don't send to organizer again if they selected themselves
                        if (meeting.getOrganizer() != null && attendee.getId().equals(meeting.getOrganizer().getId())) {
                            continue;
                        }

                        emailService.sendInternalMeetingInvitation(
                                attendee.getEmail(), attendee.getName(), meeting.getTitle(), organizerName,
                                meeting.getStartTime(), meeting.getEndTime(), meeting.getLocation(),
                                meeting.getDescription());
                    }
                }
            }
        }

        // Create In-App Notifications
        if (inviteEveryone) {
            String notificationMsg = "New Meeting: " + meeting.getTitle();
            Long meetingId = meeting.getId();

            if ("HR".equals(meeting.getMeetingType())) {
                List<com.example.demo.model.User> hrUsers = userRepository.findByRole("HR");
                for (com.example.demo.model.User user : hrUsers) {
                    Notification notification = new Notification(user, notificationMsg, meetingId);
                    notificationRepository.save(notification);
                }
            } else if ("MANAGER".equals(meeting.getMeetingType())) {
                List<com.example.demo.model.User> managerUsers = userRepository.findByRole("MANAGER");
                for (com.example.demo.model.User user : managerUsers) {
                    Notification notification = new Notification(user, notificationMsg, meetingId);
                    notificationRepository.save(notification);
                }
            } else {
                // Notify all employees
                List<Employee> allEmployees = employeeRepository.findAll();
                for (Employee emp : allEmployees) {
                    if (meeting.getOrganizer() != null && emp.getId().equals(meeting.getOrganizer().getId()))
                        continue;
                    Notification notification = new Notification(emp, notificationMsg, meetingId);
                    notificationRepository.save(notification);
                }
            }
        } else {
            // Specific attendees
            if (attendeeIds != null && !attendeeIds.isEmpty()) {
                List<Employee> selectedAttendees = employeeRepository.findAllById(attendeeIds);
                for (Employee attendee : selectedAttendees) {
                    if (meeting.getOrganizer() != null && attendee.getId().equals(meeting.getOrganizer().getId()))
                        continue;
                    Notification notification = new Notification(attendee, "New Meeting: " + meeting.getTitle(),
                            meeting.getId());
                    notificationRepository.save(notification);
                }
            }
        }

        redirectAttributes.addFlashAttribute("success", "Meeting created successfully!" +
                (visitor != null ? " Email notification sent to visitor." : ""));

        // Redirect based on user type
        if (session.getAttribute("employee") != null) {
            return "redirect:/employee/dashboard";
        }
        return "redirect:/admin/dashboard";
    }

    @RequestMapping(value = "/end/{id}", method = RequestMethod.POST)
    public String endMeeting(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        Optional<Meeting> meetingOpt = meetingRepository.findById(id);
        if (meetingOpt.isPresent()) {
            Meeting meeting = meetingOpt.get();
            meeting.setEndTime(LocalDateTime.now());
            meeting.setStatus("COMPLETED");
            meetingRepository.save(meeting);
            redirectAttributes.addFlashAttribute("success", "Meeting ended successfully!");
        }

        return "redirect:/admin/dashboard";
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public String listMeetings(Model model, HttpSession session) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }

        model.addAttribute("meetings", meetingRepository.findAll());
        model.addAttribute("ongoingMeetings", meetingRepository.findOngoingMeetings());
        return "meeting-list";
    }
}
