package com.repairshop.saas.auth.exception;

/**
 * The request is well-formed but collides with existing state — e.g. signing up
 * with a mobile number that already has an account. Maps to 409 so a client can
 * tell "this identifier is taken" apart from a 400 "you sent something invalid"
 * without string-matching the message.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
