package com.example.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Global database and data integrity exception handler.
 * Prevents SQL/database error leakage, sanitizes client-facing error messages,
 * and maintains atomic transactional boundaries across HTML and JSON endpoints.
 */
@ControllerAdvice
public class GlobalDatabaseExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalDatabaseExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        logger.warn("Database data integrity violation on URI [{}]: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());

        String sanitizedMessage = "A database integrity error occurred while saving the data. Please verify your input and try again.";
        String detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (detail != null) {
            String lower = detail.toLowerCase();
            if (lower.contains("unique") || lower.contains("duplicate")) {
                sanitizedMessage = "A record with this information already exists. Please verify your input and try again.";
            } else if (lower.contains("foreign key") || lower.contains("referential integrity")) {
                sanitizedMessage = "Cannot perform this operation because the record is linked to other existing data.";
            } else if (lower.contains("not null") || lower.contains("column cannot be null")) {
                sanitizedMessage = "A required field was missing. Please ensure all required fields are completed.";
            }
        }

        if (isJsonRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("error", "DataIntegrityConflict");
            body.put("message", sanitizedMessage);
            body.put("status", HttpStatus.CONFLICT.value());
            return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return handleHtmlRedirect(request, response, sanitizedMessage);
    }

    @ExceptionHandler({CannotAcquireLockException.class, ObjectOptimisticLockingFailureException.class})
    public Object handleConcurrencyException(Exception ex,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        logger.warn("Database concurrency conflict on URI [{}]: {}", request.getRequestURI(), ex.getMessage());

        String sanitizedMessage = "The requested record was modified concurrently by another process. Please refresh the page and try again.";

        if (isJsonRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("error", "ConcurrencyConflict");
            body.put("message", sanitizedMessage);
            body.put("status", HttpStatus.CONFLICT.value());
            return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return handleHtmlRedirect(request, response, sanitizedMessage);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolation(ConstraintViolationException ex,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        logger.warn("Validation constraint violation on URI [{}]: {}", request.getRequestURI(), ex.getMessage());

        String sanitizedMessage = "Validation failed: Please ensure all required fields are correctly formatted.";

        if (isJsonRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("error", "ValidationError");
            body.put("message", sanitizedMessage);
            body.put("status", HttpStatus.BAD_REQUEST.value());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return handleHtmlRedirect(request, response, sanitizedMessage);
    }

    @ExceptionHandler({SQLException.class, DataAccessException.class})
    public Object handleDatabaseException(Exception ex,
                                          HttpServletRequest request,
                                          HttpServletResponse response) {
        logger.error("Database operation failed on URI [{}]: {}", request.getRequestURI(), ex.getMessage());

        String sanitizedMessage = "A database error occurred while processing your request. Please try again or contact support.";

        if (isJsonRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("error", "DatabaseError");
            body.put("message", sanitizedMessage);
            body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return handleHtmlRedirect(request, response, sanitizedMessage);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, java.time.format.DateTimeParseException.class})
    public Object handleValidationAndArgumentException(Exception ex,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        logger.warn("Validation error on URI [{}]: {}", request.getRequestURI(), ex.getMessage());

        String sanitizedMessage = ex.getMessage();
        if (ex instanceof java.time.format.DateTimeParseException) {
            sanitizedMessage = "Invalid date format provided. Expected format: YYYY-MM-DD.";
        } else if (sanitizedMessage == null || sanitizedMessage.isBlank()) {
            sanitizedMessage = "Invalid request parameters provided.";
        }

        if (isJsonRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("error", "ValidationError");
            body.put("message", sanitizedMessage);
            body.put("status", HttpStatus.BAD_REQUEST.value());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return handleHtmlRedirect(request, response, sanitizedMessage);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex,
                                             HttpServletRequest request,
                                             HttpServletResponse response) {
        logger.warn("Upload size limit exceeded on URI [{}]: {}", request.getRequestURI(), ex.getMessage());

        String sanitizedMessage = "Uploaded file or request payload exceeds the maximum permitted size limit.";

        if (isJsonRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("error", "PayloadTooLarge");
            body.put("message", sanitizedMessage);
            body.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return handleHtmlRedirect(request, response, sanitizedMessage);
    }

    @ExceptionHandler({
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
    })
    public Object handleMissingOrMismatchedParameter(Exception ex,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        logger.warn("Parameter binding error on URI [{}]: {}", request.getRequestURI(), ex.getMessage());

        String sanitizedMessage = "Invalid or missing required request parameter.";

        if (isJsonRequest(request)) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("error", "BadRequest");
            body.put("message", sanitizedMessage);
            body.put("status", HttpStatus.BAD_REQUEST.value());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return handleHtmlRedirect(request, response, sanitizedMessage);
    }

    private boolean isJsonRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        String uri = request.getRequestURI();

        return (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (uri != null && (uri.startsWith("/api/") || uri.endsWith(".json")));
    }

    private ModelAndView handleHtmlRedirect(HttpServletRequest request,
                                            HttpServletResponse response,
                                            String sanitizedMessage) {
        FlashMap flashMap = new FlashMap();
        flashMap.put("error", sanitizedMessage);
        FlashMapManager flashMapManager = RequestContextUtils.getFlashMapManager(request);
        if (flashMapManager != null) {
            flashMapManager.saveOutputFlashMap(flashMap, request, response);
        }

        String referer = request.getHeader("Referer");
        String redirectTarget = "/dashboard";

        if (referer != null && !referer.isBlank()) {
            try {
                java.net.URI uri = java.net.URI.create(referer);
                String path = uri.getPath();
                if (path != null && !path.isBlank() && !path.startsWith("//")) {
                    String query = uri.getQuery();
                    redirectTarget = path + (query != null && !query.isBlank() ? "?" + query : "");
                }
            } catch (Exception ignored) {
                redirectTarget = "/dashboard";
            }
        }

        return new ModelAndView("redirect:" + redirectTarget);
    }
}
