package com.repairshop.saas.masterdata.dto;

import com.repairshop.saas.masterdata.entity.MasterRepairService;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** A main category with its nested issues — for the admin grouped view. */
@Data
@Builder
public class RepairCategoryGroup {
    private UUID id;
    private String code;
    private String name;
    private UUID deviceCategoryId;
    private Integer sortOrder;
    private Boolean isActive;
    private List<MasterRepairService> issues;
}
