package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Customer auth response with JWT and customer profile")
public class CustomerAuthResponse {

    @Schema(description = "JWT access token (may be null on /customer-me)")
    private String accessToken;

    @Schema(description = "Customer user ID")
    private String userId;

    @Schema(description = "Customer full name")
    private String fullName;

    @Schema(description = "Customer email")
    private String email;

    @Schema(description = "Customer mobile")
    private String mobile;

    @Schema(description = "Roles (always [\"CUSTOMER\"])")
    private List<String> roles;
}
