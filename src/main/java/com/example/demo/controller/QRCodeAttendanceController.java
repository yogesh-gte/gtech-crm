package com.example.demo.controller;

import com.example.demo.model.Attendance;
import com.example.demo.model.Employee;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/attendance/qr")
public class QRCodeAttendanceController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private EmailService emailService;

    @RequestMapping(value = "/clockin", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> clockInViaQR(@RequestParam String employeeId,
            @RequestParam String token,
            @RequestParam String workMode) {
        try {
            Optional<Employee> employeeOpt = employeeRepository.findByEmployeeId(employeeId);
            if (employeeOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Employee not found\"}");
            }

            Employee employee = employeeOpt.get();
            if (!token.equals(employee.getQrCodeToken())) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Invalid QR code token\"}");
            }

            if ("IN".equals(employee.getStatus())) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Already clocked in\"}");
            }

            // Set work mode
            if (workMode != null && !workMode.isEmpty()) {
                employee.setWorkMode(workMode);
            } else {
                employee.setWorkMode("OFFICE");
            }

            LocalDateTime now = LocalDateTime.now();
            employee.setStatus("IN");
            employee.setCurrentCheckIn(now);
            employee = employeeRepository.save(employee);

            // Create attendance record
            LocalDate today = LocalDate.now();
            List<Attendance> attendanceOpt = attendanceRepository.findByEmployeeAndAttendanceDate(employee, today);
            Attendance attendance;

            if (!attendanceOpt.isEmpty()) {
                attendance = attendanceOpt.get(0);
            } else {
                attendance = new Attendance(employee, today);
            }

            attendance.setCheckInTime(now);
            attendance.setStatus("PRESENT");
            attendance.setWorkMode(employee.getWorkMode());
            attendanceRepository.save(attendance);

            // Check for late arrival
            if (employee.getScheduledStartTime() != null && !employee.getScheduledStartTime().isEmpty()) {
                LocalTime scheduledTime = LocalTime.parse(employee.getScheduledStartTime());
                LocalTime actualTime = now.toLocalTime();

                if (actualTime.isAfter(scheduledTime.plusMinutes(5)) &&
                        !Boolean.TRUE.equals(employee.getLateAlertSent())) {
                    emailService.sendLateArrivalAlert(
                            employee.getEmail(),
                            employee.getName(),
                            employee.getScheduledStartTime(),
                            actualTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                    employee.setLateAlertSent(true);
                    employeeRepository.save(employee);
                }
            }

            return ResponseEntity.ok().body("{\"success\": true, \"message\": \"Clocked in successfully\", " +
                    "\"workMode\": \"" + employee.getWorkMode() + "\", \"time\": \"" +
                    now.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    @RequestMapping(value = "/clockout", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> clockOutViaQR(@RequestParam String employeeId,
            @RequestParam String token) {
        try {
            Optional<Employee> employeeOpt = employeeRepository.findByEmployeeId(employeeId);
            if (employeeOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Employee not found\"}");
            }

            Employee employee = employeeOpt.get();
            if (!token.equals(employee.getQrCodeToken())) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Invalid QR code token\"}");
            }

            if ("OUT".equals(employee.getStatus())) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Not clocked in\"}");
            }

            LocalDateTime now = LocalDateTime.now();
            LocalTime currentTime = now.toLocalTime();
            LocalDateTime checkInTime = employee.getCurrentCheckIn();

            // Check for early departure (email sending removed - only tracking for records)
            if (employee.getScheduledEndTime() != null && !employee.getScheduledEndTime().isEmpty()) {
                LocalTime scheduledEndTime = LocalTime.parse(employee.getScheduledEndTime());
                if (currentTime.isBefore(scheduledEndTime.minusMinutes(5)) &&
                        !Boolean.TRUE.equals(employee.getEarlyAlertSent())) {
                    employee.setEarlyAlertSent(true);
                }
            }

            if (checkInTime != null) {
                long minutes = java.time.temporal.ChronoUnit.MINUTES.between(checkInTime, now);
                double hours = minutes / 60.0;
                employee.setTotalHoursToday(employee.getTotalHoursToday() + hours);
            }

            employee.setStatus("OUT");
            employee.setCurrentCheckOut(now);
            employeeRepository.save(employee);

            // Update attendance record
            LocalDate today = LocalDate.now();
            List<Attendance> attendanceOpt = attendanceRepository.findByEmployeeAndAttendanceDate(employee, today);

            if (!attendanceOpt.isEmpty()) {
                Attendance attendance = attendanceOpt.get(0);
                attendance.setCheckOutTime(now);

                if (attendance.getCheckInTime() != null) {
                    long minutes = java.time.temporal.ChronoUnit.MINUTES.between(attendance.getCheckInTime(), now);
                    double hours = minutes / 60.0;
                    attendance.setTotalHours(hours);
                }

                attendanceRepository.save(attendance);
            }

            return ResponseEntity.ok().body("{\"success\": true, \"message\": \"Clocked out successfully\", " +
                    "\"time\": \"" + now.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"" + e.getMessage() + "\"}");
        }
    }
}
