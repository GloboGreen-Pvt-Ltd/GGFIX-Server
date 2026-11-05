package com.repairshop.saas.masterdata.dto;

import lombok.Data;

@Data
public class BannerRequest {
    private String title;
    private String imageUrl;
    private String imageBase64;
    private String linkTarget;
    private Integer sortOrder;
    private Boolean isActive;
}
