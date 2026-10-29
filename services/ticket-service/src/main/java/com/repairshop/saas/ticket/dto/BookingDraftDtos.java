package com.repairshop.saas.ticket.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Booking-draft wire shapes (/booking-drafts). Field names are a contract with
 * the owner app's New Booking wizard — do not rename.
 */
public final class BookingDraftDtos {

    private BookingDraftDtos() {}

    /** PUT /booking-drafts/{id} body. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpsertRequest {
        /** Version the client last saw; null/0 when creating. */
        private Integer baseVersion;
        private String currentStep;
        private String stepLabel;
        private String customerName;
        private String customerMobile;
        private String deviceSummary;
        private Integer deviceCount;
        /** Opaque wizard state; must be a JSON object. */
        private JsonNode payload;
    }

    /** POST /booking-drafts/{id}/submit body. */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SubmitRequest {
        private List<UUID> ticketIds;
    }

    /** PUT response. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SaveResponse {
        private UUID id;
        private int version;
        private String status;
        private Instant updatedAt;
    }

    /** List row — never carries payload or the full mobile number. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Summary {
        private UUID id;
        private String status;
        private String currentStep;
        private String stepLabel;
        private String customerName;
        private String customerMobileMasked;
        private String deviceSummary;
        private int deviceCount;
        private int version;
        private UUID createdBy;
        private Instant createdAt;
        private Instant updatedAt;
    }

    /** GET /booking-drafts/{id} and submit response. */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Detail {
        private UUID id;
        private String status;
        private String currentStep;
        private String stepLabel;
        private String customerName;
        private String customerMobileMasked;
        private String customerMobile;
        private String deviceSummary;
        private int deviceCount;
        private int version;
        private UUID createdBy;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant submittedAt;
        private JsonNode payload;
        private List<UUID> ticketIds;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PageResponse {
        private List<Summary> content;
        private int page;
        private int size;
        private long totalElements;
    }
}
