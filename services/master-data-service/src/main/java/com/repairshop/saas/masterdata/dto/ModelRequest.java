package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ModelRequest {
    private UUID brandId;
    private UUID categoryId;
    private UUID seriesId;
    private String name;
    private String slug;
    private String imageUrl;
    private String imageBase64;
    private String category;
}
