package com.example.demo.controller;

import com.example.demo.model.Employee;
import com.example.demo.model.Attendance;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.AttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Optional;

@Controller
@RequestMapping("/attendance/mobile")
public class MobileAttendanceController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @GetMapping
    public String mobileAttendance(@RequestParam String empId, @RequestParam String token, Model model) {
        Optional<Employee> employeeOpt = employeeRepository.findByEmployeeId(empId);
        
        if (employeeOpt.isEmpty() || !token.equals(employeeOpt.get().getQrCodeToken())) {
            model.addAttribute("error", "Invalid or expired QR code. Please scan a fresh QR code from your dashboard.");
            return "mobile-attendance";
        }

        Employee employee = employeeOpt.get();
        model.addAttribute("employee", employee);
        model.addAttribute("token", token);

        // Check current attendance status
        Optional<Attendance> attendanceOpt = attendanceRepository.findTopByEmployeeAndAttendanceDateOrderByCheckInTimeDesc(employee, LocalDate.now());
        if (attendanceOpt.isPresent()) {
            Attendance att = attendanceOpt.get();
            model.addAttribute("isClockedIn", att.getCheckOutTime() == null);
        } else {
            model.addAttribute("isClockedIn", false);
        }

        return "mobile-attendance";
    }

    @PostMapping("/action")
    public String processAttendance(@RequestParam String empId, @RequestParam String token, @RequestParam String action, @RequestParam String workMode, Model model) {
        Optional<Employee> employeeOpt = employeeRepository.findByEmployeeId(empId);
        
        if (employeeOpt.isEmpty() || !token.equals(employeeOpt.get().getQrCodeToken())) {
            model.addAttribute("error", "Invalid or expired QR code.");
            return "mobile-attendance";
        }

        Employee employee = employeeOpt.get();
        LocalDate today = LocalDate.now();
        
        if ("clock-in".equals(action)) {
            Attendance attendance = new Attendance();
            attendance.setEmployee(employee);
            attendance.setAttendanceDate(today);
            attendance.setCheckInTime(LocalDateTime.now());
            attendance.setWorkMode(workMode);
            attendance.setStatus("PRESENT");
            attendanceRepository.save(attendance);
            model.addAttribute("success", "Clocked In successfully!");
        } else if ("clock-out".equals(action)) {
            Optional<Attendance> attendanceOpt = attendanceRepository.findTopByEmployeeAndAttendanceDateOrderByCheckInTimeDesc(employee, today);
            if (attendanceOpt.isPresent() && attendanceOpt.get().getCheckOutTime() == null) {
                Attendance attendance = attendanceOpt.get();
                attendance.setCheckOutTime(LocalDateTime.now());
                attendanceRepository.save(attendance);
                model.addAttribute("success", "Clocked Out successfully!");
            } else {
                model.addAttribute("error", "No active Clock-In session found.");
            }
        }

        return "redirect:/attendance/mobile/success?msg=" + (model.getAttribute("success") != null ? model.getAttribute("success") : model.getAttribute("error"));
    }

    @GetMapping("/success")
    public String success(@RequestParam String msg, Model model) {
        model.addAttribute("message", msg);
        return "mobile-attendance-result";
    }
}
