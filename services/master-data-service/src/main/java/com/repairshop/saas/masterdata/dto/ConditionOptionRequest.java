package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ConditionOptionRequest {
    private UUID groupId;
    private String label;
    private String iconUrl;
    private String iconBase64;
    private BigDecimal priceImpact;
    private Integer sortOrder;
}
