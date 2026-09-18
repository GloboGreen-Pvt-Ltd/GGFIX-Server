package com.repairshop.saas.masterdata.entity;

/**
 * The three customer-facing site menus a {@link MasterCategoryMenu} tile can
 * belong to. Matches the CHECK constraint on migration 97's category_type
 * column — a value added here must be added there too.
 */
public enum CategoryMenuType {
    REPAIR,
    SELL,
    BUY
}
