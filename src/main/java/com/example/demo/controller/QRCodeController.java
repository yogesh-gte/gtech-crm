package com.example.demo.controller;

import com.example.demo.model.Employee;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.service.QRCodeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
@RequestMapping("/employee/qrcode")
public class QRCodeController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private QRCodeService qrCodeService;

    @RequestMapping(method = RequestMethod.GET)
    public String showQRCode(HttpSession session, Model model, jakarta.servlet.http.HttpServletRequest request) {
        Employee employee = (Employee) session.getAttribute("employee");
        if (employee == null) {
            return "redirect:/employee/login";
        }

        // Generate or retrieve QR code token
        if (employee.getQrCodeToken() == null || employee.getQrCodeToken().isEmpty()) {
            String token = qrCodeService.generateQRCodeToken();
            employee.setQrCodeToken(token);
            employee = employeeRepository.save(employee);
            session.setAttribute("employee", employee);
        }

        // Generate QR code data (fully qualified URL)
        String serverHost = request.getServerName();
        if ("localhost".equalsIgnoreCase(serverHost) || "127.0.0.1".equals(serverHost) || "0:0:0:0:0:0:0:1".equals(serverHost)) {
            try {
                java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
                String hostAddr = localHost.getHostAddress();
                if (hostAddr != null && !hostAddr.startsWith("127.")) {
                    serverHost = hostAddr;
                }
            } catch (Exception ignored) {
            }
        }
        String baseUrl = request.getScheme() + "://" + serverHost + ":" + request.getServerPort() + request.getContextPath();
        String qrData = qrCodeService.generateQRCodeData(baseUrl, employee.getEmployeeId(), employee.getQrCodeToken());
        String qrCodeImage = qrCodeService.generateQRCodeImage(qrData);

        model.addAttribute("employee", employee);
        model.addAttribute("qrCodeImage", qrCodeImage);
        model.addAttribute("qrData", qrData);

        return "employee-qrcode";
    }

    @RequestMapping(value = "/scan", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> scanQRCode(@RequestParam String qrData, @RequestParam String workMode) {
        try {
            // Support both old format (LOGSPHERE:ID:TOKEN) and new URL format
            String employeeId = null;
            String token = null;

            if (qrData.startsWith("LOGSPHERE:")) {
                String[] parts = qrData.split(":");
                if (parts.length == 3) {
                    employeeId = parts[1];
                    token = parts[2];
                }
            } else if (qrData.contains("/attendance/mobile")) {
                // Parse from URL params
                try {
                    java.net.URI uri = new java.net.URI(qrData);
                    String query = uri.getQuery();
                    if (query == null || query.isEmpty()) {
                        return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Invalid QR URL format - missing parameters\"}");
                    }
                    java.util.Map<String, String> params = new java.util.HashMap<>();
                    for (String param : query.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length > 1) {
                            params.put(pair[0], pair[1]);
                        }
                    }
                    employeeId = params.get("empId");
                    token = params.get("token");
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Invalid QR URL format\"}");
                }
            }

            if (employeeId == null || token == null) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Invalid QR code format\"}");
            }

            Optional<Employee> employeeOpt = employeeRepository.findByEmployeeId(employeeId);
            if (employeeOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Employee not found\"}");
            }

            Employee employee = employeeOpt.get();
            if (!token.equals(employee.getQrCodeToken())) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Invalid QR code token\"}");
            }

            // Return success with employee info
            return ResponseEntity.ok().body("{\"success\": true, \"employeeId\": \"" + employeeId + 
                    "\", \"employeeName\": \"" + employee.getName() + "\", \"workMode\": \"" + workMode + "\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Error processing QR code: " + 
                    e.getMessage() + "\"}");
        }
    }
}

