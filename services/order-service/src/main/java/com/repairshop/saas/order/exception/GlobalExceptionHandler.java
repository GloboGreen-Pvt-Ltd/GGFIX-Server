package com.repairshop.saas.order.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found", "message", e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> forbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> bad(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "bad_request", "message", e.getMessage()));
    }

    /**
     * Unmapped route — typically the JVM is running stale code that doesn't
     * yet have a recently-added @RequestMapping. Surface this clearly so the
     * client knows it's a deploy / restart issue, not a request-shape issue.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> noResource(NoResourceFoundException e, HttpServletRequest req) {
        String path = req.getRequestURI();
        log.warn("404 no resource for {} {} — controller route not registered (rebuild + restart this JVM if you just added the endpoint)",
                req.getMethod(), path);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "not_found",
                "message", "No handler for " + req.getMethod() + " " + path
                        + " — verify the URL or restart this service if the endpoint was just added"));
    }

    /**
     * Catch-all so an unexpected throw doesn't produce an opaque 500 with
     * no body. The stack trace is logged at ERROR so the operator can
     * actually diagnose the failure; the response carries the exception
     * class name + message so the mobile client can show it to the user.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> generic(Exception e, HttpServletRequest req) {
        log.error("500 unhandled exception for {} {}: {}",
                req.getMethod(), req.getRequestURI(), e.getMessage(), e);
        String detail = e.getClass().getSimpleName()
                + (e.getMessage() != null ? ": " + e.getMessage() : "");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "internal_error",
                "message", detail));
    }
}
