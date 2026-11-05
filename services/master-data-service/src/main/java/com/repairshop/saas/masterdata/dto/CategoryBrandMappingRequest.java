package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CategoryBrandMappingRequest {
    private UUID categoryId;
    private UUID brandId;
}
