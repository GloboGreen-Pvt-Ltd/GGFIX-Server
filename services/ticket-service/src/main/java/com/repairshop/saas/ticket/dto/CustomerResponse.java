package com.repairshop.saas.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Customer response")
public class CustomerResponse {

    @Schema(description = "Customer ID")
    private UUID id;

    @Schema(description = "Customer name")
    private String name;

    @Schema(description = "Email")
    private String email;

    @Schema(description = "Phone number")
    private String phone;

    @Schema(description = "Mobile number alias for phone")
    private String mobile;

    @Schema(description = "Optional customer ID proof document URL")
    private String idProofUrl;

    @Schema(description = "Address (legacy single-string; structured components below)")
    private String address;

    @Schema(description = "Address line — door no / street")
    private String addressLine;

    @Schema(description = "Locality / area (legacy alias of `area`; kept for old readers)")
    private String locality;

    @Schema(description = "City / district (legacy alias of `district`; kept for old readers)")
    private String city;

    @Schema(description = "District — new structured field from customer_addresses.district (migration 55)")
    private String district;

    @Schema(description = "Taluk / sub-district — new structured field from customer_addresses.taluk (migration 55)")
    private String taluk;

    @Schema(description = "Area / locality — new structured field from customer_addresses.area (migration 55)")
    private String area;

    @Schema(description = "State")
    private String state;

    @Schema(description = "Pincode")
    private String pincode;

    @Schema(description = "Created at")
    private Instant createdAt;

    @Schema(description = "Number of bookings for this customer in the current shop")
    private Long bookingCount;

    @Schema(description = "Most recent booking date for this customer in the current shop")
    private Instant lastBookingAt;

    @Schema(description = "Source of this row: 'shop' (per-shop customers table) or 'platform' (customer_users platform-wide table)")
    private String source;

    @Schema(description = "Platform customer_users.id when this row comes from or is linked to a platform customer; null otherwise")
    private UUID platformUserId;
}
