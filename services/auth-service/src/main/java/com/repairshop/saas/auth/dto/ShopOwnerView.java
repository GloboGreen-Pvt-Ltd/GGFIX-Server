package com.repairshop.saas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owner-centric view used by the admin Shop Owner List and detail page.
 * Combines a User row (role=SHOP_OWNER) with the shops that reference it
 * via shops.owner_user_id, plus a derived profile-completeness score.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Shop owner with derived profile completeness and linked shops")
public class ShopOwnerView {
    private UUID id;
    private String name;
    private String email;
    private String phone;            // primary mobile
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
    private String role;
    private Boolean isActive;
    private Boolean emailVerified;
    private Integer profileCompletePercent;
    private Integer sectionsComplete;
    private Integer sectionsTotal;
    private Instant createdAt;
    private List<ShopLocationView> locations;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShopLocationView {
        private UUID id;
        private String name;
        private String slug;
        private String mobile;
        private String address;
        private String district;
        private String state;
        private String pincode;
        private String taluk;
        private String area;
        private String street;
        private String gstNumber;
        private java.math.BigDecimal latitude;
        private java.math.BigDecimal longitude;
        private String frontImageUrl;
        private String bannerImageUrl;
        private String gstCertificateUrl;
        private String udyamCertificateUrl;
        private String pickupFromTime;
        private String pickupToTime;
        private Integer pickupDistanceKm;
        private Boolean pickupEnabled;
        private String workingDays;
        private String openingTime;
        private String closingTime;
        private Boolean isActive;
        /** Persisted JSON snapshot of selected Android / Apple services. NULL until first save. */
        private String serviceCategoriesJson;
        private Integer progressPercent;   // derived: % of documents/fields supplied
        private Instant createdAt;
    }
}
