package com.repairshop.saas.masterdata.dto;

import lombok.Data;

@Data
public class DeviceCategoryRequest {
    private String code;
    private String name;
    private String imageUrl;
    private String imageBase64;
    private Boolean isActive;
}
