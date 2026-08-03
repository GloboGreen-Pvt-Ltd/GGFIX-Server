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
@Schema(description = "Login request")
public class LoginRequest {

    @NotBlank(message = "Email or mobile is required")
    @Schema(description = "Login identifier. Accepts email or mobile number — the server tries users-table " +
            "(SHOP_OWNER / SUPER_ADMIN / EMPLOYEE) first, then falls back to shop-mobile credentials (SHOP_LOGIN). " +
            "Field name kept as `email` for backward compatibility.",
            example = "owner@example.com or 9876543210",
            required = true)
    private String email;

    @Schema(description = "Password (one of password or otp is required)", example = "********")
    private String password;

    @Schema(description = "One-time password (one of password or otp is required)", example = "123456")
    private String otp;

    @Schema(description = "Shop slug for tenant context (optional if email is globally unique)")
    private String shopSlug;
}
