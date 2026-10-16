package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Enterprise secure file storage service:
 * 1. Per-category extension allowlists with strict enforcement
 * 2. Signature / magic number inspection against file spoofing
 * 3. Filename sanitization and path traversal neutralization
 * 4. Server-controlled UUID-based storage filenames
 * 5. File size upper bounds
 * 6. Isolated storage outside static web assets
 * 7. Automatic cleanup of failed or partial uploads
 */
@Service
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    // Sane upper limits per category
    public static final long MAX_ISSUE_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    public static final long MAX_TASK_FILE_SIZE = 25L * 1024 * 1024;  // 25 MB
    public static final long MAX_FACE_IMAGE_SIZE = 5L * 1024 * 1024;  // 5 MB

    // Allowed extensions per category
    public static final Set<String> ALLOWED_ISSUE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "pdf", "txt", "log", "csv", "docx", "xlsx"
    );

    public static final Set<String> ALLOWED_TASK_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "pdf", "txt", "doc", "docx", "xlsx", "zip"
    );

    // Explicit blacklist of dangerous extensions (defense-in-depth)
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "dll", "so", "dylib", "bin", "sh", "bash", "bat", "cmd", "com",
            "vbs", "vbe", "js", "jse", "wsf", "wsh", "ps1", "ps2", "psc1", "psc2",
            "jsp", "jspx", "jspf", "action", "do", "php", "php3", "php4", "php5", "phtml",
            "asp", "aspx", "ascx", "ashx", "asmx", "cgi", "pl", "py", "rb",
            "html", "htm", "xhtml", "shtml", "svg", "svgz", "xml", "xslt", "xsl",
            "jar", "war", "ear", "class", "htaccess", "htpasswd", "config", "env"
    );

    // Reserved Windows device names
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private static final Pattern SAFE_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]");

    /**
     * Carrier record for securely stored file metadata
     */
    public record StoredFileInfo(
            String originalFilename,
            String storedFilename,
            String category,
            long fileSize,
            String contentType,
            Path fullPath
    ) {}

    /**
     * Stores an uploaded multipart file securely on disk after comprehensive security validations.
     */
    public StoredFileInfo storeFile(MultipartFile file, String category, Set<String> allowedExtensions, long maxSizeBytes)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("File size (" + file.getSize() + " bytes) exceeds maximum allowed limit of "
                    + maxSizeBytes + " bytes");
        }

        String rawOriginalFilename = file.getOriginalFilename();
        if (rawOriginalFilename == null || rawOriginalFilename.trim().isEmpty()) {
            rawOriginalFilename = "attachment";
        }

        // 1. Sanitize original filename
        String sanitizedOriginalFilename = sanitizeFilename(rawOriginalFilename);

        // 2. Extract and validate extension
        String extension = getFileExtension(sanitizedOriginalFilename).toLowerCase(Locale.ROOT);
        if (extension.isEmpty()) {
            throw new IllegalArgumentException("File must have a valid extension");
        }

        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new SecurityException("File extension ." + extension + " is prohibited for security reasons");
        }

        if (allowedExtensions != null && !allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException("File extension ." + extension + " is not permitted for " + category);
        }

        // 3. Inspect magic bytes / file signature
        byte[] headerBytes = extractHeaderBytes(file, 16);
        if (!isValidSignature(headerBytes, extension)) {
            throw new SecurityException("File content signature does not match declared extension ." + extension);
        }

        // 4. Resolve and verify target storage directory
        Path baseDir = getUploadBaseDir();
        Path categoryDir = baseDir.resolve(category).normalize();
        if (!categoryDir.startsWith(baseDir)) {
            throw new SecurityException("Path traversal detected in category path");
        }

        if (!Files.exists(categoryDir)) {
            Files.createDirectories(categoryDir);
        }

        // 5. Generate server-controlled random storage filename
        String storedFilename = UUID.randomUUID().toString() + "." + extension;
        Path targetPath = categoryDir.resolve(storedFilename).normalize();

        // 6. Path traversal check on target file
        if (!targetPath.startsWith(categoryDir)) {
            throw new SecurityException("Path traversal detected in target filename");
        }

        // 7. Store file safely with atomic replace and failure cleanup
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // Clean up any partially written file
            try {
                Files.deleteIfExists(targetPath);
            } catch (Exception ignored) {}
            throw new IOException("Failed to save file: " + e.getMessage(), e);
        }

        String contentType = determineSafeContentType(extension);

        return new StoredFileInfo(
                sanitizedOriginalFilename,
                storedFilename,
                category,
                file.getSize(),
                contentType,
                targetPath
        );
    }

    /**
     * Resolves an existing stored file path and confirms it is within the category boundary.
     */
    public Path resolveStoredFile(String category, String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) {
            return null;
        }

        if (storedFilename.contains("..") || storedFilename.contains("/") || storedFilename.contains("\\")) {
            throw new SecurityException("Access denied: Path traversal detected in filename");
        }

        // Strip path traversal attempts from requested filename
        String cleanName = Paths.get(storedFilename).getFileName().toString();

        Path baseDir = getUploadBaseDir();
        Path categoryDir = baseDir.resolve(category).normalize();
        Path targetPath = categoryDir.resolve(cleanName).normalize();

        if (!targetPath.startsWith(categoryDir)) {
            throw new SecurityException("Access denied: Path traversal detected");
        }

        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            return null;
        }

        return targetPath;
    }

    /**
     * Sanitizes a user-supplied filename against path traversal, control characters, null bytes,
     * and Windows device names.
     */
    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "attachment";
        }

        // Strip path components
        String name = filename.replace("\\", "/");
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        // Strip null bytes and control characters
        name = name.replace("\0", "").replaceAll("[\\r\\n\\t]", "");

        // Replace unsafe characters
        name = SAFE_CHARS_PATTERN.matcher(name).replaceAll("_");

        // Trim leading and trailing dots / spaces
        name = name.replaceAll("^[. ]+", "").replaceAll("[. ]+$", "");

        if (name.isEmpty()) {
            name = "attachment";
        }

        // Check Windows reserved names (e.g. CON, PRN, AUX, NUL)
        String baseWithoutExt = name;
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0) {
            baseWithoutExt = name.substring(0, dotIdx);
        }
        if (WINDOWS_RESERVED_NAMES.contains(baseWithoutExt.toUpperCase(Locale.ROOT))) {
            name = "_" + name;
        }

        // Limit length
        if (name.length() > 200) {
            String ext = getFileExtension(name);
            String base = name.substring(0, Math.min(190, name.length() - ext.length() - 1));
            name = base + "." + ext;
        }

        return name;
    }

    /**
     * Extracts the extension from a filename.
     */
    public String getFileExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }

    /**
     * Validates file signature / magic numbers against the claimed extension.
     */
    public boolean isValidSignature(byte[] headerBytes, String extension) {
        if (headerBytes == null || headerBytes.length == 0) {
            return false;
        }

        String ext = extension.toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" ->
                // PNG magic: 89 50 4E 47 0D 0A 1A 0A
                headerBytes.length >= 8
                        && (headerBytes[0] & 0xFF) == 0x89
                        && (headerBytes[1] & 0xFF) == 0x50
                        && (headerBytes[2] & 0xFF) == 0x4E
                        && (headerBytes[3] & 0xFF) == 0x47
                        && (headerBytes[4] & 0xFF) == 0x0D
                        && (headerBytes[5] & 0xFF) == 0x0A
                        && (headerBytes[6] & 0xFF) == 0x1A
                        && (headerBytes[7] & 0xFF) == 0x0A;

            case "jpg", "jpeg" ->
                // JPEG magic: FF D8 FF
                headerBytes.length >= 3
                        && (headerBytes[0] & 0xFF) == 0xFF
                        && (headerBytes[1] & 0xFF) == 0xD8
                        && (headerBytes[2] & 0xFF) == 0xFF;

            case "pdf" ->
                // PDF magic: %PDF (25 50 44 46)
                headerBytes.length >= 4
                        && (headerBytes[0] & 0xFF) == 0x25
                        && (headerBytes[1] & 0xFF) == 0x50
                        && (headerBytes[2] & 0xFF) == 0x44
                        && (headerBytes[3] & 0xFF) == 0x46;

            case "zip", "docx", "xlsx" ->
                // ZIP container magic: PK\x03\x04 (50 4B 03 04)
                headerBytes.length >= 4
                        && (headerBytes[0] & 0xFF) == 0x50
                        && (headerBytes[1] & 0xFF) == 0x4B
                        && (headerBytes[2] & 0xFF) == 0x03
                        && (headerBytes[3] & 0xFF) == 0x04;

            case "doc" ->
                // Legacy Word: OLE2 Compound Document (D0 CF 11 E0 A1 B1 1A E1) or ZIP (.docx)
                (headerBytes.length >= 8
                        && (headerBytes[0] & 0xFF) == 0xD0
                        && (headerBytes[1] & 0xFF) == 0xCF
                        && (headerBytes[2] & 0xFF) == 0x11
                        && (headerBytes[3] & 0xFF) == 0xE0
                        && (headerBytes[4] & 0xFF) == 0xA1
                        && (headerBytes[5] & 0xFF) == 0xB1
                        && (headerBytes[6] & 0xFF) == 0x1A
                        && (headerBytes[7] & 0xFF) == 0xE1)
                        || (headerBytes.length >= 4
                        && (headerBytes[0] & 0xFF) == 0x50
                        && (headerBytes[1] & 0xFF) == 0x4B
                        && (headerBytes[2] & 0xFF) == 0x03
                        && (headerBytes[3] & 0xFF) == 0x04);

            case "txt", "log", "csv" ->
                // Text files: Must NOT start with executable / script signatures
                isValidPlainTextHeader(headerBytes);

            default -> false;
        };
    }

    /**
     * Inspects text files to ensure they don't contain binary executable headers,
     * shell script shebangs, PHP tags, or HTML/JS tags disguised as text.
     */
    private boolean isValidPlainTextHeader(byte[] header) {
        if (header.length >= 2 && (header[0] & 0xFF) == 0x4D && (header[1] & 0xFF) == 0x5A) {
            return false; // PE executable / MZ
        }
        if (header.length >= 4 && (header[0] & 0xFF) == 0x7F && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0x4C && (header[3] & 0xFF) == 0x46) {
            return false; // ELF executable
        }
        if (header.length >= 4 && (header[0] & 0xFF) == 0xCA && (header[1] & 0xFF) == 0xFE
                && (header[2] & 0xFF) == 0xBA && (header[3] & 0xFF) == 0xBE) {
            return false; // Java class file
        }
        if (header.length >= 2 && header[0] == '#' && header[1] == '!') {
            return false; // Shell shebang
        }
        if (header.length >= 2 && header[0] == '<' && (header[1] == '?' || header[1] == '!')) {
            return false; // PHP tag or HTML doctype
        }

        // Ensure header characters are valid text (no binary null bytes in first bytes)
        for (byte b : header) {
            if (b == 0) {
                return false; // Null byte indicates binary content
            }
        }
        return true;
    }

    /**
     * Determines a safe, non-executable Content-Type based on extension.
     */
    public String determineSafeContentType(String extension) {
        String ext = extension.toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "pdf" -> "application/pdf";
            case "txt", "log" -> "text/plain";
            case "csv" -> "text/csv";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "doc" -> "application/msword";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    /**
     * Reads the first N bytes from a MultipartFile without consuming the stream permanently.
     */
    private byte[] extractHeaderBytes(MultipartFile file, int numBytes) throws IOException {
        try (InputStream is = file.getInputStream();
             BufferedInputStream bis = new BufferedInputStream(is)) {
            byte[] buffer = new byte[numBytes];
            int read = bis.read(buffer, 0, numBytes);
            if (read < 0) {
                return new byte[0];
            }
            if (read < numBytes) {
                return Arrays.copyOf(buffer, read);
            }
            return buffer;
        }
    }

    public Path getUploadBaseDir() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }
}
