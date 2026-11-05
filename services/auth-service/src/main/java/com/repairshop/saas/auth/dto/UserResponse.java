package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User in a shop (for admin user management)")
public class UserResponse {

    @Schema(description = "User ID")
    private UUID id;

    @Schema(description = "Email (login)")
    private String email;

    @Schema(description = "Display name")
    private String name;

    @Schema(description = "Role: SHOP_OWNER, TECHNICIAN, CUSTOMER, SUPER_ADMIN")
    private String role;

    @Schema(description = "Active flag")
    private Boolean isActive;

    @Schema(description = "Shop ID")
    private UUID shopId;

    @Schema(description = "Shop name")
    private String shopName;
}
