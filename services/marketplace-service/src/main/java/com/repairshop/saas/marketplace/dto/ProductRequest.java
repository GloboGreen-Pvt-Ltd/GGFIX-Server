package com.repairshop.saas.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {
    private UUID shopId;
    private UUID sellerUserId;
    private UUID brandId;
    private UUID modelId;
    private UUID ramOptionId;
    private UUID storageOptionId;

    @NotBlank
    private String title;

    private String description;

    private String type; // SELL | BUY

    private BigDecimal price;

    private String status;

    private String conditionLabel;
    private String color;
    private String ramLabel;
    private String storageLabel;
    private String network;
    private String imei;
    private String workingCondition;
    private String descriptionType;
    private String imageUrl;
    private List<String> extraImageUrls;
    private String assessmentJson;

    // ---- Sell module ----
    private String listingType;        // DEVICE | SPARE_PART | ACCESSORY
    private String itemCondition;      // NEW | USED | REFURBISHED
    private Integer quantity;
    private Boolean negotiable;
    private List<String> fulfilmentOptions; // PICKUP | SHIPPING
    private String videoUrl;
    private String clientRef;          // idempotency key (<= 64)
    private UUID batchId;
}
