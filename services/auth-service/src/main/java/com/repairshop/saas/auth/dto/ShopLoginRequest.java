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
@Schema(description = "Shop-mobile login request. Authenticates against shops.mobile (single-shop session).")
public class ShopLoginRequest {

    @NotBlank(message = "Mobile is required")
    @Schema(description = "Shop mobile number (10-digit; +91 prefix tolerated)", example = "9876543210", required = true)
    private String mobile;

    @Schema(description = "Mobile password (one of password or otp is required)", example = "********")
    private String password;

    @Schema(description = "One-time password (one of password or otp is required)", example = "123456")
    private String otp;
}
