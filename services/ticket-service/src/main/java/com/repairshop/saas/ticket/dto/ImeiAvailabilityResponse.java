package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Answer to "can this booking use this IMEI?" — the pre-flight the shop app
 * runs before opening the invoice generator. `valid` and `available` are
 * separate because they mean different things to the user: a malformed number
 * needs correcting, a taken one needs looking up.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Whether an IMEI is well-formed and free for this booking")
public class ImeiAvailabilityResponse {

    @Schema(description = "The normalised (digits-only) IMEI that was checked, or the raw input when invalid")
    private String imei;

    @Schema(description = "True when the IMEI is 14-17 digits")
    private boolean valid;

    @Schema(description = "True when no other still-open booking in this shop holds it")
    private boolean available;

    @Schema(description = "Tracking id of the booking already holding it, when available=false")
    private String conflictTrackingId;

    @Schema(description = "Human-readable reason when the IMEI can't be used")
    private String message;
}
