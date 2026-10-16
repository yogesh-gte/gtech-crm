package com.example.demo.service;

import com.example.demo.model.Employee;
import com.example.demo.model.Meeting;
import com.example.demo.model.Payroll;
import com.example.demo.model.Task;
import com.example.demo.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/**
 * Centralized service for role-based authorization, session principal extraction,
 * and fine-grained record ownership / IDOR access control.
 */
@Service
public class AuthorizationService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_HR = "HR";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";

    /**
     * Extracts authenticated User entity from session.
     */
    public User getAuthenticatedUser(HttpSession session) {
        if (session == null) return null;
        Object userObj = session.getAttribute("user");
        return (userObj instanceof User) ? (User) userObj : null;
    }

    /**
     * Extracts authenticated Employee entity from session.
     */
    public Employee getAuthenticatedEmployee(HttpSession session) {
        if (session == null) return null;
        Object empObj = session.getAttribute("employee");
        return (empObj instanceof Employee) ? (Employee) empObj : null;
    }

    /**
     * Returns true if any principal (User or Employee) is authenticated.
     */
    public boolean isAuthenticated(HttpSession session) {
        return getAuthenticatedUser(session) != null || getAuthenticatedEmployee(session) != null;
    }

    /**
     * Returns the standardized role of the current principal ("ADMIN", "HR", "MANAGER", "EMPLOYEE", or null).
     */
    public String getUserRole(HttpSession session) {
        if (session == null) return null;
        User user = getAuthenticatedUser(session);
        if (user != null && user.getRole() != null) {
            return user.getRole().toUpperCase().trim();
        }
        Object roleAttr = session.getAttribute("userRole");
        if (roleAttr != null && !roleAttr.toString().trim().isEmpty()) {
            return roleAttr.toString().toUpperCase().trim();
        }
        if (getAuthenticatedEmployee(session) != null) {
            return ROLE_EMPLOYEE;
        }
        return null;
    }

    /**
     * Checks if current principal has the exact specified role.
     */
    public boolean hasRole(HttpSession session, String requiredRole) {
        if (requiredRole == null) return false;
        String currentRole = getUserRole(session);
        return requiredRole.equalsIgnoreCase(currentRole);
    }

    /**
     * Checks if current principal has any of the specified roles.
     */
    public boolean hasAnyRole(HttpSession session, String... requiredRoles) {
        if (requiredRoles == null) return false;
        String currentRole = getUserRole(session);
        if (currentRole == null) return false;
        for (String role : requiredRoles) {
            if (role != null && role.equalsIgnoreCase(currentRole)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAdmin(HttpSession session) {
        return hasRole(session, ROLE_ADMIN);
    }

    public boolean isHr(HttpSession session) {
        return hasRole(session, ROLE_HR);
    }

    public boolean isManager(HttpSession session) {
        return hasRole(session, ROLE_MANAGER);
    }

    public boolean isStaff(HttpSession session) {
        return hasAnyRole(session, ROLE_ADMIN, ROLE_HR, ROLE_MANAGER);
    }

    public boolean isEmployee(HttpSession session) {
        return getAuthenticatedEmployee(session) != null;
    }

    // =========================================================================
    // Fine-Grained Ownership & IDOR Authorization Checks
    // =========================================================================

    /**
     * Verifies if the authenticated caller is authorized to view the given Meeting.
     * Staff (ADMIN, HR, MANAGER) are authorized to view company meetings.
     * Regular employees are only authorized if they are:
     * - The organizer
     * - An attendee
     * - An employee participant
     * - The target employee
     */
    public boolean canAccessMeeting(HttpSession session, Meeting meeting) {
        if (meeting == null || !isAuthenticated(session)) return false;
        if (isStaff(session)) return true;

        Employee employee = getAuthenticatedEmployee(session);
        if (employee == null) return false;

        Long empId = employee.getId();
        if (empId == null) return false;

        // Check company-wide
        if (Boolean.TRUE.equals(meeting.getInviteEveryone())) {
            return true;
        }

        // Check organizer
        if (meeting.getOrganizer() != null && empId.equals(meeting.getOrganizer().getId())) {
            return true;
        }
        // Check attendees
        if (meeting.getAttendees() != null && meeting.getAttendees().stream().anyMatch(a -> a != null && empId.equals(a.getId()))) {
            return true;
        }
        // Check participants
        if (meeting.getEmployeeParticipants() != null && meeting.getEmployeeParticipants().stream().anyMatch(p -> p != null && empId.equals(p.getId()))) {
            return true;
        }
        // Check target employee
        if (meeting.getTargetEmployee() != null && empId.equals(meeting.getTargetEmployee().getId())) {
            return true;
        }
        return false;
    }

    /**
     * Verifies if caller is authorized to modify or end a meeting.
     * Only ADMIN or the meeting's organizer employee can modify/end it.
     */
    public boolean canModifyMeeting(HttpSession session, Meeting meeting) {
        if (meeting == null || !isAuthenticated(session)) return false;
        if (isAdmin(session)) return true;

        Employee employee = getAuthenticatedEmployee(session);
        if (employee != null && meeting.getOrganizer() != null) {
            return employee.getId() != null && employee.getId().equals(meeting.getOrganizer().getId());
        }
        return false;
    }

    /**
     * Verifies if caller can delete a task.
     * Allowed: ADMIN, MANAGER, or TeamLead within their team scope.
     */
    public boolean canDeleteTask(HttpSession session, Task task) {
        if (task == null || !isAuthenticated(session)) return false;
        if (isAdmin(session) || isManager(session)) return true;

        Employee employee = getAuthenticatedEmployee(session);
        if (employee != null && Boolean.TRUE.equals(employee.getIsTeamLead())) {
            if (task.getEmployee() != null && task.getEmployee().getTeam() != null && employee.getTeam() != null) {
                return task.getEmployee().getTeam().getId().equals(employee.getTeam().getId());
            }
        }
        return false;
    }

    /**
     * Verifies if caller can access a payroll record.
     * Allowed: ADMIN, HR, or the employee owner.
     */
    public boolean canAccessPayroll(HttpSession session, Payroll payroll) {
        if (payroll == null || !isAuthenticated(session)) return false;
        if (isAdmin(session) || isHr(session)) return true;

        Employee employee = getAuthenticatedEmployee(session);
        if (employee != null && payroll.getEmployee() != null) {
            return employee.getId() != null && employee.getId().equals(payroll.getEmployee().getId());
        }
        return false;
    }
}
