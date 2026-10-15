package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.ContextPathUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContextPathUtil contextPathUtil;

    @Value("${server.port:8080}")
    private int serverPort;

    public void sendVisitorArrivalNotification(String employeeEmail, String visitorName, String visitorCompany,
            String visitorPurpose, String personToMeet, java.time.LocalDateTime checkInTime) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(employeeEmail);
            helper.setFrom("suchisuchithra184@gmail.com");
            helper.setSubject("Visitor Arrived - " + visitorName + " is at Reception");

            String checkInTimeStr = checkInTime != null
                    ? checkInTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // Escape HTML to prevent XSS
            String safeVisitorName = visitorName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safeCompany = visitorCompany.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safePurpose = (visitorPurpose != null ? visitorPurpose : "N/A").replace("&", "&amp;")
                    .replace("<", "&lt;").replace(">", "&gt;");
            String safePersonToMeet = (personToMeet != null ? personToMeet : "You").replace("&", "&amp;")
                    .replace("<", "&lt;").replace(">", "&gt;");

            // Plain text version
            String plainText = "Dear Employee,\n\n" +
                    "Your visitor has arrived!\n\n" +
                    "Visitor Details:\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                    "Visitor Name: " + visitorName + "\n" +
                    "Company: " + visitorCompany + "\n" +
                    "Person to Meet: " + (personToMeet != null ? personToMeet : "You") + "\n" +
                    "Purpose: " + (visitorPurpose != null ? visitorPurpose : "N/A") + "\n" +
                    "Check-In Time: " + checkInTimeStr + "\n" +
                    "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                    "Your visitor is waiting at the reception. Please proceed to meet them.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System";

            // HTML version
            String htmlContent = "<!DOCTYPE html>" +
                    "<html>" +
                    "<head>" +
                    "<meta charset='UTF-8'>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                    "</head>" +
                    "<body style='margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;'>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f4f4f4; padding: 20px;'>"
                    +
                    "<tr>" +
                    "<td align='center'>" +
                    "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                    +
                    "<tr>" +
                    "<td style='padding: 30px; background-color: #28a745;'>" +
                    "<h2 style='margin: 0; color: #ffffff; font-size: 24px;'>✓ Visitor Arrived</h2>" +
                    "</td>" +
                    "</tr>" +
                    "<tr>" +
                    "<td style='padding: 30px;'>" +
                    "<p style='margin: 0 0 15px 0; font-size: 16px; color: #333333;'>Dear Employee,</p>" +
                    "<p style='margin: 0 0 20px 0; font-size: 18px; color: #28a745; font-weight: bold;'>Your visitor has arrived!</p>"
                    +
                    "<p style='margin: 0 0 20px 0; font-size: 16px; color: #333333;'>Please proceed to the reception to meet your visitor.</p>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f9f9f9; border: 1px solid #e0e0e0; border-radius: 5px; margin: 20px 0;'>"
                    +
                    "<tr><td style='padding: 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Visitor Name:</strong> "
                    + safeVisitorName + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Company:</strong> "
                    + safeCompany + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Person to Meet:</strong> "
                    + safePersonToMeet + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Purpose:</strong> "
                    + safePurpose + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px 15px 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Check-In Time:</strong> "
                    + checkInTimeStr + "</p></td></tr>" +
                    "</table>" +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>Thank you,<br>LogSphere System</p>"
                    +
                    "</td>" +
                    "</tr>" +
                    "</table>" +
                    "</td>" +
                    "</tr>" +
                    "</table>" +
                    "</body>" +
                    "</html>";

            helper.setText(plainText, htmlContent);
            mailSender.send(message);
            System.out.println("Visitor arrival notification sent to employee: " + employeeEmail);
        } catch (Exception e) {
            System.err.println("Failed to send visitor arrival notification email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendVisitorApprovalRequest(String employeeEmail, String visitorName, String visitorCompany,
            String purpose) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setSubject("Visitor Approval Request - LogSphere");
            message.setText("Dear Employee,\n\n" +
                    "A visitor request has been created:\n\n" +
                    "Visitor Name: " + visitorName + "\n" +
                    "Company: " + visitorCompany + "\n" +
                    "Purpose: " + purpose + "\n\n" +
                    "Please log in to approve or deny this visitor request.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System");

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }

    public void sendVisitorRegistrationNotification(String employeeEmail, String visitorName,
            String visitorCompany, String purpose,
            String personToMeet, java.time.LocalDateTime registeredTime, Long visitorId, String visitorEmail) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(employeeEmail);
            helper.setFrom("suchisuchithra184@gmail.com");
            helper.setSubject("Visitor Registration Notification - LogSphere");

            String registeredTimeStr = registeredTime
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // Base URL for approval links (use localhost for development, change to your
            // domain for production)
            // Get context path dynamically (works for both embedded and external Tomcat
            // deployment)
            String contextPath = contextPathUtil.getContextPath();
            String baseUrl = "http://localhost:" + serverPort
                    + (contextPath != null && !contextPath.isEmpty() ? contextPath : "");

            // Encode employee email for URL
            String encodedEmployeeEmail = java.net.URLEncoder.encode(employeeEmail,
                    java.nio.charset.StandardCharsets.UTF_8);

            // Create HTTP links that directly approve/deny/reject/busy the visitor
            // Employee clicks button → visitor is approved and admin gets email
            // notification
            String approveLink = baseUrl + "/employee/approvals/email/approve/" + visitorId + "?email="
                    + encodedEmployeeEmail;
            String denyLink = baseUrl + "/employee/approvals/email/deny/" + visitorId + "?email="
                    + encodedEmployeeEmail;
            String rejectLink = baseUrl + "/employee/approvals/email/reject/" + visitorId + "?email="
                    + encodedEmployeeEmail;
            String busyLink = baseUrl + "/employee/approvals/email/busy/" + visitorId + "?email="
                    + encodedEmployeeEmail;

            // Escape HTML to prevent XSS
            String safeVisitorName = visitorName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safeCompany = visitorCompany.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safePersonToMeet = (personToMeet != null ? personToMeet : "You").replace("&", "&amp;")
                    .replace("<", "&lt;").replace(">", "&gt;");
            String safePurpose = purpose.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

            // Use visitorEmail parameter that's now passed to the method

            // Create plain text version with mailto links
            String plainText = "Dear Employee,\n\n" +
                    "A visitor has been registered to meet you:\n\n" +
                    "Visitor Name: " + visitorName + "\n" +
                    "Company: " + visitorCompany + "\n" +
                    "Person to Meet: " + (personToMeet != null ? personToMeet : "You") + "\n" +
                    "Purpose: " + purpose + "\n" +
                    "Registered Time: " + registeredTimeStr + "\n\n" +
                    "ACTION REQUIRED: Click one of the buttons below to approve, deny, reject, or mark as busy.\n\n" +
                    "Clicking a button will automatically update the visitor status and send notifications to admin and visitor.\n\n"
                    +
                    "After approval, the visitor can check-in using their ID proof at the reception.\n\n" +
                    "Thank you,\nLogSphere System";

            // Create HTML email with table-based layout for better email client
            // compatibility
            String htmlContent = "<!DOCTYPE html>" +
                    "<html>" +
                    "<head>" +
                    "<meta charset='UTF-8'>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                    "</head>" +
                    "<body style='margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;'>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f4f4f4; padding: 20px;'>"
                    +
                    "<tr>" +
                    "<td align='center'>" +
                    "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                    +
                    "<tr>" +
                    "<td style='padding: 30px; background-color: #ffc107;'>" +
                    "<h2 style='margin: 0; color: #000000; font-size: 24px;'>Visitor Registration Notification</h2>" +
                    "</td>" +
                    "</tr>" +
                    "<tr>" +
                    "<td style='padding: 30px;'>" +
                    "<p style='margin: 0 0 15px 0; font-size: 16px; color: #333333;'>Dear Employee,</p>" +
                    "<p style='margin: 0 0 20px 0; font-size: 16px; color: #333333;'>A visitor has been registered to meet you:</p>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f9f9f9; border: 1px solid #e0e0e0; border-radius: 5px; margin: 20px 0;'>"
                    +
                    "<tr><td style='padding: 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Visitor Name:</strong> "
                    + safeVisitorName + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Company:</strong> "
                    + safeCompany + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Person to Meet:</strong> "
                    + safePersonToMeet + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Purpose:</strong> "
                    + safePurpose + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px 15px 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Registered Time:</strong> "
                    + registeredTimeStr + "</p></td></tr>" +
                    "</table>" +
                    "<h3 style='margin: 30px 0 20px 0; color: #dc3545; font-size: 18px;'>ACTION REQUIRED: Please select an action</h3>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='margin: 30px 0;'>"
                    +
                    "<tr>" +
                    "<td align='center' style='padding: 5px; width: 25%;'>" +
                    "<a href='" + approveLink
                    + "' style='display: block; padding: 15px 20px; background-color: #28a745; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 14px; text-align: center;'>✓ Approve</a>"
                    +
                    "</td>" +
                    "<td align='center' style='padding: 5px; width: 25%;'>" +
                    "<a href='" + denyLink
                    + "' style='display: block; padding: 15px 20px; background-color: #dc3545; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 14px; text-align: center;'>✗ Deny</a>"
                    +
                    "</td>" +
                    "<td align='center' style='padding: 5px; width: 25%;'>" +
                    "<a href='" + rejectLink
                    + "' style='display: block; padding: 15px 20px; background-color: #6c757d; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 14px; text-align: center;'>⊘ Reject</a>"
                    +
                    "</td>" +
                    "<td align='center' style='padding: 5px; width: 25%;'>" +
                    "<a href='" + busyLink
                    + "' style='display: block; padding: 15px 20px; background-color: #ffc107; color: #000000; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 14px; text-align: center;'>⏰ Busy</a>"
                    +
                    "</td>" +
                    "</tr>" +
                    "</table>" +
                    "<p style='margin: 20px 0 10px 0; color: #6c757d; font-size: 12px;'>Note: Clicking the buttons above will automatically approve/deny/reject/mark as busy the visitor and send notifications to admin and visitor. "
                    +
                    "No login required - the system verifies your email automatically.</p>" +
                    "<p style='margin: 10px 0; font-size: 14px; color: #333333;'>After approval, the visitor can check-in using their ID proof at the reception.</p>"
                    +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>Thank you,<br>LogSphere System</p>"
                    +
                    "</td>" +
                    "</tr>" +
                    "</table>" +
                    "</td>" +
                    "</tr>" +
                    "</table>" +
                    "</body>" +
                    "</html>";

            helper.setText(plainText, htmlContent);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendLateArrivalAlert(String employeeEmail, String employeeName, String scheduledTime,
            String actualTime) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setSubject("Late Arrival Alert - LogSphere");
            message.setText("Dear " + employeeName + ",\n\n" +
                    "You have arrived late today.\n\n" +
                    "Scheduled Time: " + scheduledTime + "\n" +
                    "Actual Arrival: " + actualTime + "\n\n" +
                    "Please ensure you arrive on time for your scheduled shift.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System");

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }

    public void sendEarlyDepartureAlert(String employeeEmail, String employeeName, String scheduledTime,
            String actualTime) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setSubject("Early Departure Alert - LogSphere");
            message.setText("Dear " + employeeName + ",\n\n" +
                    "You have left earlier than your scheduled shift time.\n\n" +
                    "Scheduled End Time: " + scheduledTime + "\n" +
                    "Actual Departure: " + actualTime + "\n\n" +
                    "Please ensure you complete your full scheduled shift.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System");

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }

    public void sendMissedClockOutAlert(String employeeEmail, String employeeName, String checkInTime) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setSubject("Missed Clock Out Alert - LogSphere");
            message.setText("Dear " + employeeName + ",\n\n" +
                    "You have not clocked out today.\n\n" +
                    "Check-In Time: " + checkInTime + "\n" +
                    "Current Status: Still Clocked In\n\n" +
                    "Please remember to clock out when leaving.\n" +
                    "If you have already left, please contact HR.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System");

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }

    public void sendMissedClockOutAlertToAdmin(String adminEmail, String employeeName, String employeeId,
            String checkInTime) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            message.setSubject("Employee Missed Clock Out - LogSphere");
            message.setText("Dear Admin,\n\n" +
                    "An employee has not clocked out today:\n\n" +
                    "Employee Name: " + employeeName + "\n" +
                    "Employee ID: " + employeeId + "\n" +
                    "Check-In Time: " + checkInTime + "\n" +
                    "Current Status: Still Clocked In\n\n" +
                    "Please follow up with the employee.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System");

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }

    public void sendIdleAlert(String employeeEmail, String employeeName, String idleHours) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setSubject("Idle Activity Alert - LogSphere");
            message.setText("Dear " + employeeName + ",\n\n" +
                    "You have been inactive for " + idleHours + " hours.\n\n" +
                    "Please ensure you are actively working during your scheduled hours.\n\n" +
                    "If you are working, please update your task progress to reset the idle timer.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System");

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }

    public void sendIdleAlertToAdmin(String adminEmail, String employeeName, String employeeId, String idleMinutes) {
        try {
            System.out.println("Sending idle alert to admin: " + adminEmail);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("⚠️ Employee Idle Alert - LogSphere");

            String emailBody = "Dear Admin,\n\n" +
                    "⚠️ An employee has been detected as idle:\n\n" +
                    "Employee Name: " + employeeName + "\n" +
                    "Employee ID: " + employeeId + "\n" +
                    "Idle Duration: " + idleMinutes + " minutes\n\n" +
                    "This employee has been inactive for " + idleMinutes + " minutes.\n" +
                    "Please check the admin dashboard for more details and follow up with the employee.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System";

            message.setText(emailBody);
            mailSender.send(message);
            System.out.println("Idle alert email sent successfully to admin: " + adminEmail);
        } catch (Exception e) {
            System.err.println("Failed to send idle alert email to admin " + adminEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendIdleWarningAlert(String employeeEmail, String employeeName, String idleMinutes) {
        try {
            System.out.println("Sending idle warning email to: " + employeeEmail);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("⚠️ Idle Activity Warning - LogSphere");

            String emailBody = "Dear " + employeeName + ",\n\n" +
                    "⚠️ WARNING: You have been inactive for " + idleMinutes + " minutes.\n\n" +
                    "The system has detected no activity on your screen for the past " + idleMinutes + " minutes.\n\n" +
                    "Please ensure you are actively working. Move your mouse or use your keyboard to reset the activity timer.\n\n"
                    +
                    "If you continue to be inactive, this will be reported to your supervisor.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System";

            message.setText(emailBody);
            mailSender.send(message);
            System.out.println("Idle warning email sent successfully to: " + employeeEmail);
        } catch (Exception e) {
            System.err.println("Failed to send idle warning email to " + employeeEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setFrom("suchisuchithra184@gmail.com");
            helper.setSubject("Password Reset Request - LogSphere");

            String plainText = "Dear User,\n\n" +
                    "To reset your password, please click the link below:\n\n" +
                    resetUrl + "\n\n" +
                    "This link will expire in 15 minutes.\n\n" +
                    "If you did not request a password reset, please ignore this email.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System";

            String htmlContent = "<!DOCTYPE html>" +
                    "<html><head><meta charset='UTF-8'></head>" +
                    "<body style='margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;'>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f4f4f4; padding: 20px;'>"
                    +
                    "<tr><td align='center'>" +
                    "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                    +
                    "<tr><td style='padding: 30px; background-color: #1a8cf0;'><h2 style='margin: 0; color: #ffffff; font-size: 24px;'>Password Reset Request</h2></td></tr>"
                    +
                    "<tr><td style='padding: 30px;'>" +
                    "<p style='margin: 0 0 15px 0; font-size: 16px; color: #333333;'>Dear User,</p>" +
                    "<p style='margin: 0 0 20px 0; font-size: 16px; color: #333333;'>We received a request to reset your password.</p>"
                    +
                    "<div style='text-align: center; margin: 30px 0;'>" +
                    "<a href='" + resetUrl
                    + "' style='background-color: #1a8cf0; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 16px;'>Reset Password</a>"
                    +
                    "</div>" +
                    "<p style='margin: 0 0 10px 0; font-size: 14px; color: #666;'>Or copy and paste this link into your browser:</p>"
                    +
                    "<p style='margin: 0 0 20px 0; font-size: 14px; color: #1a8cf0; word-break: break-all;'>" + resetUrl
                    + "</p>" +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>This link will expire in 15 minutes.</p>"
                    +
                    "<p style='margin: 10px 0 0 0; font-size: 14px; color: #666;'>If you did not request a password reset, please ignore this email.</p>"
                    +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>Thank you,<br>LogSphere System</p>"
                    +
                    "</td></tr></table></td></tr></table></body></html>";

            helper.setText(plainText, htmlContent);
            mailSender.send(message);
            System.out.println("Password reset email sent to: " + toEmail);
        } catch (Exception e) {
            System.err.println("Failed to send password reset email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendVisitorApprovalConfirmation(String visitorEmail, String visitorName,
            String employeeName, java.time.LocalDateTime registeredTime) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(visitorEmail);
            helper.setFrom("suchisuchithra184@gmail.com");
            helper.setSubject("Visitor Request Approved - LogSphere");

            String registeredTimeStr = registeredTime
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String baseUrl = "http://localhost:8080";

            // Plain text version
            String plainText = "Dear " + visitorName + ",\n\n" +
                    "Your visit request has been APPROVED by " + employeeName + ".\n\n" +
                    "Registered Time: " + registeredTimeStr + "\n\n" +
                    "You can now check-in at the reception using the ID proof (Gov ID) you provided during registration.\n\n"
                    +
                    "Visit the reception desk to complete your check-in.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System";

            // HTML version
            String htmlContent = "<!DOCTYPE html>" +
                    "<html>" +
                    "<head><meta charset='UTF-8'></head>" +
                    "<body style='margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;'>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f4f4f4; padding: 20px;'>"
                    +
                    "<tr><td align='center'>" +
                    "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                    +
                    "<tr><td style='padding: 30px; background-color: #28a745;'><h2 style='margin: 0; color: #ffffff; font-size: 24px;'>✓ Request Approved</h2></td></tr>"
                    +
                    "<tr><td style='padding: 30px;'>" +
                    "<p style='margin: 0 0 15px 0; font-size: 16px; color: #333333;'>Dear "
                    + visitorName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + ",</p>" +
                    "<p style='margin: 0 0 20px 0; font-size: 16px; color: #333333;'>Your visit request has been <strong style='color: #28a745;'>APPROVED</strong> by "
                    + employeeName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + ".</p>" +
                    "<div style='background-color: #f9f9f9; border: 1px solid #e0e0e0; border-radius: 5px; padding: 15px; margin: 20px 0;'>"
                    +
                    "<p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Registered Time:</strong> "
                    + registeredTimeStr + "</p>" +
                    "</div>" +
                    "<div style='background-color: #d4edda; border: 2px solid #28a745; border-radius: 5px; padding: 20px; margin: 20px 0; text-align: center;'>"
                    +
                    "<h3 style='margin: 0 0 10px 0; color: #155724; font-size: 18px;'>You can now check-in!</h3>" +
                    "<p style='margin: 0; font-size: 14px; color: #155724;'>Please visit the reception desk and provide your ID proof (Gov ID) to complete your check-in.</p>"
                    +
                    "</div>" +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>Thank you,<br>LogSphere System</p>"
                    +
                    "</td></tr></table></td></tr></table></body></html>";

            helper.setText(plainText, htmlContent);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendVisitorApprovalNotificationToAdmin(String adminEmail, String adminName, String visitorName,
            String visitorCompany, String purpose, String personToMeet,
            String employeeName, java.time.LocalDateTime registeredTime,
            java.time.LocalDateTime approvalTime) {
        try {
            System.out.println("Attempting to send admin approval notification to: " + adminEmail);

            if (adminEmail == null || adminEmail.isEmpty()) {
                System.err.println("ERROR: Admin email is null or empty!");
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(adminEmail);
            helper.setFrom("suchisuchithra184@gmail.com");
            helper.setSubject("Visitor Approved by Employee - LogSphere");

            String registeredTimeStr = registeredTime
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String approvalTimeStr = approvalTime
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // Escape HTML
            String safeAdminName = adminName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safeVisitorName = visitorName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safeCompany = visitorCompany.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safePurpose = purpose.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safePersonToMeet = (personToMeet != null ? personToMeet : "N/A").replace("&", "&amp;")
                    .replace("<", "&lt;").replace(">", "&gt;");
            String safeEmployeeName = employeeName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

            // Plain text version
            String plainText = "Dear " + adminName + ",\n\n" +
                    "Employee " + employeeName + " has APPROVED that visitor " + visitorName + " can visit.\n\n" +
                    "Visitor Details:\n" +
                    "Visitor Name: " + visitorName + "\n" +
                    "Company: " + visitorCompany + "\n" +
                    "Person to Meet: " + (personToMeet != null ? personToMeet : "N/A") + "\n" +
                    "Purpose: " + purpose + "\n" +
                    "Registered Time: " + registeredTimeStr + "\n" +
                    "Approved Time: " + approvalTimeStr + "\n" +
                    "Approved By: " + employeeName + "\n\n" +
                    "The visitor can now check-in at the reception using their ID proof (Gov ID).\n\n" +
                    "Thank you,\n" +
                    "LogSphere System";

            // HTML version
            String htmlContent = "<!DOCTYPE html>" +
                    "<html><head><meta charset='UTF-8'></head>" +
                    "<body style='margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;'>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f4f4f4; padding: 20px;'>"
                    +
                    "<tr><td align='center'>" +
                    "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                    +
                    "<tr><td style='padding: 30px; background-color: #28a745;'><h2 style='margin: 0; color: #ffffff; font-size: 24px;'>✓ Visitor Approved by Employee</h2></td></tr>"
                    +
                    "<tr><td style='padding: 30px;'>" +
                    "<p style='margin: 0 0 15px 0; font-size: 16px; color: #333333;'>Dear " + safeAdminName + ",</p>" +
                    "<p style='margin: 0 0 20px 0; font-size: 16px; color: #333333;'><strong>Employee "
                    + safeEmployeeName + " has APPROVED that visitor " + safeVisitorName + " can visit.</strong></p>" +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f9f9f9; border: 1px solid #e0e0e0; border-radius: 5px; margin: 20px 0;'>"
                    +
                    "<tr><td style='padding: 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Visitor Name:</strong> "
                    + safeVisitorName + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Company:</strong> "
                    + safeCompany + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Person to Meet:</strong> "
                    + safePersonToMeet + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Purpose:</strong> "
                    + safePurpose + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Registered Time:</strong> "
                    + registeredTimeStr + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Approved Time:</strong> "
                    + approvalTimeStr + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px 15px 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Approved By:</strong> "
                    + safeEmployeeName + "</p></td></tr>" +
                    "</table>" +
                    "<div style='background-color: #d4edda; border: 2px solid #28a745; border-radius: 5px; padding: 20px; margin: 20px 0;'>"
                    +
                    "<p style='margin: 0; font-size: 14px; color: #155724;'><strong>📌 Status:</strong> The visitor can now check-in at the reception using their ID proof (Gov ID).</p>"
                    +
                    "</div>" +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>Thank you,<br>LogSphere System</p>"
                    +
                    "</td></tr></table></td></tr></table></body></html>";

            helper.setText(plainText, htmlContent);
            mailSender.send(message);
            System.out.println("Admin approval notification email sent successfully to: " + adminEmail);
        } catch (Exception e) {
            System.err.println(
                    "Failed to send admin approval notification email to " + adminEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendVisitorDenialNotification(String visitorEmail, String visitorName, String employeeName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(visitorEmail);
            helper.setFrom("suchisuchithra184@gmail.com");
            helper.setSubject("Visitor Request Denied - LogSphere");

            String plainText = "Dear " + visitorName + ",\n\n" +
                    "We regret to inform you that your visit request has been DENIED by " + employeeName + ".\n\n" +
                    "Unfortunately, we cannot accommodate your visit at this time.\n\n" +
                    "If you have any questions, please contact the reception.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System";

            String htmlContent = "<!DOCTYPE html>" +
                    "<html><head><meta charset='UTF-8'></head>" +
                    "<body style='margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;'>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f4f4f4; padding: 20px;'>"
                    +
                    "<tr><td align='center'>" +
                    "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                    +
                    "<tr><td style='padding: 30px; background-color: #dc3545;'><h2 style='margin: 0; color: #ffffff; font-size: 24px;'>✗ Request Denied</h2></td></tr>"
                    +
                    "<tr><td style='padding: 30px;'>" +
                    "<p style='margin: 0 0 15px 0; font-size: 16px; color: #333333;'>Dear "
                    + visitorName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + ",</p>" +
                    "<p style='margin: 0 0 20px 0; font-size: 16px; color: #333333;'>We regret to inform you that your visit request has been <strong style='color: #dc3545;'>DENIED</strong> by "
                    + employeeName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + ".</p>" +
                    "<div style='background-color: #f8d7da; border: 2px solid #dc3545; border-radius: 5px; padding: 20px; margin: 20px 0;'>"
                    +
                    "<p style='margin: 0; font-size: 14px; color: #721c24;'>Unfortunately, we cannot accommodate your visit at this time.</p>"
                    +
                    "</div>" +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>If you have any questions, please contact the reception.</p>"
                    +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>Thank you,<br>LogSphere System</p>"
                    +
                    "</td></tr></table></td></tr></table></body></html>";

            helper.setText(plainText, htmlContent);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendVisitorRejectionNotification(String visitorEmail, String visitorName, String employeeName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(visitorEmail);
            helper.setFrom("suchisuchithra184@gmail.com");
            helper.setSubject("Visitor Request Rejected - LogSphere");

            String plainText = "Dear " + visitorName + ",\n\n" +
                    "Your visit request has been REJECTED by " + employeeName + ".\n\n" +
                    "We are unable to proceed with your visit request at this time.\n\n" +
                    "If you have any questions, please contact the reception.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System";

            String htmlContent = "<!DOCTYPE html>" +
                    "<html><head><meta charset='UTF-8'></head>" +
                    "<body style='margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;'>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f4f4f4; padding: 20px;'>"
                    +
                    "<tr><td align='center'>" +
                    "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                    +
                    "<tr><td style='padding: 30px; background-color: #6c757d;'><h2 style='margin: 0; color: #ffffff; font-size: 24px;'>⊘ Request Rejected</h2></td></tr>"
                    +
                    "<tr><td style='padding: 30px;'>" +
                    "<p style='margin: 0 0 15px 0; font-size: 16px; color: #333333;'>Dear "
                    + visitorName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + ",</p>" +
                    "<p style='margin: 0 0 20px 0; font-size: 16px; color: #333333;'>Your visit request has been <strong style='color: #6c757d;'>REJECTED</strong> by "
                    + employeeName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + ".</p>" +
                    "<div style='background-color: #e2e3e5; border: 2px solid #6c757d; border-radius: 5px; padding: 20px; margin: 20px 0;'>"
                    +
                    "<p style='margin: 0; font-size: 14px; color: #383d41;'>We are unable to proceed with your visit request at this time.</p>"
                    +
                    "</div>" +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>If you have any questions, please contact the reception.</p>"
                    +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>Thank you,<br>LogSphere System</p>"
                    +
                    "</td></tr></table></td></tr></table></body></html>";

            helper.setText(plainText, htmlContent);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendVisitorBusyNotification(String visitorEmail, String visitorName, String employeeName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(visitorEmail);
            helper.setFrom("suchisuchithra184@gmail.com");
            helper.setSubject("Employee Currently Busy - LogSphere");

            String plainText = "Dear " + visitorName + ",\n\n" +
                    "We wanted to inform you that " + employeeName
                    + " is currently BUSY and unable to meet at this time.\n\n" +
                    "Your visit request is still pending. We will notify you once " + employeeName
                    + " becomes available.\n\n" +
                    "You may contact the reception for more information or to reschedule.\n\n" +
                    "Thank you for your understanding,\n" +
                    "LogSphere System";

            String htmlContent = "<!DOCTYPE html>" +
                    "<html><head><meta charset='UTF-8'></head>" +
                    "<body style='margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;'>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f4f4f4; padding: 20px;'>"
                    +
                    "<tr><td align='center'>" +
                    "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                    +
                    "<tr><td style='padding: 30px; background-color: #ffc107;'><h2 style='margin: 0; color: #000000; font-size: 24px;'>⏰ Employee Busy</h2></td></tr>"
                    +
                    "<tr><td style='padding: 30px;'>" +
                    "<p style='margin: 0 0 15px 0; font-size: 16px; color: #333333;'>Dear "
                    + visitorName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + ",</p>" +
                    "<p style='margin: 0 0 20px 0; font-size: 16px; color: #333333;'>We wanted to inform you that <strong>"
                    + employeeName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    + "</strong> is currently <strong style='color: #ffc107;'>BUSY</strong> and unable to meet at this time.</p>"
                    +
                    "<div style='background-color: #fff3cd; border: 2px solid #ffc107; border-radius: 5px; padding: 20px; margin: 20px 0;'>"
                    +
                    "<p style='margin: 0 0 10px 0; font-size: 14px; color: #856404;'><strong>Your visit request is still pending.</strong></p>"
                    +
                    "<p style='margin: 0; font-size: 14px; color: #856404;'>We will notify you once "
                    + employeeName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    + " becomes available.</p>" +
                    "</div>" +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>You may contact the reception for more information or to reschedule.</p>"
                    +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>Thank you for your understanding,<br>LogSphere System</p>"
                    +
                    "</td></tr></table></td></tr></table></body></html>";

            helper.setText(plainText, htmlContent);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendMeetingNotificationToVisitor(String visitorEmail, String visitorName, String meetingTitle,
            String organizerName, java.time.LocalDateTime startTime,
            java.time.LocalDateTime endTime, String location, String description) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(visitorEmail);
            helper.setFrom("suchisuchithra184@gmail.com");
            helper.setSubject("Meeting Scheduled: " + meetingTitle + " - LogSphere");

            // Format dates and times
            String startDate = startTime.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"));
            String startTimeStr = startTime.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));
            String endTimeStr = endTime != null
                    ? endTime.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
                    : "TBD";
            String endDate = endTime != null
                    ? endTime.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"))
                    : "";

            // Calculate duration if end time is available
            String duration = "";
            if (endTime != null) {
                long hours = java.time.Duration.between(startTime, endTime).toHours();
                long minutes = java.time.Duration.between(startTime, endTime).toMinutes() % 60;
                if (hours > 0 && minutes > 0) {
                    duration = hours + " hour(s) and " + minutes + " minute(s)";
                } else if (hours > 0) {
                    duration = hours + " hour(s)";
                } else {
                    duration = minutes + " minute(s)";
                }
            }

            // Escape HTML
            String safeVisitorName = visitorName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safeMeetingTitle = meetingTitle.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safeOrganizerName = organizerName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String safeLocation = (location != null ? location : "TBD").replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;");
            String safeDescription = (description != null ? description : "").replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;");

            // Plain text version
            String plainText = "Dear " + visitorName + ",\n\n" +
                    "A meeting has been scheduled for you:\n\n" +
                    "Meeting Title: " + meetingTitle + "\n" +
                    "Organizer: " + organizerName + "\n" +
                    "Date: " + startDate + "\n" +
                    "Start Time: " + startTimeStr + "\n" +
                    (endTime != null ? "End Time: " + endTimeStr + "\n" : "") +
                    (duration != null && !duration.isEmpty() ? "Duration: " + duration + "\n" : "") +
                    "Location: " + (location != null ? location : "TBD") + "\n" +
                    (description != null && !description.isEmpty() ? "Description: " + description + "\n" : "") +
                    "\nPlease make sure to arrive on time for the meeting.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System";

            // HTML version
            String htmlContent = "<!DOCTYPE html>" +
                    "<html><head><meta charset='UTF-8'></head>" +
                    "<body style='margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;'>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f4f4f4; padding: 20px;'>"
                    +
                    "<tr><td align='center'>" +
                    "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                    +
                    "<tr><td style='padding: 30px; background-color: #007bff;'><h2 style='margin: 0; color: #ffffff; font-size: 24px;'>📅 Meeting Scheduled</h2></td></tr>"
                    +
                    "<tr><td style='padding: 30px;'>" +
                    "<p style='margin: 0 0 15px 0; font-size: 16px; color: #333333;'>Dear " + safeVisitorName + ",</p>"
                    +
                    "<p style='margin: 0 0 20px 0; font-size: 16px; color: #333333;'>A meeting has been scheduled for you. Please find the details below:</p>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f9f9f9; border: 1px solid #e0e0e0; border-radius: 5px; margin: 20px 0;'>"
                    +
                    "<tr><td style='padding: 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Meeting Title:</strong> "
                    + safeMeetingTitle + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Organizer:</strong> "
                    + safeOrganizerName + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Date:</strong> "
                    + startDate + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Start Time:</strong> "
                    + startTimeStr + "</p></td></tr>" +
                    (endTime != null
                            ? "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>End Time:</strong> "
                                    + endTimeStr + "</p></td></tr>"
                            : "")
                    +
                    (duration != null && !duration.isEmpty()
                            ? "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Duration:</strong> "
                                    + duration + "</p></td></tr>"
                            : "")
                    +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Location:</strong> "
                    + safeLocation + "</p></td></tr>" +
                    (description != null && !description.isEmpty()
                            ? "<tr><td style='padding: 0 15px 15px 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Description:</strong> "
                                    + safeDescription + "</p></td></tr>"
                            : "")
                    +
                    "</table>" +
                    "<div style='background-color: #d1ecf1; border: 2px solid #007bff; border-radius: 5px; padding: 20px; margin: 20px 0;'>"
                    +
                    "<p style='margin: 0; font-size: 14px; color: #0c5460;'><strong>📌 Important:</strong> Please make sure to arrive on time for the meeting.</p>"
                    +
                    "</div>" +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>Thank you,<br>LogSphere System</p>"
                    +
                    "</td></tr></table></td></tr></table></body></html>";

            helper.setText(plainText, htmlContent);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send meeting notification email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendMeetingReminder(String recipientEmail, String recipientName, String meetingTitle,
            String organizerName, String visitorName, String location,
            java.time.LocalDateTime startTime, java.time.LocalDateTime endTime,
            String timeUntilMeeting) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("Meeting Reminder: " + meetingTitle + " - LogSphere");

            String startTimeStr = startTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            String endTimeStr = endTime != null
                    ? endTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    : "TBD";

            StringBuilder emailBody = new StringBuilder();
            emailBody.append("Dear ").append(recipientName).append(",\n\n");
            emailBody.append("This is a reminder that you have a meeting scheduled:\n\n");
            emailBody.append("Meeting Title: ").append(meetingTitle).append("\n");
            emailBody.append("Organizer: ").append(organizerName).append("\n");

            if (visitorName != null && !visitorName.isEmpty()) {
                emailBody.append("Visitor: ").append(visitorName).append("\n");
            }

            if (location != null && !location.isEmpty()) {
                emailBody.append("Location: ").append(location).append("\n");
            }

            emailBody.append("Start Time: ").append(startTimeStr).append("\n");
            emailBody.append("End Time: ").append(endTimeStr).append("\n");
            emailBody.append("Time Until Meeting: ").append(timeUntilMeeting).append("\n\n");
            emailBody.append("Please make sure you are prepared and arrive on time.\n\n");
            emailBody.append("Thank you,\n");
            emailBody.append("LogSphere System");

            message.setText(emailBody.toString());
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send meeting reminder email: " + e.getMessage());
        }
    }

    public void sendClockInReminder(String employeeEmail, String employeeName, String scheduledTime) {
        try {
            System.out.println("Sending clock-in reminder email to: " + employeeEmail);
            System.out.println("Employee: " + employeeName + ", Scheduled time: " + scheduledTime);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("Clock-In Reminder - LogSphere");

            String currentTime = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            String emailBody = "Dear " + employeeName + ",\n\n" +
                    "⏰ REMINDER: You have 5 minutes until your scheduled clock-in time.\n\n" +
                    "Scheduled Clock-In Time: " + scheduledTime + "\n" +
                    "Current Time: " + currentTime + "\n" +
                    "Time Remaining: 5 minutes\n\n" +
                    "Please prepare to clock in on time.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System";

            message.setText(emailBody);
            mailSender.send(message);
            System.out.println("Clock-in reminder email sent successfully to: " + employeeEmail);
        } catch (Exception e) {
            System.err.println("Failed to send clock-in reminder email to " + employeeEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendClockOutReminder(String employeeEmail, String employeeName, String scheduledTime) {
        try {
            System.out.println("Sending clock-out reminder email to: " + employeeEmail);
            System.out.println("Employee: " + employeeName + ", Scheduled time: " + scheduledTime);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("Clock-Out Reminder - LogSphere");

            String currentTime = java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            String emailBody = "Dear " + employeeName + ",\n\n" +
                    "⏰ REMINDER: You have 5 minutes until your scheduled clock-out time.\n\n" +
                    "Scheduled Clock-Out Time: " + scheduledTime + "\n" +
                    "Current Time: " + currentTime + "\n" +
                    "Time Remaining: 5 minutes\n\n" +
                    "Please prepare to clock out on time.\n\n" +
                    "Thank you,\n" +
                    "LogSphere System";

            message.setText(emailBody);
            mailSender.send(message);
            System.out.println("Clock-out reminder email sent successfully to: " + employeeEmail);
        } catch (Exception e) {
            System.err.println("Failed to send clock-out reminder email to " + employeeEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendEarlyClockOutAlert(String employeeEmail, String employeeName, String scheduledTime,
            String actualTime) {
        try {
            System.out.println("Attempting to send early clock-out alert to: " + employeeEmail);
            System.out
                    .println("Employee: " + employeeName + ", Scheduled: " + scheduledTime + ", Actual: " + actualTime);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("Early Clock-Out Alert - LogSphere");

            // Check if clock-out is before 6:00 PM
            boolean before6PM = false;
            try {
                java.time.LocalTime actualTimeObj = java.time.LocalTime.parse(actualTime);
                java.time.LocalTime sixPM = java.time.LocalTime.of(18, 0);
                before6PM = actualTimeObj.isBefore(sixPM);
                System.out.println("Parsed time: " + actualTimeObj + ", Before 6 PM: " + before6PM);
            } catch (Exception e) {
                System.err.println("Error parsing time: " + actualTime + ", Error: " + e.getMessage());
                // If parsing fails, check if actualTime contains time before 18:00
                before6PM = actualTime.contains("17:") || actualTime.contains("16:") ||
                        actualTime.contains("15:") || actualTime.contains("14:") ||
                        actualTime.contains("13:") || actualTime.contains("12:") ||
                        actualTime.contains("11:") || actualTime.contains("10:") ||
                        actualTime.contains("09:") || actualTime.contains("08:") ||
                        actualTime.contains("07:") || actualTime.contains("06:") ||
                        actualTime.contains("05:") || actualTime.contains("04:") ||
                        actualTime.contains("03:") || actualTime.contains("02:") ||
                        actualTime.contains("01:") || actualTime.contains("00:");
            }

            String emailBody = "Dear " + employeeName + ",\n\n";

            if (before6PM) {
                emailBody += "⚠️ EARLY CLOCK-OUT ALERT: You have clocked out before 6:00 PM.\n\n";
            } else {
                emailBody += "⚠️ You have clocked out early today.\n\n";
            }

            emailBody += "Scheduled Clock-Out Time: " + scheduledTime + "\n";
            emailBody += "Actual Clock-Out Time: " + actualTime + "\n";

            if (before6PM) {
                emailBody += "Required Clock-Out Time: 6:00 PM (18:00)\n";
            }

            emailBody += "\nPlease ensure you complete your full scheduled shift.\n";
            emailBody += "Employees are expected to clock out at or after 6:00 PM.\n\n";
            emailBody += "Thank you,\n";
            emailBody += "LogSphere System";

            message.setText(emailBody);
            mailSender.send(message);
            System.out.println("Early clock-out alert email sent successfully to: " + employeeEmail);
        } catch (Exception e) {
            System.err
                    .println("Failed to send early clock-out alert email to " + employeeEmail + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendLeaveApprovalNotification(String employeeEmail, String employeeName, String leaveType,
            java.time.LocalDate startDate, java.time.LocalDate endDate,
            Integer numberOfDays) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(employeeEmail);
            helper.setFrom("suchisuchithra184@gmail.com");
            helper.setSubject("Leave Request Approved - LogSphere");

            String htmlContent = "<!DOCTYPE html>" +
                    "<html>" +
                    "<head>" +
                    "<meta charset='UTF-8'>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                    "</head>" +
                    "<body style='margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;'>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f4f4f4; padding: 20px;'>"
                    +
                    "<tr>" +
                    "<td align='center'>" +
                    "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                    +
                    "<tr>" +
                    "<td style='padding: 30px; background-color: #28a745;'>" +
                    "<h2 style='margin: 0; color: #ffffff; font-size: 24px;'>✓ Leave Request Approved</h2>" +
                    "</td>" +
                    "</tr>" +
                    "<tr>" +
                    "<td style='padding: 30px;'>" +
                    "<p style='margin: 0 0 15px 0; font-size: 16px; color: #333333;'>Dear " + employeeName + ",</p>" +
                    "<p style='margin: 0 0 20px 0; font-size: 16px; color: #333333;'>Your leave request has been approved!</p>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f9f9f9; border: 1px solid #e0e0e0; border-radius: 5px; margin: 20px 0;'>"
                    +
                    "<tr><td style='padding: 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Leave Type:</strong> "
                    + leaveType + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Start Date:</strong> "
                    + startDate + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>End Date:</strong> "
                    + endDate + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px 15px 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Number of Days:</strong> "
                    + numberOfDays + "</p></td></tr>" +
                    "</table>" +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>Thank you,<br>LogSphere HR Team</p>"
                    +
                    "</td>" +
                    "</tr>" +
                    "</table>" +
                    "</td>" +
                    "</tr>" +
                    "</table>" +
                    "</body>" +
                    "</html>";

            String plainText = "Dear " + employeeName + ",\n\n" +
                    "Your leave request has been approved!\n\n" +
                    "Leave Type: " + leaveType + "\n" +
                    "Start Date: " + startDate + "\n" +
                    "End Date: " + endDate + "\n" +
                    "Number of Days: " + numberOfDays + "\n\n" +
                    "Thank you,\n" +
                    "LogSphere HR Team";

            helper.setText(plainText, htmlContent);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send leave approval email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendLeaveRejectionNotification(String employeeEmail, String employeeName, String leaveType,
            java.time.LocalDate startDate, java.time.LocalDate endDate,
            String rejectionReason) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(employeeEmail);
            helper.setFrom("suchisuchithra184@gmail.com");
            helper.setSubject("Leave Request Rejected - LogSphere");

            String htmlContent = "<!DOCTYPE html>" +
                    "<html>" +
                    "<head>" +
                    "<meta charset='UTF-8'>" +
                    "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                    "</head>" +
                    "<body style='margin: 0; padding: 0; font-family: Arial, Helvetica, sans-serif; background-color: #f4f4f4;'>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f4f4f4; padding: 20px;'>"
                    +
                    "<tr>" +
                    "<td align='center'>" +
                    "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1);'>"
                    +
                    "<tr>" +
                    "<td style='padding: 30px; background-color: #dc3545;'>" +
                    "<h2 style='margin: 0; color: #ffffff; font-size: 24px;'>✗ Leave Request Rejected</h2>" +
                    "</td>" +
                    "</tr>" +
                    "<tr>" +
                    "<td style='padding: 30px;'>" +
                    "<p style='margin: 0 0 15px 0; font-size: 16px; color: #333333;'>Dear " + employeeName + ",</p>" +
                    "<p style='margin: 0 0 20px 0; font-size: 16px; color: #333333;'>We regret to inform you that your leave request has been rejected.</p>"
                    +
                    "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='background-color: #f9f9f9; border: 1px solid #e0e0e0; border-radius: 5px; margin: 20px 0;'>"
                    +
                    "<tr><td style='padding: 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Leave Type:</strong> "
                    + leaveType + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Start Date:</strong> "
                    + startDate + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>End Date:</strong> "
                    + endDate + "</p></td></tr>" +
                    "<tr><td style='padding: 0 15px 15px 15px;'><p style='margin: 5px 0; font-size: 14px; color: #333333;'><strong>Reason:</strong> "
                    + (rejectionReason != null ? rejectionReason : "Not specified") + "</p></td></tr>" +
                    "</table>" +
                    "<p style='margin: 20px 0 0 0; font-size: 14px; color: #333333;'>If you have any questions, please contact HR.<br>Thank you,<br>LogSphere HR Team</p>"
                    +
                    "</td>" +
                    "</tr>" +
                    "</table>" +
                    "</td>" +
                    "</tr>" +
                    "</table>" +
                    "</body>" +
                    "</html>";

            String plainText = "Dear " + employeeName + ",\n\n" +
                    "We regret to inform you that your leave request has been rejected.\n\n" +
                    "Leave Type: " + leaveType + "\n" +
                    "Start Date: " + startDate + "\n" +
                    "End Date: " + endDate + "\n" +
                    "Reason: " + (rejectionReason != null ? rejectionReason : "Not specified") + "\n\n" +
                    "If you have any questions, please contact HR.\n\n" +
                    "Thank you,\n" +
                    "LogSphere HR Team";

            helper.setText(plainText, htmlContent);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send leave rejection email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendMeetingInvitation(String recipientEmail, String recipientName, String meetingTitle,
            String organizerName, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime, String location,
            String description) {
        sendMeetingNotificationToVisitor(recipientEmail, recipientName, meetingTitle, organizerName, startTime, endTime,
                location, description);
    }

    public void sendInternalMeetingInvitation(String recipientEmail, String recipientName, String meetingTitle,
            String organizerName, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime, String location,
            String description) {
        sendMeetingNotificationToVisitor(recipientEmail, recipientName, meetingTitle, organizerName, startTime, endTime,
                location, description);
    }

    /** Task assigned by manager to employee */
    public void sendTaskAssignedNotification(String employeeEmail, String employeeName, String taskTitle,
            String assignedBy, String dueDate) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("New Task Assigned - " + taskTitle + " - LogSphere");
            message.setText("Dear " + employeeName + ",\n\n" +
                    "A new task has been assigned to you.\n\n" +
                    "Task: " + taskTitle + "\n" +
                    "Assigned by: " + assignedBy + "\n" +
                    (dueDate != null && !dueDate.isEmpty() ? "Due date: " + dueDate + "\n" : "") +
                    "\nPlease log in to view and complete the task.\n\nThank you,\nLogSphere System");
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send task assigned email: " + e.getMessage());
        }
    }

    /** Issue assigned to employee */
    public void sendIssueAssignedNotification(String employeeEmail, String employeeName, String issueKey,
            String issueTitle, String assignedBy) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("Issue Assigned - " + issueKey + " - LogSphere");
            message.setText("Dear " + employeeName + ",\n\n" +
                    "You have been assigned to an issue.\n\n" +
                    "Issue: " + issueKey + " - " + issueTitle + "\n" +
                    "Assigned by: " + assignedBy + "\n\n" +
                    "Please log in to view and work on the issue.\n\nThank you,\nLogSphere System");
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send issue assigned email: " + e.getMessage());
        }
    }

    /** Issue status changed */
    public void sendIssueStatusChangeNotification(String employeeEmail, String employeeName, String issueKey,
            String oldStatus, String newStatus, String changedBy) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("Issue Status Update - " + issueKey + " - LogSphere");
            message.setText("Dear " + employeeName + ",\n\n" +
                    "The status of an issue has been updated.\n\n" +
                    "Issue: " + issueKey + "\n" +
                    "Status: " + (oldStatus != null ? oldStatus : "-") + " → " + newStatus + "\n" +
                    "Changed by: " + changedBy + "\n\n" +
                    "Please log in to view the issue.\n\nThank you,\nLogSphere System");
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send issue status change email: " + e.getMessage());
        }
    }

    /** New comment on issue */
    public void sendIssueCommentNotification(String employeeEmail, String employeeName, String issueKey,
            String commentPreview, String commentBy) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("New Comment on " + issueKey + " - LogSphere");
            String preview = (commentPreview != null && !commentPreview.isEmpty())
                    ? commentPreview.substring(0, Math.min(100, commentPreview.length())) + (commentPreview.length() > 100 ? "..." : "")
                    : "";
            message.setText("Dear " + employeeName + ",\n\n" +
                    "A new comment was added to an issue you're involved in.\n\n" +
                    "Issue: " + issueKey + "\n" +
                    "Comment by: " + commentBy + "\n" +
                    (preview.isEmpty() ? "" : "Preview: " + preview + "\n") +
                    "\nPlease log in to view the full comment.\n\nThank you,\nLogSphere System");
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send issue comment email: " + e.getMessage());
        }
    }

    /** Overdue task reminder */
    public void sendOverdueTaskReminder(String employeeEmail, String employeeName, int overdueCount, String taskTitles) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("Overdue Task(s) Reminder - LogSphere");
            message.setText("Dear " + employeeName + ",\n\n" +
                    "You have " + overdueCount + " overdue task(s).\n\n" +
                    (taskTitles != null && !taskTitles.isEmpty() ? taskTitles + "\n\n" : "") +
                    "Please log in to complete these tasks.\n\nThank you,\nLogSphere System");
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send overdue task email: " + e.getMessage());
        }
    }

    /** Sprint ending soon reminder */
    public void sendSprintEndReminder(String employeeEmail, String employeeName, String sprintName, String projectName, int openIssueCount) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            message.setFrom("suchisuchithra184@gmail.com");
            message.setSubject("Sprint Ending Tomorrow - " + sprintName + " - LogSphere");
            message.setText("Dear " + employeeName + ",\n\n" +
                    "Sprint \"" + sprintName + "\" (" + projectName + ") ends tomorrow.\n\n" +
                    "You have " + openIssueCount + " open issue(s) in this sprint.\n\n" +
                    "Please complete or move them before the sprint ends.\n\nThank you,\nLogSphere System");
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send sprint end email: " + e.getMessage());
        }
    }

}
