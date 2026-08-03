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
@Schema(description = "Provision an employee login. At least one of email or phone is required — "
        + "employees are usually keyed by mobile, in which case login is mobile + OTP.")
public class RegisterTechnicianRequest {

    @Schema(description = "Employee email (optional login identifier)", example = "tech@shop.com")
    private String email;

    @Schema(description = "Password (optional — omit for OTP-only login)")
    private String password;

    @Schema(description = "Employee mobile number — primary login identifier for staff", example = "7603856616")
    private String phone;

    @Schema(description = "Login OTP (optional — defaults to 123456 when omitted)", example = "123456")
    private String otp;

    @Schema(description = "Display name")
    private String name;

    @Schema(description = "Role label as shown in the owner UI: Technician, Staff, or Pickup Person", example = "Pickup Person")
    private String roleLabel;
}
