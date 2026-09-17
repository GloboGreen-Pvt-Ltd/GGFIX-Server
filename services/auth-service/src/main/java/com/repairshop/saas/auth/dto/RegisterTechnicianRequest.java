package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Add a technician to a shop")
public class RegisterTechnicianRequest {

    @NotBlank(message = "Email is required")
    @Schema(description = "Technician email (used as login)", example = "tech@shop.com", required = true)
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password", required = true)
    private String password;

    @Schema(description = "Display name")
    private String name;

    @Schema(description = "Role label as shown in the owner UI: Technician, Staff, or Pickup Person", example = "Pickup Person")
    private String roleLabel;
}
