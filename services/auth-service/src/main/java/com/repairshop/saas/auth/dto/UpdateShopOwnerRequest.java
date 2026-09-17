package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial-update payload for an existing shop owner. All fields are optional;
 * only non-null fields are applied to the persisted row. Password is optional
 * — only set when the admin wants to rotate it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Partial update for a shop owner profile")
public class UpdateShopOwnerRequest {
    private String name;
    private String email;
    private String password;          // optional rotation
    private String phone;
    private String secondaryMobile;
    private String avatarUrl;
    private String idProofUrl;
    private String personalAddress;
    private String addrState;
    private String addrDistrict;
    private String addrTaluk;
    private String addrArea;
    private String addrStreet;
    private String addrPincode;
    private String otpCode;
}
