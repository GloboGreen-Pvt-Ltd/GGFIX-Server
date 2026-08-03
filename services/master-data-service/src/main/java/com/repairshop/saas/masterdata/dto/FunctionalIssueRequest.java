package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class FunctionalIssueRequest {
    private String code;
    private UUID deviceCategoryId;
    private String name;
    private String iconUrl;
    private String iconBase64;
    private BigDecimal priceImpact;
    private Integer sortOrder;
    private Boolean isActive;
}
