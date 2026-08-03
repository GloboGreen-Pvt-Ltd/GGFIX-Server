package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Register new platform customer (mobile app)")
public class CustomerRegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255)
    @Schema(description = "Customer full name", example = "Rahul Sharma", required = true)
    private String fullName;

    @Email
    @Size(max = 255)
    @Schema(description = "Customer email (optional)", example = "rahul@example.com")
    private String email;

    @NotBlank(message = "Mobile is required")
    @Size(max = 50)
    @Schema(description = "Customer mobile (unique)", example = "+919876543210", required = true)
    private String mobile;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100)
    @Schema(description = "Password (min 6 chars)", required = true)
    private String password;
}
