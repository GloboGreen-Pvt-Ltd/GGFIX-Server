package com.repairshop.saas.marketplace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductResponse {
    private UUID id;
    private UUID shopId;
    private UUID sellerUserId;
    private UUID brandId;
    private UUID modelId;
    private UUID ramOptionId;
    private UUID storageOptionId;
    private String title;
    private String description;
    private String type;
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
}
