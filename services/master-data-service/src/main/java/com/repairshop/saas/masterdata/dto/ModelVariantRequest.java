package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ModelVariantRequest {
    private UUID modelId;
    private UUID ramOptionId;
    private UUID storageOptionId;
    private UUID colorId;
    private BigDecimal referencePrice;
}
