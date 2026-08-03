package com.repairshop.saas.masterdata.dto;

import lombok.Data;

@Data
public class RepairServiceRequest {
    private String code;
    private String name;
    private String description;
    private java.util.UUID categoryId;        // -> master_repair_categories.id (main category)
    private java.util.UUID deviceCategoryId;  // -> master_device_categories.id
    private String iconUrl;
    private String iconBase64;
}
