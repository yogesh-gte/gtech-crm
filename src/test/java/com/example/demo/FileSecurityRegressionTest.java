package com.example.demo;

import com.example.demo.model.Employee;
import com.example.demo.model.IssueAttachment;
import com.example.demo.model.Task;
import com.example.demo.model.User;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.IssueAttachmentRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.service.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class FileSecurityRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private IssueAttachmentRepository issueAttachmentRepository;

    private Employee employee1;
    private Employee employee2;
    private Task task1;
    private Task task2;
    private final List<Path> createdFiles = new ArrayList<>();

    // Standard magic byte signatures for synthetic fixtures
    private static final byte[] PNG_SIGNATURE = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG_SIGNATURE = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    private static final byte[] PDF_SIGNATURE = "%PDF-1.7 standard safe document content".getBytes();
    private static final byte[] ZIP_SIGNATURE = new byte[]{0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0};

    @BeforeEach
    public void setup() {
        cleanup();

        employee1 = new Employee();
        employee1.setName("File Owner");
        employee1.setEmail("file.owner@example.com");
        employee1.setEmployeeId("FILE_EMP_01");
        employee1.setDepartment("Engineering");
        employee1.setStatus("ACTIVE");
        employee1 = employeeRepository.save(employee1);

        employee2 = new Employee();
        employee2.setName("File Other");
        employee2.setEmail("file.other@example.com");
        employee2.setEmployeeId("FILE_EMP_02");
        employee2.setDepartment("Operations");
        employee2.setStatus("ACTIVE");
        employee2 = employeeRepository.save(employee2);
    }

    @AfterEach
    public void tearDown() {
        for (Path p : createdFiles) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ignored) {}
        }
        cleanup();
    }

    private void cleanup() {
        taskRepository.deleteAll();
        issueAttachmentRepository.deleteAll();
        employeeRepository.findByEmail("file.owner@example.com").ifPresent(employeeRepository::delete);
        employeeRepository.findByEmail("file.other@example.com").ifPresent(employeeRepository::delete);
    }

    // =========================================================================
    // 1. FILE UPLOAD INTEGRITY AND VALIDATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Valid upload with genuine magic bytes and allowed extension succeeds and generates UUID name")
    public void testValidUploadSucceedsWithSecureUuidName() throws IOException {
        MockMultipartFile validPng = new MockMultipartFile(
                "file", "screenshot.png", "image/png", PNG_SIGNATURE
        );

        FileStorageService.StoredFileInfo info = fileStorageService.storeFile(
                validPng, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE
        );

        assertNotNull(info);
        createdFiles.add(info.fullPath());
        assertTrue(Files.exists(info.fullPath()), "Uploaded file must be stored securely");
        assertNotEquals("screenshot.png", info.storedFilename(), "Stored filename must be randomized to prevent collisions");
        assertTrue(info.storedFilename().endsWith(".png"), "Stored filename must retain safe extension");
    }

    @Test
    @DisplayName("Extension and MIME spoofing: Executable file disguised as PNG is rejected")
    public void testMimeAndContentMismatchRejected() {
        // MZ header (Windows executable / DLL) disguised as PNG
        byte[] fakePngContent = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile spoofedFile = new MockMultipartFile(
                "file", "exploit.png", "image/png", fakePngContent
        );

        assertThrows(SecurityException.class, () ->
                fileStorageService.storeFile(
                        spoofedFile, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE
                )
        );
    }

    @Test
    @DisplayName("Dangerous extensions (exe, sh, jsp, php, html, svg) are strictly blocked")
    public void testDangerousExtensionsRejected() {
        String[] dangerous = {"shell.jsp", "malware.exe", "script.sh", "info.php", "xss.svg", "page.html"};
        for (String filename : dangerous) {
            MockMultipartFile file = new MockMultipartFile("file", filename, "application/octet-stream", "harmless text".getBytes());
            assertThrows(SecurityException.class, () ->
                    fileStorageService.storeFile(
                            file, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE
                    ), "File " + filename + " must be rejected"
            );
        }
    }

    @Test
    @DisplayName("Oversized uploads exceeding category limit are rejected")
    public void testOversizedUploadRejected() {
        long limit = 1024; // 1 KB limit
        byte[] oversizedData = new byte[2048]; // 2 KB data
        MockMultipartFile oversizedFile = new MockMultipartFile("file", "large.txt", "text/plain", oversizedData);

        assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.storeFile(
                        oversizedFile, "issues", Set.of("txt"), limit
                )
        );
    }

    @Test
    @DisplayName("Empty or null files are rejected")
    public void testEmptyOrNullFileRejected() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.storeFile(
                        emptyFile, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE
                )
        );

        assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.storeFile(
                        null, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE
                )
        );
    }

    @Test
    @DisplayName("Path traversal sequences, null bytes, and Windows reserved names are neutralized")
    public void testFilenameSanitization() {
        // Path traversal
        String sanitized1 = fileStorageService.sanitizeFilename("../../etc/passwd");
        assertFalse(sanitized1.contains("/"));
        assertFalse(sanitized1.contains(".."));

        // Windows backslash path traversal
        String sanitized2 = fileStorageService.sanitizeFilename("..\\..\\windows\\system32\\cmd.exe");
        assertFalse(sanitized2.contains("\\"));
        assertFalse(sanitized2.contains(".."));

        // Null bytes
        String sanitized3 = fileStorageService.sanitizeFilename("document\0.pdf");
        assertFalse(sanitized3.contains("\0"));

        // Windows reserved device names
        String sanitized4 = fileStorageService.sanitizeFilename("CON.txt");
        assertNotEquals("CON.txt", sanitized4);
        assertTrue(sanitized4.startsWith("safe_") || !sanitized4.equalsIgnoreCase("CON.txt"));
    }

    @Test
    @DisplayName("Valid ZIP-based office documents and archives succeed with signature verification")
    public void testValidZipUploadSucceeds() throws IOException {
        MockMultipartFile validZip = new MockMultipartFile(
                "file", "project_docs.zip", "application/zip", ZIP_SIGNATURE
        );

        FileStorageService.StoredFileInfo info = fileStorageService.storeFile(
                validZip, "tasks", FileStorageService.ALLOWED_TASK_EXTENSIONS, FileStorageService.MAX_TASK_FILE_SIZE
        );

        assertNotNull(info);
        createdFiles.add(info.fullPath());
        assertTrue(Files.exists(info.fullPath()));
    }

    // =========================================================================
    // 2. FILE DOWNLOAD SECURITY AND IDOR DEFENSE TESTS
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated request to file download endpoints redirects browser and returns 401 for API")
    public void testUnauthenticatedDownloadReturnsUnauthorized() throws Exception {
        // Browser request redirects to login
        mockMvc.perform(get("/attachments/issues/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        // API request returns 401 Unauthorized
        mockMvc.perform(get("/attachments/issues/1").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("IDOR Defense: Employee 2 is forbidden from downloading Employee 1's assigned task attachment")
    public void testTaskAttachmentIdorForbiddenForCrossEmployee() throws Exception {
        // Create an uploaded file on disk
        MockMultipartFile validPdf = new MockMultipartFile("file", "confidential.pdf", "application/pdf", PDF_SIGNATURE);
        FileStorageService.StoredFileInfo stored = fileStorageService.storeFile(
                validPdf, "tasks", FileStorageService.ALLOWED_TASK_EXTENSIONS, FileStorageService.MAX_TASK_FILE_SIZE
        );
        createdFiles.add(stored.fullPath());

        // Assign task to Employee 1
        Task task = new Task();
        task.setTitle("Confidential Strategy Task");
        task.setDescription("Confidential Task Details");
        task.setEmployee(employee1);
        task.setAttachmentUrl("/uploads/tasks/" + stored.storedFilename());
        task = taskRepository.save(task);

        // Employee 2 tries to download Employee 1's task attachment
        MockHttpSession emp2Session = new MockHttpSession();
        emp2Session.setAttribute("employee", employee2);
        emp2Session.setAttribute("employeeId", employee2.getEmployeeId());

        mockMvc.perform(get("/attachments/tasks/" + task.getId()).session(emp2Session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authorized Access: Owner Employee and Admin can download task attachment with security headers")
    public void testAuthorizedTaskAttachmentDownloadWithSecurityHeaders() throws Exception {
        MockMultipartFile validPdf = new MockMultipartFile("file", "report.pdf", "application/pdf", PDF_SIGNATURE);
        FileStorageService.StoredFileInfo stored = fileStorageService.storeFile(
                validPdf, "tasks", FileStorageService.ALLOWED_TASK_EXTENSIONS, FileStorageService.MAX_TASK_FILE_SIZE
        );
        createdFiles.add(stored.fullPath());

        Task task = new Task();
        task.setTitle("Project Report Task");
        task.setDescription("Project Report Description");
        task.setEmployee(employee1);
        task.setAttachmentUrl("/uploads/tasks/" + stored.storedFilename());
        task = taskRepository.save(task);

        // Employee 1 (owner) accesses own task attachment
        MockHttpSession emp1Session = new MockHttpSession();
        emp1Session.setAttribute("employee", employee1);
        emp1Session.setAttribute("employeeId", employee1.getEmployeeId());

        mockMvc.perform(get("/attachments/tasks/" + task.getId()).session(emp1Session))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Content-Disposition"));

        // Admin accesses task attachment
        MockHttpSession adminSession = new MockHttpSession();
        adminSession.setAttribute("user", new User("admin@example.com", "pass", "Admin", "ADMIN"));
        adminSession.setAttribute("userRole", "ADMIN");

        mockMvc.perform(get("/attachments/tasks/" + task.getId()).session(adminSession))
                .andExpect(status().isOk());
    }
}
