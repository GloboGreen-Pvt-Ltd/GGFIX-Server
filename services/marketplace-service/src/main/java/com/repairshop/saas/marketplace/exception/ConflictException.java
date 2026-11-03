package com.repairshop.saas.marketplace.exception;

/** Invalid state transition / business conflict: mapped to HTTP 409 {message}. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
