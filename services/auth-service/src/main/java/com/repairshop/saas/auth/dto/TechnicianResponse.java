package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Technician user for assignment list")
public class TechnicianResponse {

    @Schema(description = "User ID (use as assignedTechnicianId)")
    private UUID id;

    @Schema(description = "Display name")
    private String name;

    @Schema(description = "Email")
    private String email;

    @Schema(description = "Role label")
    private String roleLabel;
}
