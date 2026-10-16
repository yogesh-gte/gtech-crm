package com.example.demo;

import com.example.demo.controller.FileDownloadController;
import com.example.demo.model.Employee;
import com.example.demo.model.IssueAttachment;
import com.example.demo.model.Task;
import com.example.demo.model.User;
import com.example.demo.repository.IssueAttachmentRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.service.FaceRecognitionService;
import com.example.demo.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Comprehensive automated security test suite for Phase 6:
 * 1. Upload validation (allowlists, magic bytes, size limits, failure cleanup)
 * 2. Path traversal defense and server-controlled storage filenames
 * 3. Object-level access control / IDOR prevention on downloads
 * 4. Safe download security headers (attachment, nosniff, CSP)
 * 5. Face image validation and memory exhaustion protection
 */
class FileUploadSecurityTest {

    private FileStorageService fileStorageService;

    @Mock
    private IssueAttachmentRepository issueAttachmentRepository;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private FileDownloadController fileDownloadController;

    private FaceRecognitionService faceRecognitionService;

    @TempDir
    Path tempUploadDir;

    // Standard valid magic headers
    private static final byte[] VALID_PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    private static final byte[] VALID_JPEG_BYTES = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };

    private static final byte[] VALID_PDF_BYTES = "%PDF-1.5\n%Valid PDF file content for test\n%%EOF".getBytes();

    private static final byte[] VALID_ZIP_BYTES = new byte[] {
            0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00,
            0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    private static final byte[] MALICIOUS_EXE_BYTES = new byte[] {
            0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00,
            0x04, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00
    };

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fileStorageService = new FileStorageService();
        ReflectionTestUtils.setField(fileStorageService, "uploadDir", tempUploadDir.toString());

        ReflectionTestUtils.setField(fileDownloadController, "fileStorageService", fileStorageService);

        faceRecognitionService = new FaceRecognitionService();
    }

    // =========================================================================
    // 1. Upload Security & Validation Tests
    // =========================================================================
    @Nested
    @DisplayName("1. Upload Security and Validation Tests")
    class UploadValidationTests {

        @Test
        @DisplayName("Valid PNG upload with matching magic bytes succeeds")
        void testValidPngUploadSucceeds() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "screenshot.png", "image/png", VALID_PNG_BYTES);

            FileStorageService.StoredFileInfo info = fileStorageService.storeFile(
                    file, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE);

            assertNotNull(info);
            assertEquals("screenshot.png", info.originalFilename());
            assertTrue(info.storedFilename().endsWith(".png"));
            assertTrue(Files.exists(info.fullPath()));
            assertEquals("image/png", info.contentType());
        }

        @Test
        @DisplayName("Valid PDF upload with matching magic bytes succeeds")
        void testValidPdfUploadSucceeds() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "report.pdf", "application/pdf", VALID_PDF_BYTES);

            FileStorageService.StoredFileInfo info = fileStorageService.storeFile(
                    file, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE);

            assertNotNull(info);
            assertEquals("report.pdf", info.originalFilename());
            assertTrue(info.storedFilename().endsWith(".pdf"));
            assertTrue(Files.exists(info.fullPath()));
        }

        @Test
        @DisplayName("Valid ZIP upload with matching PK magic bytes succeeds for tasks")
        void testValidZipUploadSucceeds() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "attachment", "project_files.zip", "application/zip", VALID_ZIP_BYTES);

            FileStorageService.StoredFileInfo info = fileStorageService.storeFile(
                    file, "tasks", FileStorageService.ALLOWED_TASK_EXTENSIONS, FileStorageService.MAX_TASK_FILE_SIZE);

            assertNotNull(info);
            assertEquals("project_files.zip", info.originalFilename());
            assertTrue(info.storedFilename().endsWith(".zip"));
            assertTrue(Files.exists(info.fullPath()));
        }

        @Test
        @DisplayName("Disallowed executable extension (.jsp, .exe, .sh, .html, .svg) is rejected")
        void testDisallowedExtensionsRejected() {
            // Test JSP
            MockMultipartFile jspFile = new MockMultipartFile("file", "webshell.jsp", "text/plain", "<% out.print(\"hacked\"); %>".getBytes());
            assertThrows(SecurityException.class, () ->
                    fileStorageService.storeFile(jspFile, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE));

            // Test EXE
            MockMultipartFile exeFile = new MockMultipartFile("file", "payload.exe", "application/octet-stream", MALICIOUS_EXE_BYTES);
            assertThrows(SecurityException.class, () ->
                    fileStorageService.storeFile(exeFile, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE));

            // Test HTML
            MockMultipartFile htmlFile = new MockMultipartFile("file", "xss.html", "text/html", "<html><script>alert(1)</script></html>".getBytes());
            assertThrows(SecurityException.class, () ->
                    fileStorageService.storeFile(htmlFile, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE));

            // Test SVG
            MockMultipartFile svgFile = new MockMultipartFile("file", "xss.svg", "image/svg+xml", "<svg onload=alert(1)>".getBytes());
            assertThrows(SecurityException.class, () ->
                    fileStorageService.storeFile(svgFile, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE));
        }

        @Test
        @DisplayName("Extension and magic byte signature mismatch is detected and rejected")
        void testSignatureMismatchRejected() {
            // An EXE renamed to .png
            MockMultipartFile spoofedPng = new MockMultipartFile(
                    "file", "trojan.png", "image/png", MALICIOUS_EXE_BYTES);

            SecurityException ex = assertThrows(SecurityException.class, () ->
                    fileStorageService.storeFile(spoofedPng, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE));
            assertTrue(ex.getMessage().contains("signature does not match"));

            // HTML script disguised as PDF
            MockMultipartFile spoofedPdf = new MockMultipartFile(
                    "file", "exploit.pdf", "application/pdf", "<script>alert('xss')</script>".getBytes());

            assertThrows(SecurityException.class, () ->
                    fileStorageService.storeFile(spoofedPdf, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE));
        }

        @Test
        @DisplayName("Zero-byte empty file is rejected")
        void testEmptyFileRejected() {
            MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
            assertThrows(IllegalArgumentException.class, () ->
                    fileStorageService.storeFile(emptyFile, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE));
        }

        @Test
        @DisplayName("File exceeding maximum configured size is rejected")
        void testFileSizeExceededRejected() {
            byte[] largeBytes = new byte[100];
            MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", largeBytes) {
                @Override
                public long getSize() {
                    return FileStorageService.MAX_ISSUE_FILE_SIZE + 1024L; // Exceeds limit
                }
            };

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    fileStorageService.storeFile(file, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE));
            assertTrue(ex.getMessage().contains("exceeds maximum allowed limit"));
        }
    }

    // =========================================================================
    // 2. Path Traversal & Filename Neutralization Tests
    // =========================================================================
    @Nested
    @DisplayName("2. Path Traversal & Filename Neutralization Tests")
    class PathTraversalNeutralizationTests {

        @Test
        @DisplayName("Directory traversal sequences in original filename are stripped")
        void testPathTraversalInFilenameNeutralized() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "../../../../etc/passwd.pdf", "application/pdf", VALID_PDF_BYTES);

            FileStorageService.StoredFileInfo info = fileStorageService.storeFile(
                    file, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE);

            assertEquals("passwd.pdf", info.originalFilename());
            assertFalse(info.storedFilename().contains("/"));
            assertFalse(info.storedFilename().contains("\\"));
            assertTrue(info.fullPath().startsWith(tempUploadDir.resolve("issues")));
        }

        @Test
        @DisplayName("Null bytes in filenames are stripped")
        void testNullByteInFilenameNeutralized() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "safe\0name.pdf", "application/pdf", VALID_PDF_BYTES);

            FileStorageService.StoredFileInfo info = fileStorageService.storeFile(
                    file, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE);

            assertEquals("safename.pdf", info.originalFilename());
            assertFalse(info.originalFilename().contains("\0"));
        }

        @Test
        @DisplayName("Windows reserved device names are neutralized")
        void testWindowsReservedNamesNeutralized() {
            String sanitized = fileStorageService.sanitizeFilename("CON.pdf");
            assertEquals("_CON.pdf", sanitized);

            String sanitizedNul = fileStorageService.sanitizeFilename("NUL.png");
            assertEquals("_NUL.png", sanitizedNul);

            String sanitizedAux = fileStorageService.sanitizeFilename("aux.txt");
            assertEquals("_aux.txt", sanitizedAux);
        }

        @Test
        @DisplayName("Stored filenames are always server-controlled UUIDs, never user-controlled")
        void testServerControlledStoredFilenames() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "MyUserSuppliedFilename.pdf", "application/pdf", VALID_PDF_BYTES);

            FileStorageService.StoredFileInfo info = fileStorageService.storeFile(
                    file, "issues", FileStorageService.ALLOWED_ISSUE_EXTENSIONS, FileStorageService.MAX_ISSUE_FILE_SIZE);

            assertNotEquals("MyUserSuppliedFilename.pdf", info.storedFilename());
            // Format is 36-char UUID + .pdf = 40 chars
            assertTrue(info.storedFilename().endsWith(".pdf"));
            assertEquals(40, info.storedFilename().length());
        }

        @Test
        @DisplayName("Path traversal attempts on resolveStoredFile are blocked")
        void testResolveStoredFileBlocksTraversal() {
            assertThrows(SecurityException.class, () ->
                    fileStorageService.resolveStoredFile("issues", "../../../windows/system32/cmd.exe"));
        }
    }

    // =========================================================================
    // 3. Download & IDOR / BOLA Prevention Tests
    // =========================================================================
    @Nested
    @DisplayName("3. Download & IDOR Prevention Tests")
    class DownloadAndIdorTests {

        private Task assignedTask;
        private Employee employeeAlice;
        private Employee employeeBob;
        private User adminUser;
        private Path sampleTaskFile;

        @BeforeEach
        void setupTaskAndUsers() throws IOException {
            employeeAlice = new Employee();
            employeeAlice.setId(101L);
            employeeAlice.setName("Alice");
            employeeAlice.setEmail("alice@logsphere.com");

            employeeBob = new Employee();
            employeeBob.setId(202L);
            employeeBob.setName("Bob");
            employeeBob.setEmail("bob@logsphere.com");

            adminUser = new User();
            adminUser.setId(1L);
            adminUser.setName("Admin");
            adminUser.setRole("ADMIN");

            // Create sample file on disk in tasks category
            Path tasksDir = tempUploadDir.resolve("tasks");
            Files.createDirectories(tasksDir);
            sampleTaskFile = tasksDir.resolve("task-doc-123.pdf");
            Files.write(sampleTaskFile, VALID_PDF_BYTES);

            assignedTask = new Task();
            assignedTask.setId(55L);
            assignedTask.setTitle("Confidential Alice Task");
            assignedTask.setEmployee(employeeAlice);
            assignedTask.setAttachmentUrl("/attachments/tasks/task-doc-123.pdf");
        }

        @Test
        @DisplayName("Unauthenticated request to download task attachment is rejected with 401")
        void testUnauthenticatedDownloadRejected() throws IOException {
            MockHttpSession session = new MockHttpSession(); // No user or employee
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/attachments/tasks/55");

            ResponseEntity<Resource> response = fileDownloadController.downloadTaskAttachment("55", session, request);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }

        @Test
        @DisplayName("Assigned employee (Alice) can download her own task attachment (200 OK)")
        void testAssignedEmployeeCanDownloadOwnTaskAttachment() throws IOException {
            when(taskRepository.findById(55L)).thenReturn(Optional.of(assignedTask));

            MockHttpSession session = new MockHttpSession();
            session.setAttribute("employee", employeeAlice);
            session.setAttribute("employeeName", "Alice");
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/attachments/tasks/55");

            ResponseEntity<Resource> response = fileDownloadController.downloadTaskAttachment("55", session, request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
        }

        @Test
        @DisplayName("Unassigned employee (Bob) is FORBIDDEN (403) from downloading Alice's task attachment (IDOR blocked)")
        void testUnassignedEmployeeCannotDownloadOtherEmployeeAttachment() throws IOException {
            when(taskRepository.findById(55L)).thenReturn(Optional.of(assignedTask));

            MockHttpSession session = new MockHttpSession();
            session.setAttribute("employee", employeeBob); // Bob is logged in
            session.setAttribute("employeeName", "Bob");
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/attachments/tasks/55");

            ResponseEntity<Resource> response = fileDownloadController.downloadTaskAttachment("55", session, request);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                    "Bob must NOT be allowed to access Alice's task attachment (IDOR prevention)");
        }

        @Test
        @DisplayName("Admin user can download any employee's task attachment (200 OK)")
        void testAdminCanDownloadAnyTaskAttachment() throws IOException {
            when(taskRepository.findById(55L)).thenReturn(Optional.of(assignedTask));

            MockHttpSession session = new MockHttpSession();
            session.setAttribute("user", adminUser);
            session.setAttribute("userRole", "ADMIN");
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/attachments/tasks/55");

            ResponseEntity<Resource> response = fileDownloadController.downloadTaskAttachment("55", session, request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("Download response includes secure Content-Disposition, nosniff, and CSP headers")
        void testDownloadSecurityHeaders() throws IOException {
            when(taskRepository.findById(55L)).thenReturn(Optional.of(assignedTask));

            MockHttpSession session = new MockHttpSession();
            session.setAttribute("user", adminUser);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/attachments/tasks/55");

            ResponseEntity<Resource> response = fileDownloadController.downloadTaskAttachment("55", session, request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            HttpHeaders headers = response.getHeaders();

            // 1. Content-Disposition: attachment
            assertNotNull(headers.getContentDisposition());
            assertTrue(headers.getContentDisposition().isAttachment());
            assertTrue(headers.getContentDisposition().getFilename().endsWith(".pdf"));

            // 2. X-Content-Type-Options: nosniff
            assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));

            // 3. Restrictive CSP sandbox
            assertEquals("default-src 'none'; sandbox", headers.getFirst("Content-Security-Policy"));

            // 4. Cache-Control: private, no-store
            assertTrue(headers.getFirst("Cache-Control").contains("no-store"));
        }

        @Test
        @DisplayName("Non-existent task attachment returns 404 Not Found")
        void testNonExistentTaskReturns404() throws IOException {
            when(taskRepository.findById(999L)).thenReturn(Optional.empty());

            MockHttpSession session = new MockHttpSession();
            session.setAttribute("user", adminUser);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/attachments/tasks/999");

            ResponseEntity<Resource> response = fileDownloadController.downloadTaskAttachment("999", session, request);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        @DisplayName("Legacy /uploads/tasks/{filename} URL maps to secure handler with IDOR check")
        void testLegacyTaskUploadUrlSecure() throws IOException {
            when(taskRepository.findFirstByAttachmentUrlEndingWith("task-doc-123.pdf"))
                    .thenReturn(Optional.of(assignedTask));

            MockHttpSession bobSession = new MockHttpSession();
            bobSession.setAttribute("employee", employeeBob);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/uploads/tasks/task-doc-123.pdf");

            // Bob trying to access via legacy URL should still get 403 Forbidden!
            ResponseEntity<Resource> response = fileDownloadController.downloadLegacyTaskUpload(
                    "task-doc-123.pdf", bobSession, request);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                    "Legacy upload URL must enforce the same IDOR protection");
        }
    }

    // =========================================================================
    // 4. Face Image Security Tests
    // =========================================================================
    @Nested
    @DisplayName("4. Face Recognition Image Validation & Safety Tests")
    class FaceImageSecurityTests {

        @Test
        @DisplayName("Valid JPEG face image with FF D8 FF signature is accepted")
        void testValidJpegFaceImageAccepted() {
            byte[] jpegData = new byte[2000];
            System.arraycopy(VALID_JPEG_BYTES, 0, jpegData, 0, VALID_JPEG_BYTES.length);
            String base64Jpeg = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpegData);

            assertTrue(faceRecognitionService.validateFaceImage(base64Jpeg));
        }

        @Test
        @DisplayName("Valid PNG face image with 89 50 4E 47 signature is accepted")
        void testValidPngFaceImageAccepted() {
            byte[] pngData = new byte[2000];
            System.arraycopy(VALID_PNG_BYTES, 0, pngData, 0, VALID_PNG_BYTES.length);
            String base64Png = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);

            assertTrue(faceRecognitionService.validateFaceImage(base64Png));
        }

        @Test
        @DisplayName("Non-image payload (e.g. text/html/executable) is rejected by validateFaceImage")
        void testNonImagePayloadRejected() {
            byte[] htmlData = "<html><script>alert('xss')</script></html>".getBytes();
            // Pad to 1500 bytes to pass minimum size
            byte[] padded = new byte[1500];
            System.arraycopy(htmlData, 0, padded, 0, htmlData.length);
            String base64Html = Base64.getEncoder().encodeToString(padded);

            assertFalse(faceRecognitionService.validateFaceImage(base64Html),
                    "HTML payload disguised as face image must be rejected");
        }

        @Test
        @DisplayName("Face image smaller than 1KB minimum is rejected")
        void testTooSmallFaceImageRejected() {
            byte[] smallData = new byte[500];
            System.arraycopy(VALID_JPEG_BYTES, 0, smallData, 0, VALID_JPEG_BYTES.length);
            String base64Small = Base64.getEncoder().encodeToString(smallData);

            assertFalse(faceRecognitionService.validateFaceImage(base64Small));
        }

        @Test
        @DisplayName("Oversized face payload (>5MB) is rejected to prevent memory exhaustion")
        void testOversizedFacePayloadRejected() {
            // String longer than 7,000,000 chars
            String hugeString = "A".repeat(7_000_001);
            assertFalse(faceRecognitionService.validateFaceImage(hugeString));
        }
    }
}
