package com.planted.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(errorBody(
                ex.getMessage() != null ? ex.getMessage() : "Invalid request state"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(errorBody("Invalid or unreadable request body"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex) {
        log.error("Database error", ex);
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        String detail = root.getMessage() != null ? root.getMessage() : ex.getMessage();
        String message = "Database error while processing the request.";
        if (detail != null && (detail.contains("column") || detail.contains("does not exist"))) {
            message = "Database schema may be out of date. Restart the backend so Flyway can run migrations "
                    + "(including info_panel_summary on plant_analyses). Details: " + detail;
        } else if (detail != null
                && (detail.contains("did not return a unique result")
                        || detail.contains("Incorrect result size"))) {
            message = "A query expected one row but several matched (often several INFO_PANEL or care analyses). "
                    + "Stop the backend completely and start it again from the current codebase so paged "
                    + "repository methods are loaded. Technical detail: " + detail;
        } else if (detail != null && !detail.isBlank()) {
            message = message + " " + detail;
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(errorBody("File too large. Maximum upload size exceeded."));
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<Map<String, Object>> handleNotWritable(HttpMessageNotWritableException ex) {
        log.error("Failed to serialize response body", ex);
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        String detail = describeThrowable(root);
        if (detail == null || detail.isBlank()) {
            detail = describeThrowable(ex);
        }
        String message = "Failed to serialize the response.";
        if (detail != null && !detail.isBlank()) {
            message = message + " " + detail;
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        String msg = root.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex.getMessage();
        }
        if (msg == null || msg.isBlank()) {
            msg = fallbackMessageFor(ex, root);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(msg));
    }

    /**
     * When {@link Throwable#getMessage()} is null (e.g. many {@link NullPointerException}s), still return
     * something actionable for clients and logs.
     */
    private static String fallbackMessageFor(Exception ex, Throwable root) {
        String base = ex.getClass().getName();
        if (root != null && root != ex) {
            base = base + " (caused by " + root.getClass().getName() + ")";
        }
        return base;
    }

    private static String describeThrowable(Throwable t) {
        if (t == null) {
            return null;
        }
        String msg = t.getMessage();
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        return t.getClass().getName();
    }

    private Map<String, Object> errorBody(String message) {
        return Map.of(
                "error", message,
                "timestamp", OffsetDateTime.now().toString()
        );
    }
}
