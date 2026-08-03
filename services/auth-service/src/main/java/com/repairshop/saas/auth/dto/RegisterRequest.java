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
@Schema(description = "Register new shop owner / user")
public class RegisterRequest {

    @NotBlank(message = "Shop name is required")
    @Size(max = 255)
    @Schema(description = "Shop name", example = "Green Mobiles", required = true)
    private String shopName;

    @NotBlank(message = "Shop slug is required")
    @Size(max = 100)
    @Schema(description = "Unique shop slug (URL-friendly)", example = "green-mobiles", required = true)
    private String shopSlug;

    @NotBlank(message = "Email is required")
    @Email
    @Schema(description = "Owner email", example = "owner@greenmobiles.com", required = true)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100)
    @Schema(description = "Password (min 8 chars)", required = true)
    private String password;

    @Size(max = 255)
    @Schema(description = "Owner display name")
    private String name;
}
