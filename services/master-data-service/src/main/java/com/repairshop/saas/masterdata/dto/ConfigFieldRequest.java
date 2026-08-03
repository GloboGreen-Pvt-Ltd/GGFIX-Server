package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ConfigFieldRequest {
    private UUID deviceCategoryId;
    private String name;          // the key, e.g. "Device Processor"
    private Integer sortOrder;
    private Boolean isActive;
    private List<String> options; // values, e.g. ["Intel", "AMD", ...]
}
