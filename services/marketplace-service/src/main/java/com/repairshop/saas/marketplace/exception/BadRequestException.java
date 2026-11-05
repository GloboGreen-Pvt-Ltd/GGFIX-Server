package com.repairshop.saas.marketplace.exception;

/**
 * Input validation failure: HTTP 400 {message}. Extends IllegalArgumentException
 * so the existing GlobalExceptionHandler mapping applies.
 */
public class BadRequestException extends IllegalArgumentException {

    public BadRequestException(String message) {
        super(message);
    }
}
