package com.repairshop.saas.ticket.exception;

import lombok.Getter;

/**
 * An IMEI is already on another booking that hasn't been handed back yet.
 *
 * Its own type rather than a ResponseStatusException on purpose: this service's
 * {@link GlobalExceptionHandler} declares an {@code @ExceptionHandler(Exception.class)},
 * and Spring resolves @ExceptionHandler methods before its own
 * ResponseStatusExceptionResolver — so a ResponseStatusException thrown from a
 * service would come back to the app as a 500, not the status it named.
 *
 * The handler turns this into a 409 carrying {@code code = IMEI_ALREADY_USED},
 * which is what the shop app branches on to show its "IMEI Already Used" alert
 * rather than a generic failure toast.
 */
@Getter
public class ImeiConflictException extends RuntimeException {

    /** Tracking id of the booking already holding the IMEI, for the message. */
    private final String conflictTrackingId;

    public ImeiConflictException(String message, String conflictTrackingId) {
        super(message);
        this.conflictTrackingId = conflictTrackingId;
    }
}
