package com.repairshop.saas.masterdata.dto;

import lombok.Data;

@Data
public class BrandRequest {
    private String name;
    private String imageUrl;
    private String imageBase64;
}
