package com.repairshop.saas.auth.exception;

/**
 * Caller is authenticated but lacks the role required for the action.
 * Maps to 403 in {@link GlobalExceptionHandler} — distinct from
 * {@link UnauthorizedException} (401), which means "we don't know who you are".
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
