package com.example.demo;

import com.example.demo.controller.EmployeeController;
import com.example.demo.controller.ManagerController;
import com.example.demo.controller.TaskController;
import com.example.demo.model.*;
import com.example.demo.repository.*;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ManagerBugsVerificationTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskCommentRepository taskCommentRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private TeamBadgeRepository teamBadgeRepository;

    @Mock
    private TeamRuleRepository teamRuleRepository;

    @Mock
    private IdleIncidentRepository idleIncidentRepository;

    @Mock
    private SuggestionRepository suggestionRepository;

    @Mock
    private EmployeeActivityRepository employeeActivityRepository;

    @Mock
    private com.example.demo.service.EmailService emailService;

    @Mock
    private HttpSession session;

    @InjectMocks
    private ManagerController managerController;

    @InjectMocks
    private TaskController taskController;

    @InjectMocks
    private EmployeeController employeeController;

    private User managerUser;
    private Employee regularEmp;
    private Employee teamLeadEmp;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        managerUser = new User();
        managerUser.setId(10L);
        managerUser.setName("Test Manager");
        managerUser.setEmail("manager@test.com");
        managerUser.setRole("MANAGER");

        regularEmp = new Employee();
        regularEmp.setId(101L);
        regularEmp.setName("Regular Employee");
        regularEmp.setEmployeeId("EMP101");
        regularEmp.setIsTeamLead(false);
        regularEmp.setIsActive(true);

        teamLeadEmp = new Employee();
        teamLeadEmp.setId(102L);
        teamLeadEmp.setName("Lead Employee");
        teamLeadEmp.setEmployeeId("EMP102");
        teamLeadEmp.setIsTeamLead(true);
        teamLeadEmp.setIsActive(true);

        when(session.getAttribute("user")).thenReturn(managerUser);
        when(session.getAttribute("employee")).thenReturn(regularEmp);
    }

    // =========================================================================
    // LOG-MGR-001: Task assignment restricted to Team Leads
    // =========================================================================
    @Test
    @DisplayName("LOG-MGR-001: Reject task assignment to regular employee who is not a Team Lead")
    void testTaskAssignment_RejectNonTeamLead() {
        when(employeeRepository.findById(101L)).thenReturn(Optional.of(regularEmp));
        RedirectAttributes ra = new RedirectAttributesModelMap();

        String result = managerController.addTaskForEmployee("Sprint Backlog Grooming", "Description", "HIGH",
                LocalDate.now().plusDays(2).toString(), 101L, null, session, ra);

        assertEquals("redirect:/manager/dashboard?tab=planning", result);
        assertTrue(ra.getFlashAttributes().containsKey("error"));
        assertTrue(ra.getFlashAttributes().get("error").toString().toLowerCase().contains("team lead"));
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    @DisplayName("LOG-MGR-001: Allow task assignment to verified Team Lead")
    void testTaskAssignment_AllowTeamLead() {
        when(employeeRepository.findById(102L)).thenReturn(Optional.of(teamLeadEmp));
        RedirectAttributes ra = new RedirectAttributesModelMap();

        String result = managerController.addTaskForEmployee("Team Architecture Review", "Lead the meeting", "HIGH",
                LocalDate.now().plusDays(3).toString(), 102L, null, session, ra);

        assertEquals("redirect:/manager/dashboard?tab=planning", result);
        assertTrue(ra.getFlashAttributes().containsKey("success"));
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    // =========================================================================
    // LOG-MGR-002 & LOG-MGR-004: Manager Sidebar fragment and CSS
    // =========================================================================
    @Test
    @DisplayName("LOG-MGR-002 & LOG-MGR-004: Manager sidebar fragment exists and manager-add-feedback uses it")
    void testSidebarFragmentAndFeedbackPage() throws Exception {
        File sidebarFragment = new File("src/main/resources/templates/fragments/manager-sidebar.html");
        assertTrue(sidebarFragment.exists(), "fragments/manager-sidebar.html must exist");

        String sidebarContent = Files.readString(sidebarFragment.toPath());
        assertTrue(sidebarContent.contains(".sidebar"), "Sidebar fragment must contain .sidebar CSS");
        assertTrue(sidebarContent.contains(".main-content"), "Sidebar fragment must contain .main-content CSS");
        assertTrue(sidebarContent.contains("toggleManagerSidebar"), "Sidebar fragment must contain toggle function");

        String feedbackContent = Files.readString(Paths.get("src/main/resources/templates/manager-add-feedback.html"));
        assertTrue(feedbackContent.contains("fragments/manager-sidebar :: sidebar"),
                "manager-add-feedback.html must use the shared manager sidebar fragment");
    }

    // =========================================================================
    // LOG-MGR-003: Real-time team availability calculation
    // =========================================================================
    @Test
    @DisplayName("LOG-MGR-003: Employee is AVAILABLE when meeting is completed or outside current time")
    void testAvailability_NotStuckInMeetingWhenCompleted() {
        LocalDateTime now = LocalDateTime.now();
        Meeting pastMeeting = new Meeting();
        pastMeeting.setId(501L);
        pastMeeting.setTitle("Morning Standup");
        pastMeeting.setOrganizer(teamLeadEmp);
        pastMeeting.setStartTime(now.minusHours(3));
        pastMeeting.setEndTime(now.minusHours(2));
        pastMeeting.setStatus("COMPLETED");

        when(leaveRequestRepository.findActiveLeavesOnDate(any())).thenReturn(Collections.emptyList());
        when(meetingRepository.findByDateRange(any(), any())).thenReturn(Collections.singletonList(pastMeeting));
        when(taskRepository.findAll()).thenReturn(Collections.emptyList());

        Map<Employee, String> availability = managerController.calculateEmployeeAvailability(Collections.singletonList(teamLeadEmp));

        assertEquals("AVAILABLE", availability.get(teamLeadEmp),
                "Employee should be AVAILABLE when past meeting is COMPLETED and no current meetings exist");
    }

    @Test
    @DisplayName("LOG-MGR-003: Employee is IN_MEETING when meeting is currently active (ongoing)")
    void testAvailability_InMeetingWhenCurrentlyActive() {
        LocalDateTime now = LocalDateTime.now();
        Meeting activeMeeting = new Meeting();
        activeMeeting.setId(502L);
        activeMeeting.setTitle("Active Strategy Meeting");
        activeMeeting.setOrganizer(teamLeadEmp);
        activeMeeting.setAttendees(Collections.singletonList(regularEmp));
        activeMeeting.setStartTime(now.minusMinutes(15));
        activeMeeting.setEndTime(now.plusMinutes(45));
        activeMeeting.setStatus("SCHEDULED");

        when(leaveRequestRepository.findActiveLeavesOnDate(any())).thenReturn(Collections.emptyList());
        when(meetingRepository.findByDateRange(any(), any())).thenReturn(Collections.singletonList(activeMeeting));
        when(taskRepository.findAll()).thenReturn(Collections.emptyList());

        Map<Employee, String> availability = managerController.calculateEmployeeAvailability(Arrays.asList(teamLeadEmp, regularEmp));

        assertEquals("IN_MEETING", availability.get(teamLeadEmp), "Organizer should be IN_MEETING during active window");
        assertEquals("IN_MEETING", availability.get(regularEmp), "Attendee should be IN_MEETING during active window");
    }

    @Test
    @DisplayName("LOG-MGR-003: showCreateMeetingForm populates employeeAvailability for meeting section")
    void testShowCreateMeetingForm_ProvidesAvailability() {
        when(employeeRepository.findAll()).thenReturn(Arrays.asList(teamLeadEmp, regularEmp));
        when(leaveRequestRepository.findActiveLeavesOnDate(any())).thenReturn(Collections.emptyList());
        when(meetingRepository.findByDateRange(any(), any())).thenReturn(Collections.emptyList());
        when(taskRepository.findAll()).thenReturn(Collections.emptyList());

        Model model = new ConcurrentModel();
        String view = managerController.showCreateMeetingForm(model, session);

        assertEquals("manager-create-meeting", view);
        assertTrue(model.containsAttribute("employeeAvailability"), "Create meeting view must receive employeeAvailability");
    }

    // =========================================================================
    // LOG-MGR-005: Idle Employees list accessible across pages
    // =========================================================================
    @Test
    @DisplayName("LOG-MGR-005: ModelAttribute populates idleIncidents and idleCount across manager views")
    void testIdleIncidentsModelPopulation() {
        IdleIncident incident = new IdleIncident();
        incident.setId(1L);
        incident.setEmployee(regularEmp);
        incident.setIdleDurationMinutes(15L);
        incident.setIdleStartTime(LocalDateTime.now().minusMinutes(15));
        incident.setStatus("ACTIVE");

        when(idleIncidentRepository.findActiveIncidents()).thenReturn(Collections.singletonList(incident));

        Model model = new ConcurrentModel();
        managerController.populateCommonManagerAttributes(model, session);

        assertTrue(model.containsAttribute("idleIncidents"));
        assertTrue(model.containsAttribute("idleCount"));
        assertEquals(1, ((Number) model.getAttribute("idleCount")).intValue());
    }

    // =========================================================================
    // LOG-MGR-006: Form field validations across Manager Dashboard
    // =========================================================================
    @Test
    @DisplayName("LOG-MGR-006: Validate task form fields (title, due date in past)")
    void testTaskFormValidations() {
        RedirectAttributes ra = new RedirectAttributesModelMap();

        // 1. Blank/short title
        String res1 = managerController.addTaskForEmployee("  ", "desc", "HIGH", LocalDate.now().toString(), 102L, null, session, ra);
        assertEquals("redirect:/manager/dashboard?tab=planning", res1);
        assertTrue(ra.getFlashAttributes().containsKey("error"));

        // 2. Due date in the past
        when(employeeRepository.findById(102L)).thenReturn(Optional.of(teamLeadEmp));
        RedirectAttributes ra2 = new RedirectAttributesModelMap();
        String res2 = managerController.addTaskForEmployee("Valid Title", "desc", "HIGH",
                LocalDate.now().minusDays(2).toString(), 102L, null, session, ra2);
        assertEquals("redirect:/manager/dashboard?tab=planning", res2);
        assertTrue(ra2.getFlashAttributes().get("error").toString().toLowerCase().contains("past"));
    }

    @Test
    @DisplayName("LOG-MGR-006: Validate feedback form fields (notes length, past followUpDate)")
    void testFeedbackFormValidations() {
        RedirectAttributes ra = new RedirectAttributesModelMap();

        // Notes too short
        String res1 = managerController.addFeedback(101L, "hi", null, null, session, ra);
        assertEquals("redirect:/manager/feedback/add", res1);
        assertTrue(ra.getFlashAttributes().get("error").toString().contains("5 characters"));

        // Past follow up date
        when(employeeRepository.findById(101L)).thenReturn(Optional.of(regularEmp));
        RedirectAttributes ra2 = new RedirectAttributesModelMap();
        String res2 = managerController.addFeedback(101L, "Good overall performance this sprint", null,
                LocalDate.now().minusDays(1).toString(), session, ra2);
        assertEquals("redirect:/manager/feedback/add", res2);
        assertTrue(ra2.getFlashAttributes().get("error").toString().toLowerCase().contains("past"));
    }

    @Test
    @DisplayName("LOG-MGR-006: Validate meeting form fields (end time before start time, zero attendees)")
    void testMeetingFormValidations() {
        RedirectAttributes ra = new RedirectAttributesModelMap();

        // 1. Past meeting date
        String res1 = managerController.createTeamMeeting("Standup", "desc", LocalDate.now().minusDays(1).toString(),
                "10:00", "11:00", "Online", Collections.singletonList(101L), false, session, ra);
        assertEquals("redirect:/manager/meetings/create", res1);
        assertTrue(ra.getFlashAttributes().get("error").toString().toLowerCase().contains("past"));

        // 2. Zero attendees selected
        RedirectAttributes ra2 = new RedirectAttributesModelMap();
        String res2 = managerController.createTeamMeeting("Standup", "desc", LocalDate.now().toString(),
                "10:00", "11:00", "Online", Collections.emptyList(), false, session, ra2);
        assertEquals("redirect:/manager/meetings/create", res2);
        assertTrue(ra2.getFlashAttributes().get("error").toString().toLowerCase().contains("attendee"));

        // 3. End time before start time
        RedirectAttributes ra3 = new RedirectAttributesModelMap();
        String res3 = managerController.createTeamMeeting("Standup", "desc", LocalDate.now().toString(),
                "15:00", "14:00", "Online", Collections.singletonList(101L), false, session, ra3);
        assertEquals("redirect:/manager/meetings/create", res3);
        assertTrue(ra3.getFlashAttributes().get("error").toString().toLowerCase().contains("end time"));
    }

    @Test
    @DisplayName("LOG-MGR-006: Validate team rule creation fields")
    void testRuleCreationValidations() {
        RedirectAttributes ra = new RedirectAttributesModelMap();

        // Rule name too short
        String res1 = managerController.createRule("ab", "desc", "BREAK_RULE", "15 min", session, ra);
        assertEquals("redirect:/manager/rule/create", res1);
        assertTrue(ra.getFlashAttributes().get("error").toString().contains("3 characters"));

        // Description empty
        RedirectAttributes ra2 = new RedirectAttributesModelMap();
        String res2 = managerController.createRule("Break Rule", "", "BREAK_RULE", "15 min", session, ra2);
        assertEquals("redirect:/manager/rule/create", res2);
        assertTrue(ra2.getFlashAttributes().get("error").toString().toLowerCase().contains("description"));
    }

    // =========================================================================
    // LOG-MGR-007: Responsiveness across manager views
    // =========================================================================
    @Test
    @DisplayName("LOG-MGR-007: Check responsive viewport and mobile toggle in manager templates")
    void testManagerTemplatesResponsiveness() throws Exception {
        String[] templateFiles = {
                "manager-dashboard.html",
                "manager-add-feedback.html",
                "manager-award-badge.html",
                "manager-create-meeting.html",
                "manager-performance.html",
                "manager-notifications.html",
                "manager-create-rule.html",
                "manager-edit-rule.html"
        };

        for (String fileName : templateFiles) {
            String content = Files.readString(Paths.get("src/main/resources/templates/" + fileName));
            assertTrue(content.contains("name=\"viewport\""), fileName + " must have responsive viewport meta tag");
        }
    }

    // =========================================================================
    // LOG-MGR-008: Notifications for Manager events
    // =========================================================================
    @Test
    @DisplayName("LOG-MGR-008: Employee suggestion triggers notification for managers with actionUrl")
    void testSuggestionNotificationForManagers() {
        when(userRepository.findByRole("MANAGER")).thenReturn(Collections.singletonList(managerUser));
        when(userRepository.findByRole("HR")).thenReturn(Collections.emptyList());

        RedirectAttributes ra = new RedirectAttributesModelMap();
        employeeController.submitSuggestion("Improve Dev Environment", "Need Docker support", "MANAGER", session, ra);

        verify(notificationRepository, atLeastOnce()).save(argThat(n ->
                n.getUserRecipient() != null &&
                "MANAGER".equals(n.getUserRecipient().getRole()) &&
                n.getActionUrl() != null &&
                n.getActionUrl().contains("suggestions")
        ));
    }

    @Test
    @DisplayName("LOG-MGR-008: Employee task progress update triggers notification for managers")
    void testTaskUpdateNotificationForManagers() {
        Task task = new Task();
        task.setId(77L);
        task.setTitle("Implement API Gateway");
        task.setStatus("PENDING");
        task.setEmployee(regularEmp);

        when(taskRepository.findById(77L)).thenReturn(Optional.of(task));
        when(userRepository.findByRole("MANAGER")).thenReturn(Collections.singletonList(managerUser));

        RedirectAttributes ra = new RedirectAttributesModelMap();
        taskController.updateTask(77L, "COMPLETED", 100, session, ra);

        verify(notificationRepository, atLeastOnce()).save(argThat(n ->
                n.getUserRecipient() != null &&
                "MANAGER".equals(n.getUserRecipient().getRole()) &&
                n.getActionUrl() != null &&
                n.getActionUrl().contains("/manager/tasks/details/77")
        ));
    }

    @Test
    @DisplayName("LOG-MGR-008: Notification getActionUrl resolves correctly based on type and content")
    void testNotificationActionUrlResolution() {
        Notification taskNotif = new Notification(managerUser, "Task Update: Employee updated task", null);
        assertEquals("/manager/dashboard?tab=planning", taskNotif.getActionUrl());

        Notification suggNotif = new Notification(managerUser, "New Suggestion submitted", null);
        assertEquals("/manager/dashboard?tab=suggestions", suggNotif.getActionUrl());

        Notification meetingNotif = new Notification(managerUser, "New Meeting: Sync", 88L);
        assertEquals("#", meetingNotif.getActionUrl());

        Notification customNotif = new Notification(managerUser, "Custom alert", null, "/manager/custom-path");
        assertEquals("/manager/custom-path", customNotif.getActionUrl());
    }

    @Test
    @DisplayName("Verify Projects & Reports section (Issues, Sprints, Reports, Notifications) removed from manager sidebar")
    void testProjectsAndReportsRemovedFromManagerSidebar() throws Exception {
        String sidebarContent = Files.readString(Paths.get("src/main/resources/templates/fragments/manager-sidebar.html"));
        assertFalse(sidebarContent.contains("Projects & Reports"),
                "manager-sidebar.html must NOT contain Projects & Reports header");
        assertFalse(sidebarContent.contains("th:href=\"@{/issues}\""),
                "manager-sidebar.html must NOT contain Issues link");
        assertFalse(sidebarContent.contains("th:href=\"@{/sprints}\""),
                "manager-sidebar.html must NOT contain Sprints link");
        assertFalse(sidebarContent.contains("th:href=\"@{/reports}\""),
                "manager-sidebar.html must NOT contain Reports link");
        assertFalse(sidebarContent.contains("th:href=\"@{/manager/notifications/history}\""),
                "manager-sidebar.html must NOT contain Notifications link");
    }
}
