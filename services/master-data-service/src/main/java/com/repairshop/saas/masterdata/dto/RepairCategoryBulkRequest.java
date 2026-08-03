package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Create many main categories at once under one device category. */
@Data
public class RepairCategoryBulkRequest {
    private UUID deviceCategoryId;
    private List<String> names;
}
