package com.repairshop.saas.ticket.dto;

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
@Schema(description = "Create or update customer")
public class CustomerRequest {

    @NotBlank
    @Schema(description = "Customer name", required = true)
    private String name;

    @Schema(description = "Email")
    private String email;

    @NotBlank
    @Schema(description = "Phone number", required = true)
    private String phone;

    @Schema(description = "Optional customer ID proof document URL")
    private String idProofUrl;

    @Schema(description = "Concatenated address (legacy field). When the structured fields below are provided they take precedence.")
    private String address;

    @Schema(description = "Door No. / Street") private String addressLine;
    @Schema(description = "Taluk / Locality")  private String locality;
    @Schema(description = "District / City")   private String city;
    @Schema(description = "State")             private String state;
    @Schema(description = "Pin code")          private String pincode;
}
