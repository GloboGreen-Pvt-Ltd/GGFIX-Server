package com.repairshop.saas.ticket.exception;

import java.util.Collections;
import java.util.Map;

/**
 * Thrown when an attendance action (check-in / check-out) is refused by a
 * business gate rather than an outright error — the two current gates are:
 *
 *   * the 100m shop geofence (codes LOCATION_REQUIRED / SHOP_LOCATION_MISSING /
 *     OUT_OF_RADIUS), mirroring the pickup-person "Reached Shop" flow; and
 *   * the early-checkout guard (code EARLY_CHECKOUT_BLOCKED).
 *
 * {@link GlobalExceptionHandler} renders this as HTTP 422 with a JSON body that
 * carries {@code code} plus any {@code details} (e.g. distanceMeters) so the
 * employee app can show a precise, actionable message.
 */
public class AttendanceBlockedException extends RuntimeException {

    private final int status;
    private final String code;
    private final transient Map<String, Object> details;

    public AttendanceBlockedException(String code, String message) {
        this(422, code, message, Collections.emptyMap());
    }

    public AttendanceBlockedException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details != null ? details : Collections.emptyMap();
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
