package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class DeviceSeriesRequest {
    private UUID brandId;
    private UUID categoryBrandId;
    private String name;
    private String slug;
    private Integer sortOrder;
}
