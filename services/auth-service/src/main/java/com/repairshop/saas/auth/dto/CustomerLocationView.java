package com.repairshop.saas.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Normalized location used to seed the customer's chosen location on the site. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerLocationView {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String placeId;
    private String formattedAddress;
    private String pincode;
    private String area;
    private String city;
    private String district;
    private String state;
    private String country;
}
