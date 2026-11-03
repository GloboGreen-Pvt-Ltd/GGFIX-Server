package com.repairshop.saas.ticket.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Re-estimate workflow refusal with a machine-readable {@code code} the apps
 * branch on (REESTIMATE_PENDING, INVALID_TRANSITION, NOT_ASSIGNED_TECHNICIAN,
 * ROLE_NOT_ALLOWED, VALIDATION). Its own type for the same reason as
 * {@link ImeiConflictException}: GlobalExceptionHandler turns it into a flat
 * body carrying {@code code}.
 */
@Getter
public class ReEstimateException extends RuntimeException {

    public static final String REESTIMATE_PENDING = "REESTIMATE_PENDING";
    public static final String INVALID_TRANSITION = "INVALID_TRANSITION";
    public static final String NOT_ASSIGNED_TECHNICIAN = "NOT_ASSIGNED_TECHNICIAN";
    public static final String ROLE_NOT_ALLOWED = "ROLE_NOT_ALLOWED";
    public static final String VALIDATION = "VALIDATION";

    private final HttpStatus status;
    private final String code;

    public ReEstimateException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ReEstimateException conflict(String code, String message) {
        return new ReEstimateException(HttpStatus.CONFLICT, code, message);
    }

    public static ReEstimateException forbidden(String code, String message) {
        return new ReEstimateException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ReEstimateException badRequest(String message) {
        return new ReEstimateException(HttpStatus.BAD_REQUEST, VALIDATION, message);
    }
}
