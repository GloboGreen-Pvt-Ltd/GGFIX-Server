package com.repairshop.saas.marketplace.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return response(HttpStatus.NOT_FOUND, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed")
                .path(req.getRequestURI())
                .fieldErrors(errors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleBadRequestBody(HttpMessageNotReadableException ex, HttpServletRequest req) {
        String message = "Invalid request body";
        if (ex.getMessage() != null && ex.getMessage().contains("UUID")) {
            message = "IDs must be valid UUIDs.";
        } else if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            message = message + ": " + ex.getCause().getMessage();
        }
        return response(HttpStatus.BAD_REQUEST, message, req.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return response(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex, HttpServletRequest req) {
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("Missing user context") || msg.contains("Missing shop context"))) {
            return response(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization token", req.getRequestURI());
        }
        return response(HttpStatus.BAD_REQUEST, msg, req.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return response(HttpStatus.FORBIDDEN, "Access denied", req.getRequestURI());
    }

    /**
     * Honour the status code that {@link ResponseStatusException} carries —
     * without this, a `throw new ResponseStatusException(UNAUTHORIZED, …)` is
     * swallowed by the generic Exception handler below and surfaces as a
     * misleading 500 "Internal server error" to the client.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return response(status, reason, req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        // Log the actual stack trace so the cause isn't lost behind the generic
        // "Internal server error" body. Surface the root cause message in the
        // response so the client can show something more useful than "ISE".
        log.error("Unhandled exception on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        String rootMessage = rootCauseMessage(ex);
        String body = (rootMessage != null && !rootMessage.isBlank())
                ? "Server error: " + rootMessage
                : "Internal server error";
        return response(HttpStatus.INTERNAL_SERVER_ERROR, body, req.getRequestURI());
    }

    private static String rootCauseMessage(Throwable ex) {
        Throwable cur = ex;
        String last = ex.getMessage();
        int hop = 0;
        while (cur.getCause() != null && cur.getCause() != cur && hop < 6) {
            cur = cur.getCause();
            if (cur.getMessage() != null && !cur.getMessage().isBlank()) last = cur.getMessage();
            hop++;
        }
        return last;
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, String path) {
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
