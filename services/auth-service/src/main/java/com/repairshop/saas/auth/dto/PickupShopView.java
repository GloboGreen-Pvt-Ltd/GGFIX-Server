package com.repairshop.saas.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** Customer-facing card for the "pickup shops near me" feed. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickupShopView {
    private UUID id;
    private String name;
    private String slug;
    private String mobile;
    private String address;         // human-readable joined address
    private String city;            // area / taluk for the short header
    private String district;
    private String state;
    private String pincode;
    private String gstNumber;       // exposed so the Deliver Invoice "FROM" block
                                    // can render the shop's GSTIN when present.
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String frontImageUrl;
    private String bannerImageUrl;
    private String pickupFromTime;
    private String pickupToTime;
    private Integer pickupDistanceKm;
    private Double distanceKm;      // computed from caller lat/lng
}
