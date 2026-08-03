package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional notes + GPS coordinates for the shop geofence gate")
public class AttendanceCheckRequest {

    @Schema(description = "Optional notes")
    private String notes;

    @Schema(description = "Employee's current latitude (required when the shop has coordinates configured)")
    private Double latitude;

    @Schema(description = "Employee's current longitude (required when the shop has coordinates configured)")
    private Double longitude;
}
