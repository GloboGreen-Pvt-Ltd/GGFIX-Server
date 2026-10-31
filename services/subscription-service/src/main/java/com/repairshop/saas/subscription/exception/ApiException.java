package com.repairshop.saas.subscription.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A refusal with a stable machine code, rendered by {@link ApiExceptionHandler}
 * as {@code { "message": "...", "code": "..." }} (the Subscription v2 contract).
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException paymentsNotConfigured() {
        return conflict("PAYMENTS_NOT_CONFIGURED", "Payments are not configured");
    }

    /** Same code; the message says why (missing keys vs key/RAZORPAY_MODE mismatch). */
    public static ApiException paymentsNotConfigured(String reason) {
        return conflict("PAYMENTS_NOT_CONFIGURED", reason == null ? "Payments are not configured" : reason);
    }
}
