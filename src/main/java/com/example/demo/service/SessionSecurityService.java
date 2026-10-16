package com.example.demo.service;

import com.example.demo.model.Employee;
import com.example.demo.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing HTTP session security, session fixation prevention via ID rotation,
 * secure cookie invalidation, targeted user session invalidation on password change/reset,
 * and session attribute credential sanitization.
 */
@Service
public class SessionSecurityService implements HttpSessionListener {

    public static final String SESSION_COOKIE_NAME = "JSESSIONID";

    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean defaultCookieSecure;

    private final ConcurrentHashMap<String, HttpSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> userSessionMap = new ConcurrentHashMap<>();

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        if (se != null && se.getSession() != null) {
            HttpSession session = se.getSession();
            activeSessions.put(session.getId(), session);
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        if (se != null && se.getSession() != null) {
            HttpSession session = se.getSession();
            String id = session.getId();
            activeSessions.remove(id);
            for (Set<String> sessions : userSessionMap.values()) {
                sessions.remove(id);
            }
        }
    }

    /**
     * Prevents session fixation attacks by rotating the session identifier upon successful authentication.
     * Uses Servlet 3.1+ request.changeSessionId(), falling back to safe attribute migration if needed.
     *
     * @param request the current HTTP servlet request
     * @return the new/rotated HttpSession
     */
    public HttpSession rotateSession(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        HttpSession existingSession = request.getSession(false);
        if (existingSession == null) {
            HttpSession newSession = request.getSession(true);
            activeSessions.put(newSession.getId(), newSession);
            return newSession;
        }

        String oldId = existingSession.getId();
        activeSessions.remove(oldId);

        try {
            // Supported Servlet container rotation (Tomcat / Servlet 3.1+)
            request.changeSessionId();
            HttpSession rotatedSession = request.getSession(false);
            if (rotatedSession != null) {
                activeSessions.put(rotatedSession.getId(), rotatedSession);
                return rotatedSession;
            }
        } catch (Exception e) {
            // Fallback: manual session attribute migration
            Map<String, Object> attributes = new HashMap<>();
            Enumeration<String> names = existingSession.getAttributeNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                attributes.put(name, existingSession.getAttribute(name));
            }

            try {
                existingSession.invalidate();
            } catch (IllegalStateException ignored) {
            }

            HttpSession newSession = request.getSession(true);
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                newSession.setAttribute(entry.getKey(), entry.getValue());
            }
            activeSessions.put(newSession.getId(), newSession);
            return newSession;
        }

        return request.getSession(true);
    }

    /**
     * Associates an authenticated user's email with their active session ID.
     */
    public void registerUserSession(String email, HttpSession session) {
        if (email == null || session == null) {
            return;
        }
        activeSessions.put(session.getId(), session);
        userSessionMap.computeIfAbsent(email.trim().toLowerCase(), k -> ConcurrentHashMap.newKeySet())
                .add(session.getId());
    }

    /**
     * Invalidates all active sessions for a specific user (e.g. following password reset, password change,
     * or account disablement) without affecting any unrelated sessions.
     *
     * @param email the user or employee email address
     */
    public void invalidateSessionsForUser(String email) {
        if (email == null) {
            return;
        }
        Set<String> sessionIds = userSessionMap.remove(email.trim().toLowerCase());
        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                HttpSession session = activeSessions.remove(sessionId);
                if (session != null) {
                    try {
                        session.invalidate();
                    } catch (IllegalStateException ignored) {
                        // Already invalidated
                    }
                }
            }
        }
    }

    /**
     * Sets an expired JSESSIONID cookie on the response with Max-Age=0, Path=/, HttpOnly=true, and SameSite=Lax.
     *
     * @param response the HTTP servlet response
     * @param secure whether the cookie should include the Secure attribute
     */
    public void expireSessionCookie(HttpServletResponse response, boolean secure) {
        if (response == null) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE_NAME, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void expireSessionCookie(HttpServletResponse response) {
        expireSessionCookie(response, defaultCookieSecure);
    }

    /**
     * Creates a defensive detached copy of User for session storage with sensitive credentials removed.
     * Guarantees password hashes, reset tokens, and expiry timestamps are never held in the session.
     */
    public User sanitizeUserForSession(User user) {
        if (user == null) {
            return null;
        }
        User safe = new User();
        safe.setId(user.getId());
        safe.setEmail(user.getEmail());
        safe.setName(user.getName());
        safe.setRole(user.getRole());
        safe.setCreatedAt(user.getCreatedAt());
        safe.setPassword(null); // Never store raw or hashed password in session
        safe.setResetToken(null); // Never store reset token in session
        safe.setResetTokenExpiry(null);
        return safe;
    }

    /**
     * Creates a defensive detached copy of Employee for session storage with sensitive credentials removed.
     * Guarantees password and heavy faceImage base64 payloads are never held in the session.
     */
    public Employee sanitizeEmployeeForSession(Employee employee) {
        if (employee == null) {
            return null;
        }
        Employee safe = new Employee();
        safe.setId(employee.getId());
        safe.setName(employee.getName());
        safe.setEmail(employee.getEmail());
        safe.setEmployeeId(employee.getEmployeeId());
        safe.setDepartment(employee.getDepartment());
        safe.setTeam(employee.getTeam());
        safe.setIsTeamLead(employee.getIsTeamLead());
        safe.setDesignation(employee.getDesignation());
        safe.setPhone(employee.getPhone());
        safe.setStatus(employee.getStatus());
        safe.setCurrentCheckIn(employee.getCurrentCheckIn());
        safe.setCurrentCheckOut(employee.getCurrentCheckOut());
        safe.setTotalHoursToday(employee.getTotalHoursToday());
        safe.setScheduledStartTime(employee.getScheduledStartTime());
        safe.setScheduledEndTime(employee.getScheduledEndTime());
        safe.setWorkMode(employee.getWorkMode());
        safe.setQrCodeToken(employee.getQrCodeToken());
        safe.setIsActive(employee.getIsActive());
        safe.setJoinDate(employee.getJoinDate());
        safe.setPaidLeave(employee.getPaidLeave());
        safe.setSickLeave(employee.getSickLeave());
        safe.setCasualLeave(employee.getCasualLeave());
        safe.setCustomLeaves(employee.getCustomLeaves());
        safe.setSalary(employee.getSalary());
        safe.setPassword(null); // Never store employee password in session
        safe.setFaceImage(null); // Omit heavy image payload
        return safe;
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public boolean isUserSessionActive(String email) {
        if (email == null) return false;
        Set<String> sessions = userSessionMap.get(email.trim().toLowerCase());
        return sessions != null && !sessions.isEmpty();
    }
}
