package com.example.demo.controller;

import com.example.demo.model.Attendance;
import com.example.demo.model.Employee;
import com.example.demo.model.User;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.QRCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/employee/qrcode")
public class QRCodeController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private QRCodeService qrCodeService;

    @Autowired(required = false)
    private EmailService emailService;

    public String showQRCode(
            String requestedHost,
            Integer requestedPort,
            String activeTab,
            HttpSession session,
            Model model,
            HttpServletRequest request) {
        return showQRCode(null, requestedHost, requestedPort, activeTab, session, model, request);
    }

    @RequestMapping(method = RequestMethod.GET)
    public String showQRCode(
            @RequestParam(name = "scheme", required = false) String requestedScheme,
            @RequestParam(name = "host", required = false) String requestedHost,
            @RequestParam(name = "port", required = false) Integer requestedPort,
            @RequestParam(name = "tab", required = false) String activeTab,
            HttpSession session,
            Model model,
            HttpServletRequest request) {

        Employee sessionEmp = (Employee) session.getAttribute("employee");
        if (sessionEmp == null) {
            User user = (User) session.getAttribute("user");
            if (user != null && user.getEmail() != null) {
                sessionEmp = employeeRepository.findByEmail(user.getEmail()).orElse(null);
            }
        }
        if (sessionEmp == null) {
            return "redirect:/employee/login";
        }
        Employee employee = employeeRepository.findById(sessionEmp.getId()).orElse(sessionEmp);

        // Generate or retrieve QR code token
        if (employee.getQrCodeToken() == null || employee.getQrCodeToken().isEmpty()) {
            String token = qrCodeService.generateQRCodeToken();
            employee.setQrCodeToken(token);
            employee = employeeRepository.save(employee);
            session.setAttribute("employee", employee);
        }

        // Detect all available IPv4 network addresses on the host machine
        List<String> availableIps = getAvailableIpv4Addresses();
        String detectedLanIp = availableIps.isEmpty() ? "127.0.0.1" : availableIps.get(0);

        // Determine protocol / scheme (defaults to https for mobile camera QR scanning compatibility)
        String scheme;
        if (requestedScheme != null && !requestedScheme.trim().isEmpty()) {
            scheme = requestedScheme.trim().toLowerCase();
        } else if ("https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")) || request.isSecure()) {
            scheme = "https";
        } else {
            scheme = "https";
        }

        // Determine server host
        String serverHost;
        if (requestedHost != null && !requestedHost.trim().isEmpty()) {
            serverHost = requestedHost.trim();
        } else {
            String fwdHost = request.getHeader("X-Forwarded-Host");
            if (fwdHost != null && !fwdHost.trim().isEmpty()) {
                serverHost = fwdHost.contains(":") ? fwdHost.split(":")[0] : fwdHost;
            } else {
                String reqHost = request.getServerName();
                if ("localhost".equalsIgnoreCase(reqHost) || "127.0.0.1".equals(reqHost) || "0:0:0:0:0:0:0:1".equals(reqHost) || reqHost.contains(":")) {
                    serverHost = detectedLanIp;
                } else {
                    serverHost = reqHost;
                }
            }
        }

        // Determine port: for https with default/443, omit port from URL!
        Integer serverPort = requestedPort;
        if (serverPort == null) {
            String fwdPort = request.getHeader("X-Forwarded-Port");
            if (fwdPort != null) {
                try { serverPort = Integer.parseInt(fwdPort); } catch (Exception ignored) {}
            }
        }
        if (serverPort == null) {
            if ("https".equalsIgnoreCase(scheme)) {
                serverPort = 443;
            } else {
                serverPort = request.getServerPort() > 0 ? request.getServerPort() : 8083;
            }
        }

        String portSuffix = ((scheme.equalsIgnoreCase("http") && serverPort == 80) || (scheme.equalsIgnoreCase("https") && serverPort == 443))
                ? "" : (":" + serverPort);

        String contextPath = request.getContextPath() != null ? request.getContextPath() : "";
        String baseUrl = scheme + "://" + serverHost + portSuffix + contextPath;
        String qrData = qrCodeService.generateQRCodeData(baseUrl, employee.getEmployeeId(), employee.getQrCodeToken());
        String qrCodeImage = "";
        try {
            qrCodeImage = qrCodeService.generateQRCodeImage(qrData);
        } catch (Exception e) {
            qrCodeImage = "";
        }

        // Live attendance status for logged-in employee
        LocalDate today = LocalDate.now();
        List<Attendance> todayAttendances = attendanceRepository.findByEmployeeAndAttendanceDate(employee, today);
        Attendance todayAttendance = todayAttendances.isEmpty() ? null : todayAttendances.get(0);
        boolean isClockedIn = "IN".equalsIgnoreCase(employee.getStatus());

        model.addAttribute("employee", employee);
        model.addAttribute("emp", employee); // for fragments/employee-sidebar.html
        model.addAttribute("isClockedIn", isClockedIn);
        model.addAttribute("todayAttendance", todayAttendance);
        model.addAttribute("currentStatus", employee.getStatus() != null ? employee.getStatus() : "OUT");
        model.addAttribute("qrCodeImage", qrCodeImage);
        model.addAttribute("qrData", qrData);
        model.addAttribute("activeScheme", scheme);
        model.addAttribute("activeHost", serverHost);
        model.addAttribute("activePort", (serverPort == 443 || serverPort == 80) ? "" : serverPort.toString());
        model.addAttribute("detectedLanIp", detectedLanIp);
        model.addAttribute("availableIps", availableIps);
        model.addAttribute("empId", employee.getEmployeeId());
        model.addAttribute("token", employee.getQrCodeToken());
        model.addAttribute("activeTab", (activeTab != null && !activeTab.isBlank()) ? activeTab : "scanner");

        return "employee-qrcode";
    }

    public static List<String> getAvailableIpv4Addresses() {
        LinkedHashSet<String> ips = new LinkedHashSet<>();

        // Priority 1: outbound route lookup via UDP dummy connect (does not transmit packets)
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            InetAddress addr = socket.getLocalAddress();
            if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isAnyLocalAddress()) {
                ips.add(addr.getHostAddress());
            }
        } catch (Exception ignored) {
        }

        // Priority 2: iterate all network interfaces
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isPointToPoint()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isAnyLocalAddress()) {
                        String host = addr.getHostAddress();
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            ips.add(host);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Priority 3: any remaining non-loopback IPv4
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isAnyLocalAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (ips.isEmpty()) {
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                if (localHost instanceof Inet4Address && !localHost.isLoopbackAddress()) {
                    ips.add(localHost.getHostAddress());
                }
            } catch (Exception ignored) {
            }
        }

        return new ArrayList<>(ips);
    }

    @PostMapping("/decode-image")
    @ResponseBody
    public ResponseEntity<?> decodeImageFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "OFFICE") String workMode,
            @RequestParam(required = false) String action) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"No image file provided\"}");
        }
        try {
            BufferedImage bufferedImage = ImageIO.read(file.getInputStream());
            if (bufferedImage == null) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Unsupported or corrupted image file\"}");
            }
            LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));

            Result result;
            try {
                result = new MultiFormatReader().decode(bitmap, hints);
            } catch (NotFoundException nfe) {
                BinaryBitmap altBitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
                result = new MultiFormatReader().decode(altBitmap, hints);
            }

            String qrData = result.getText();
            return scanQRCode(qrData, workMode, action);
        } catch (NotFoundException nfe) {
            return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"No QR code found in the uploaded image. Please ensure the QR code is clearly visible.\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Failed to decode QR image: " + e.getMessage() + "\"}");
        }
    }

    @RequestMapping(value = "/scan", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> scanQRCode(
            @RequestParam String qrData, 
            @RequestParam(required = false, defaultValue = "OFFICE") String workMode,
            @RequestParam(required = false) String action) {
        try {
            if (qrData == null || qrData.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"QR data cannot be empty\"}");
            }
            qrData = qrData.trim();

            // Support both old format (LOGSPHERE:ID:TOKEN) and URL format
            String employeeId = null;
            String token = null;

            if (qrData.startsWith("LOGSPHERE:")) {
                String[] parts = qrData.split(":");
                if (parts.length >= 3) {
                    employeeId = parts[1].trim();
                    token = parts[2].trim();
                }
            } else if (qrData.contains("/attendance/mobile") || qrData.contains("empId=")) {
                try {
                    String queryString = qrData.contains("?") ? qrData.substring(qrData.indexOf("?") + 1) : qrData;
                    Map<String, String> params = new HashMap<>();
                    for (String param : queryString.split("&")) {
                        String[] pair = param.split("=");
                        if (pair.length > 1) {
                            params.put(pair[0].trim(), URLDecoder.decode(pair[1].trim(), java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                    employeeId = params.get("empId");
                    token = params.get("token");
                    if (employeeId != null) employeeId = employeeId.trim();
                    if (token != null) token = token.trim();
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Invalid QR URL format\"}");
                }
            } else if (qrData.contains(":")) {
                String[] parts = qrData.split(":");
                if (parts.length == 2) {
                    employeeId = parts[0].trim();
                    token = parts[1].trim();
                }
            }

            if (employeeId == null || token == null) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Invalid QR code format - missing employeeId or token\"}");
            }

            Optional<Employee> employeeOpt = employeeRepository.findByEmployeeId(employeeId);
            if (employeeOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Employee not found\"}");
            }

            Employee employee = employeeOpt.get();
            if (!token.equals(employee.getQrCodeToken())) {
                return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Invalid QR code token\"}");
            }

            LocalDateTime now = LocalDateTime.now();
            String formattedTime = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String mode = (workMode != null && !workMode.trim().isEmpty()) ? workMode.trim().toUpperCase() : "OFFICE";

            // If action is "auto": toggle based on current status
            if ("auto".equalsIgnoreCase(action)) {
                action = "IN".equalsIgnoreCase(employee.getStatus()) ? "clockout" : "clockin";
            }

            // If action is specified: clockin or clockout
            if ("clockin".equalsIgnoreCase(action)) {
                if ("IN".equals(employee.getStatus())) {
                    return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Employee is already clocked in!\"}");
                }
                employee.setWorkMode(mode);
                employee.setStatus("IN");
                employee.setCurrentCheckIn(now);
                employee = employeeRepository.save(employee);

                LocalDate today = LocalDate.now();
                List<Attendance> attendanceOpt = attendanceRepository.findByEmployeeAndAttendanceDate(employee, today);
                Attendance attendance = attendanceOpt.isEmpty() ? new Attendance(employee, today) : attendanceOpt.get(0);
                attendance.setCheckInTime(now);
                attendance.setStatus("PRESENT");
                attendance.setWorkMode(mode);
                attendanceRepository.save(attendance);

                // Late arrival alert check
                if (emailService != null && employee.getScheduledStartTime() != null && !employee.getScheduledStartTime().isEmpty()) {
                    try {
                        LocalTime scheduledTime = LocalTime.parse(employee.getScheduledStartTime());
                        LocalTime actualTime = now.toLocalTime();
                        if (actualTime.isAfter(scheduledTime.plusMinutes(5)) && !Boolean.TRUE.equals(employee.getLateAlertSent())) {
                            emailService.sendLateArrivalAlert(
                                    employee.getEmail(),
                                    employee.getName(),
                                    employee.getScheduledStartTime(),
                                    actualTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                            employee.setLateAlertSent(true);
                            employeeRepository.save(employee);
                        }
                    } catch (Exception ignored) {}
                }

                return ResponseEntity.ok().body("{\"success\": true, \"action\": \"clockin\", \"employeeId\": \"" + employeeId + 
                        "\", \"employeeName\": \"" + employee.getName() + "\", \"department\": \"" + (employee.getDepartment() != null ? employee.getDepartment() : "General") + 
                        "\", \"workMode\": \"" + mode + "\", \"status\": \"IN\", \"isClockedIn\": true, \"time\": \"" + formattedTime + 
                        "\", \"message\": \"Clocked In successfully!\"}");
            } else if ("clockout".equalsIgnoreCase(action)) {
                if ("OUT".equals(employee.getStatus())) {
                    return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Employee is not clocked in!\"}");
                }
                LocalDateTime checkInTime = employee.getCurrentCheckIn();
                if (checkInTime != null) {
                    long minutes = java.time.temporal.ChronoUnit.MINUTES.between(checkInTime, now);
                    double hours = minutes / 60.0;
                    employee.setTotalHoursToday((employee.getTotalHoursToday() != null ? employee.getTotalHoursToday() : 0.0) + hours);
                }
                employee.setStatus("OUT");
                employee.setCurrentCheckOut(now);
                employeeRepository.save(employee);

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

                return ResponseEntity.ok().body("{\"success\": true, \"action\": \"clockout\", \"employeeId\": \"" + employeeId + 
                        "\", \"employeeName\": \"" + employee.getName() + "\", \"department\": \"" + (employee.getDepartment() != null ? employee.getDepartment() : "General") + 
                        "\", \"workMode\": \"" + mode + "\", \"status\": \"OUT\", \"isClockedIn\": false, \"time\": \"" + formattedTime + 
                        "\", \"message\": \"Clocked Out successfully!\"}");
            }

            // Default: verify / read employee info
            boolean isClockedIn = "IN".equalsIgnoreCase(employee.getStatus());
            return ResponseEntity.ok().body("{\"success\": true, \"employeeId\": \"" + employeeId + 
                    "\", \"employeeName\": \"" + employee.getName() + 
                    "\", \"department\": \"" + (employee.getDepartment() != null ? employee.getDepartment() : "General") + 
                    "\", \"status\": \"" + (employee.getStatus() != null ? employee.getStatus() : "OUT") + 
                    "\", \"isClockedIn\": " + isClockedIn + 
                    ", \"token\": \"" + token + 
                    "\", \"workMode\": \"" + mode + "\"}");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"success\": false, \"message\": \"Error processing QR code: " + 
                    e.getMessage() + "\"}");
        }
    }
}
