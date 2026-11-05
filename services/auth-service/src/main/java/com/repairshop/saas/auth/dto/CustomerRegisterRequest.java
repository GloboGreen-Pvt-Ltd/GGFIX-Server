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

    /**
     * Sign-up code from /auth/customer/signup/otp/send. The app's Create Account
     * flow is OTP-only and sends this instead of a password; it is consumed here
     * so an account can only be made for a number the caller verified.
     */
    @Size(max = 16)
    @Schema(description = "Sign-up OTP for the mobile (required when no password is sent)", example = "123456")
    private String otp;

    /**
     * Optional, and no longer collected by the customer app - customers sign in
     * with an OTP. Kept for older clients that still post one; when absent the
     * account is created with no password until "forgot password" sets one.
     */
    @Size(min = 6, max = 100)
    @Schema(description = "Password (min 6 chars). Optional — send `otp` instead.")
    private String password;
}
