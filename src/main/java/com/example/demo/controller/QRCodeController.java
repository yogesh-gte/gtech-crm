package com.example.demo.controller;

import com.example.demo.model.Employee;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.service.QRCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee/qrcode")
public class QRCodeController {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private QRCodeService qrCodeService;

    @RequestMapping(method = RequestMethod.GET)
    public String showQRCode(
            @RequestParam(name = "host", required = false) String requestedHost,
            @RequestParam(name = "port", required = false) Integer requestedPort,
            HttpSession session,
            Model model,
            HttpServletRequest request) {

        Employee sessionEmp = (Employee) session.getAttribute("employee");
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

        // Determine server host
        String serverHost;
        if (requestedHost != null && !requestedHost.trim().isEmpty()) {
            serverHost = requestedHost.trim();
        } else {
            String reqHost = request.getServerName();
            if ("localhost".equalsIgnoreCase(reqHost) || "127.0.0.1".equals(reqHost) || "0:0:0:0:0:0:0:1".equals(reqHost) || reqHost.contains(":")) {
                serverHost = detectedLanIp;
            } else {
                serverHost = reqHost;
            }
        }

        int serverPort = (requestedPort != null && requestedPort > 0) ? requestedPort : request.getServerPort();
        String scheme = request.getScheme();
        String contextPath = request.getContextPath();

        String portSuffix = ((scheme.equalsIgnoreCase("http") && serverPort == 80) || (scheme.equalsIgnoreCase("https") && serverPort == 443))
                ? "" : (":" + serverPort);

        String baseUrl = scheme + "://" + serverHost + portSuffix + contextPath;
        String qrData = qrCodeService.generateQRCodeData(baseUrl, employee.getEmployeeId(), employee.getQrCodeToken());
        String qrCodeImage = qrCodeService.generateQRCodeImage(qrData);

        model.addAttribute("employee", employee);
        model.addAttribute("qrCodeImage", qrCodeImage);
        model.addAttribute("qrData", qrData);
        model.addAttribute("activeHost", serverHost);
        model.addAttribute("activePort", serverPort);
        model.addAttribute("detectedLanIp", detectedLanIp);
        model.addAttribute("availableIps", availableIps);
        model.addAttribute("empId", employee.getEmployeeId());
        model.addAttribute("token", employee.getQrCodeToken());

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

