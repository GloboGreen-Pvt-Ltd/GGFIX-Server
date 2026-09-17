package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Optional notes and location for check-in/check-out")
public class AttendanceCheckRequest {

    @Schema(description = "Optional notes")
    private String notes;

    @Schema(description = "Optional latitude")
    private Double latitude;

    @Schema(description = "Optional longitude")
    private Double longitude;
}
