package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Registration response")
public class RegisterResponse {

    @Schema(description = "User ID")
    private String userId;

    @Schema(description = "Shop ID")
    private String shopId;

    @Schema(description = "Shop slug")
    private String shopSlug;

    @Schema(description = "User email")
    private String email;

    @Schema(description = "Message")
    private String message;
}
