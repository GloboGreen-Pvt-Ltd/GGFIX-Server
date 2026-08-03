package com.repairshop.saas.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SavedDeviceResponse {

    private UUID id;
    private UUID customerUserId;
    private UUID categoryId;
    private String categoryCode;
    private UUID brandId;
    private UUID modelId;
    private String modelName;
    private String brandName;
    private String ramLabel;
    private String storageLabel;
    private UUID ramOptionId;
    private UUID storageOptionId;
    private String color;
    private String imei;
    private String note;
    private Boolean isDefault;
    private Instant createdAt;
    private Instant updatedAt;
}
