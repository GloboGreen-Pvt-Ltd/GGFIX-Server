package com.repairshop.saas.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contract shape (SELL-MODULE-API.md). Nullable fields are sent as null. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnquiryResponse {
    private UUID id;
    private UUID productId;
    private UUID shopId;
    private String kind;
    private String status;
    private String message;
    private Integer quantity;
    private BigDecimal lastOfferAmount;
    private String lastOfferBy;   // SHOP | BUYER | null
    private BigDecimal agreedAmount;
    private String fulfilment;    // PICKUP | SHIPPING | null
    private Instant createdAt;
    private Instant updatedAt;
    private boolean unread;
    private Buyer buyer;
    private ProductSummary product;
    private UUID chatThreadId;
    private List<String> allowedActions;

    /** Detail (and action) responses only; omitted from lists. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Offer> offers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Buyer {
        private UUID userId;
        private String name;
        private String phone; // shop side: only when ACCEPTED or CONFIRMED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductSummary {
        private UUID id;
        private String title;
        private String imageUrl;
        private BigDecimal price;
        private String status;
        private String storageLabel;
        private String ramLabel;
        private String conditionLabel;
        private Integer quantity;
        private List<String> fulfilmentOptions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Offer {
        private UUID id;
        private String author; // SHOP | BUYER | SYSTEM
        private String action; // ENQUIRY | OFFER | COUNTER | ACCEPT | REJECT | CONFIRM | CANCEL
        private BigDecimal amount;
        private String note;
        private Instant createdAt;
    }
}
