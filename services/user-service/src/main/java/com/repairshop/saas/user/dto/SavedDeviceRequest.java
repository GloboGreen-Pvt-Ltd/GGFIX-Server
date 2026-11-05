package com.repairshop.saas.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedDeviceRequest {

    private UUID categoryId;
    private String categoryCode; // SMARTPHONE | LAPTOP | SMARTWATCH | TABLET | AUDIO | SPEAKER
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
}
