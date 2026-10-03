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

    // Category-specific device specifications. Sending any attribute requires
    // deviceCategory; one that doesn't belong to the category is a 400.
    private String deviceCategory;   // MOBILE | TABLET | LAPTOP | SMARTWATCH | AUDIO_DEVICE
    private String ram;
    private String storageCapacity;
    private String storageType;      // HDD | SATA_SSD | NVME_SSD
    private String caseSize;
    private String connectivity;
    private String deviceType;
    private String network;
    private String imei;
    private String workingCondition;
    private String descriptionType;
    private String imageUrl;
    private List<String> extraImageUrls;
    private String assessmentJson;
}
