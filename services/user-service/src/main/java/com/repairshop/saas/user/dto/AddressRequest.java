package com.repairshop.saas.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressRequest {

    private String label;
    private String fullName;
    private String mobile;
    private String pincode;
    private String locality;       // LEGACY mirror of `area` — API dual-writes
    private String area;           // canonical "Area" field on the customer-app form
    private String addressLine;    // a.k.a. "Door no. / Street"
    private String city;           // legacy — kept for backward-compat readers
    private String district;
    private String taluk;
    private String state;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean isDefault;
}
