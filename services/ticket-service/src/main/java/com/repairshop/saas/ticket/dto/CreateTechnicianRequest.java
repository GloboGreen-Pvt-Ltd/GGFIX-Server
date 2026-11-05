package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Create technician (link user to shop as employee)")
public class CreateTechnicianRequest {

    @Schema(description = "User ID from auth (after registering technician)")
    private UUID userId;

    @NotBlank
    @Schema(description = "Display name", required = true)
    private String name;

    @Schema(description = "Email")
    private String email;

    @Schema(description = "Phone")
    private String phone;

    @Schema(description = "Role label e.g. Junior Technician, Senior Technician")
    private String roleLabel;

    @Schema(description = "Salary amount")
    private String salaryAmount;

    @Schema(description = "Salary period e.g. Monthly, Weekly")
    private String salaryPeriod;

    @Schema(description = "ID verification type e.g. Aadhar, PAN (legacy)")
    private String idVerificationType;

    @Schema(description = "ID number (legacy)")
    private String idNumber;

    @Schema(description = "Aadhar card number")
    private String aadharNumber;

    @Schema(description = "Aadhar card front image URL")
    private String aadharFrontUrl;

    @Schema(description = "Aadhar card back image URL")
    private String aadharBackUrl;

    @Schema(description = "PAN card number")
    private String panNumber;

    @Schema(description = "PAN card front image URL")
    private String panFrontUrl;

    @Schema(description = "PAN card back image URL")
    private String panBackUrl;

    @Schema(description = "Daily wage amount")
    private String dailyWage;

    @Schema(description = "Date of birth")
    private LocalDate dateOfBirth;

    @Schema(description = "Date of join")
    private LocalDate dateOfJoin;

    @Schema(description = "Default check-in time e.g. 09:30")
    private LocalTime defaultCheckIn;

    @Schema(description = "Default check-out time e.g. 18:30")
    private LocalTime defaultCheckOut;

    @Schema(description = "Photo URL")
    private String photoUrl;
}
