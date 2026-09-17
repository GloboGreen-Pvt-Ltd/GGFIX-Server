package com.repairshop.saas.ticket.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Spring throws this when a request hits a path that isn't mapped to any
     * controller method (typical cause: deployed JVM doesn't have the route —
     * either the service wasn't restarted after adding a new @PostMapping, or
     * the mobile app is calling the wrong port). The default Exception
     * handler used to swallow this and return an opaque 500, which made the
     * "JVM not restarted" case impossible to diagnose from the client. Now
     * it surfaces as a 404 that names the unmatched path.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        String path = req.getRequestURI();
        log.warn("404 no resource for {} {} — controller route not registered (rebuild + restart this JVM if you just added the endpoint)",
                req.getMethod(), path);
        return response(HttpStatus.NOT_FOUND,
                "No handler for " + req.getMethod() + " " + path
                        + " — verify the URL or restart this service if the endpoint was just added",
                path);
    }

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
            message = "customerId, brandId, modelId and other IDs must be valid UUIDs (e.g. 00000000-0000-0000-0000-000000000002). Got invalid value.";
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
        if ("Missing shop context".equals(ex.getMessage())) {
            return response(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization token (shop context required)", req.getRequestURI());
        }
        return response(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        // Log the full stack trace at ERROR so the operator can actually
        // diagnose what went wrong. Previous behavior swallowed everything
        // into an opaque "Internal server error" message, which made
        // bugs that happened to escape a controller-local try/catch
        // basically un-debuggable.
        log.error("500 unhandled exception for {} {}: {}",
                req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        String detail = ex.getClass().getSimpleName()
                + (ex.getMessage() != null ? ": " + ex.getMessage() : "");
        return response(HttpStatus.INTERNAL_SERVER_ERROR, detail, req.getRequestURI());
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
