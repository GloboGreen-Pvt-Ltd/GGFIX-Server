package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ConditionGroupRequest {
    private String code;
    private UUID deviceCategoryId;
    private String name;
    private String flow;
    private Integer sortOrder;
}
