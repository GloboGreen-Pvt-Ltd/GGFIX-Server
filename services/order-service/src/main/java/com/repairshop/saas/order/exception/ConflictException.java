package com.repairshop.saas.order.exception;

/** The request is valid but the record's current state doesn't allow it (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
