package com.example.demo.controller;

import com.example.demo.model.Employee;
import com.example.demo.model.IssueAttachment;
import com.example.demo.model.Task;
import com.example.demo.model.User;
import com.example.demo.repository.IssueAttachmentRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Secure file download and attachment serving controller.
 * Enforces:
 * 1. Strict authentication for all downloads
 * 2. Object-level access control (preventing IDOR/BOLA)
 * 3. Path traversal defense
 * 4. Browser security headers:
 *    - Content-Disposition: attachment; filename="safe.ext"
 *    - X-Content-Type-Options: nosniff
 *    - Content-Security-Policy: default-src 'none'; sandbox
 *    - Cache-Control: private, no-store
 */
@Controller
public class FileDownloadController {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private IssueAttachmentRepository issueAttachmentRepository;

    @Autowired
    private TaskRepository taskRepository;

    /**
     * Download issue attachment by ID or filename.
     */
    @GetMapping("/attachments/issues/{identifier}")
    public ResponseEntity<Resource> downloadIssueAttachment(
            @PathVariable String identifier,
            HttpSession session,
            HttpServletRequest request) throws IOException {

        if (!isAuthenticated(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        IssueAttachment attachment = resolveIssueAttachment(identifier);
        if (attachment == null) {
            return ResponseEntity.notFound().build();
        }

        // Authorization check: Any authenticated user (Admin, HR, Manager, Employee) can access internal issue attachments
        String storedFilename = extractFilenameFromPath(attachment.getFilePath());
        Path targetPath = fileStorageService.resolveStoredFile("issues", storedFilename);
        if (targetPath == null || !Files.exists(targetPath)) {
            return ResponseEntity.notFound().build();
        }

        String downloadFilename = attachment.getFileName() != null && !attachment.getFileName().isBlank()
                ? fileStorageService.sanitizeFilename(attachment.getFileName())
                : fileStorageService.sanitizeFilename(storedFilename);

        return serveFile(targetPath, downloadFilename);
    }

    /**
     * Download task attachment by ID or filename.
     * Enforces strict IDOR protection:
     * - Admins, HR, Managers can view all task attachments.
     * - Employees can ONLY download attachments for tasks assigned to them.
     */
    @GetMapping("/attachments/tasks/{identifier}")
    public ResponseEntity<Resource> downloadTaskAttachment(
            @PathVariable String identifier,
            HttpSession session,
            HttpServletRequest request) throws IOException {

        if (!isAuthenticated(session)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Task task = resolveTask(identifier);
        if (task == null || task.getAttachmentUrl() == null || task.getAttachmentUrl().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        // Object-level authorization check (IDOR Prevention)
        if (!canAccessTaskAttachment(task, session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String storedFilename = extractFilenameFromPath(task.getAttachmentUrl());
        Path targetPath = fileStorageService.resolveStoredFile("tasks", storedFilename);
        if (targetPath == null || !Files.exists(targetPath)) {
            return ResponseEntity.notFound().build();
        }

        String downloadFilename = fileStorageService.sanitizeFilename(storedFilename);

        return serveFile(targetPath, downloadFilename);
    }

    /**
     * Backward-compatible secure handler for legacy /uploads/issues/{filename} URLs.
     */
    @GetMapping("/uploads/issues/{filename:.+}")
    public ResponseEntity<Resource> downloadLegacyIssueUpload(
            @PathVariable String filename,
            HttpSession session,
            HttpServletRequest request) throws IOException {
        return downloadIssueAttachment(filename, session, request);
    }

    /**
     * Backward-compatible secure handler for legacy /uploads/tasks/{filename} URLs.
     */
    @GetMapping("/uploads/tasks/{filename:.+}")
    public ResponseEntity<Resource> downloadLegacyTaskUpload(
            @PathVariable String filename,
            HttpSession session,
            HttpServletRequest request) throws IOException {
        return downloadTaskAttachment(filename, session, request);
    }

    // =========================================================================
    // Helper & Security Evaluation Methods
    // =========================================================================

    private boolean isAuthenticated(HttpSession session) {
        if (session == null) return false;
        return session.getAttribute("user") != null || session.getAttribute("employee") != null;
    }

    private boolean canAccessTaskAttachment(Task task, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user != null) {
            String role = user.getRole();
            if ("ADMIN".equals(role) || "HR".equals(role) || "MANAGER".equals(role)) {
                return true;
            }
        }

        Object empObj = session.getAttribute("employee");
        if (empObj instanceof Employee sessionEmployee) {
            return task.getEmployee() != null && task.getEmployee().getId().equals(sessionEmployee.getId());
        }

        return false;
    }

    private IssueAttachment resolveIssueAttachment(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;

        // Try numeric ID first
        try {
            Long id = Long.parseLong(identifier);
            Optional<IssueAttachment> opt = issueAttachmentRepository.findById(id);
            if (opt.isPresent()) return opt.get();
        } catch (NumberFormatException ignored) {}

        // Fallback: match by stored filename / path
        String cleanName = extractFilenameFromPath(identifier);
        return issueAttachmentRepository.findFirstByFilePathEndingWith(cleanName).orElse(null);
    }

    private Task resolveTask(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;

        // Try numeric ID first
        try {
            Long id = Long.parseLong(identifier);
            Optional<Task> opt = taskRepository.findById(id);
            if (opt.isPresent()) return opt.get();
        } catch (NumberFormatException ignored) {}

        // Fallback: match by attachment filename
        String cleanName = extractFilenameFromPath(identifier);
        return taskRepository.findFirstByAttachmentUrlEndingWith(cleanName).orElse(null);
    }

    private String extractFilenameFromPath(String path) {
        if (path == null) return "";
        String clean = path.replace("\\", "/");
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0) {
            clean = clean.substring(lastSlash + 1);
        }
        int queryIdx = clean.indexOf('?');
        if (queryIdx >= 0) {
            clean = clean.substring(0, queryIdx);
        }
        return clean.trim();
    }

    private ResponseEntity<Resource> serveFile(Path filePath, String downloadFilename) throws IOException {
        String extension = fileStorageService.getFileExtension(downloadFilename);
        String safeContentType = fileStorageService.determineSafeContentType(extension);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(downloadFilename, StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(safeContentType));
        headers.setContentLength(Files.size(filePath));
        headers.setContentDisposition(disposition);

        // Security headers against MIME-sniffing, XSS, and unauthorized framing/caching
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; sandbox");
        headers.set("Cache-Control", "private, no-cache, no-store, must-revalidate");
        headers.set("Pragma", "no-cache");
        headers.set("Expires", "0");

        Resource resource = new FileSystemResource(filePath);
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }
}
