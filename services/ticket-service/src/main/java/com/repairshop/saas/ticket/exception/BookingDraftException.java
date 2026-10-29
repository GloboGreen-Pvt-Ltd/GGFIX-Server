package com.repairshop.saas.ticket.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A deliberate booking-draft refusal. BookingDraftController renders it as
 * {@code { status, error, message, code, ...details }}.
 */
@Getter
public class BookingDraftException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public BookingDraftException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public BookingDraftException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = new LinkedHashMap<>(details);
    }

    public static BookingDraftException notFound() {
        return new BookingDraftException(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", "Draft not found.");
    }

    public static BookingDraftException closed(String draftStatus) {
        return new BookingDraftException(HttpStatus.CONFLICT, "DRAFT_CLOSED",
                "This draft is already " + draftStatus.toLowerCase() + ".",
                Map.of("draftStatus", draftStatus));
    }

    public static BookingDraftException versionConflict(int currentVersion) {
        return new BookingDraftException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "This draft was changed elsewhere. Reload it and try again.",
                Map.of("currentVersion", currentVersion));
    }

    public static BookingDraftException badRequest(String code, String message) {
        return new BookingDraftException(HttpStatus.BAD_REQUEST, code, message);
    }
}
