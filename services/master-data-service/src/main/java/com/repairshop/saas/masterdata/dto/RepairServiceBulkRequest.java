package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Create many issues at once under one (device category, main category). */
@Data
public class RepairServiceBulkRequest {
    private UUID deviceCategoryId;
    private UUID categoryId;
    private List<String> names;
}
