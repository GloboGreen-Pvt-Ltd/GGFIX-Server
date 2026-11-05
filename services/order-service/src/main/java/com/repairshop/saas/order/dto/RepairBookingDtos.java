package com.repairshop.saas.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public class RepairBookingDtos {

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ServiceRow {
        private UUID repairServiceId;
        private String serviceCode;
        private String serviceName;
        private BigDecimal estimatedPrice;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RepairBookingRequest {
        private UUID shopId;
        private UUID savedDeviceId;
        private UUID brandId;
        private UUID modelId;
        private UUID ramOptionId;
        private UUID storageOptionId;
        private String color;
        private String serviceMode; // ENQUIRY | PICKUP | WALK_IN
        private String frontImageUrl;
        private String backImageUrl;
        private String videoUrl;
        private String issueSummary;
        private List<ServiceRow> services;
        private UUID pickupAddressId;
        private LocalDate pickupDate;
        private LocalTime pickupSlotStart;
        private LocalTime pickupSlotEnd;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepairBookingEventResp {
        private UUID id;
        private String status;
        private String note;
        private String actor;
        // Optional media on a compliance-note step event. The customer's
        // service history renders an inline play button + image thumbnails
        // on the Issue Verified & Updated row when these are present.
        private String audioUrl;
        private List<String> imageUrls;
        private Instant createdAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RepairBookingResponse {
        private UUID id;
        private String bookingNumber;
        private UUID customerUserId;
        private UUID shopId;
        private UUID ticketId;
        private UUID savedDeviceId;
        private UUID brandId;
        private UUID modelId;
        private UUID ramOptionId;
        private UUID storageOptionId;
        private String color;
        private String serviceMode;
        private String frontImageUrl;
        private String backImageUrl;
        private String videoUrl;
        private String issueSummary;
        private BigDecimal estimateAmount;
        private BigDecimal finalAmount;
        private String status;
        private UUID pickupAddressId;
        private LocalDate pickupDate;
        private LocalTime pickupSlotStart;
        private LocalTime pickupSlotEnd;
        private Instant estimatedReadyAt;
        private Integer estimatedDurationHours;
        private Instant estimatedDeliveryAt;
        private String customerApproval;
        private String deviceSecurityType;
        private String devicePin;
        private String missingDamageParts;
        private String technicianName;
        private String technicianCode;
        private List<String> technicianPhotos;
        private UUID assignedPickupPersonId;
        private String pickupPersonName;
        private String pickupPersonPhone;
        private List<ServiceRow> services;
        private List<RepairBookingEventResp> events;
        private Instant createdAt;
        private Instant updatedAt;
        // Resolved customer + pickup address fields. Populated by shop-side
        // endpoints so the owner sees who placed the booking and where to go.
        private String customerName;
        private String customerMobile;
        private String pickupAddressText;
        private String pickupAddressPincode;
        private String pickupAddressMobile;
        private String pickupAddressLabel;
        // Pickup hand-off milestones (migrations 44 + 45). Surface them to
        // the shop-owner pickup detail screen so the "Mark Received" card
        // can render the audited timestamp + staff name without an events
        // walk.
        private Instant reachedShopAt;
        private Instant receivedAtShopAt;
        private UUID receivedByUserId;
        private String receivedByUserName;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RescheduleRequest {
        private LocalDate pickupDate;
        private LocalTime pickupSlotStart;
        private LocalTime pickupSlotEnd;
    }

    // Shop/owner posts a service-timeline status (key matches the customer
    // History steps) with an optional human-readable note.
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ShopStatusRequest {
        private String status;
        private String note;
    }

    // Shop owner assigns (or reassigns) a pickup person to a booking. The id
    // is the technicians.id of the chosen staff member; name + phone are
    // denormalized onto repair_bookings so the customer screen does not need
    // to cross-service lookup.
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AssignPickupPersonRequest {
        private UUID pickupPersonId;
        private String pickupPersonName;
        private String pickupPersonPhone;
    }
}
