package com.repairshop.saas.masterdata.dto;

import lombok.Data;

@Data
public class RepairCategoryRequest {
    private String code;
    private java.util.UUID deviceCategoryId;
    private String name;
    private String iconBase64;
    private String description;
    private Integer sortOrder;
    private Boolean isActive;
}
