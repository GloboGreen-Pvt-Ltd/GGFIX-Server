package com.repairshop.saas.common.devicespecs;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One category-specific device attribute. {@link #jsonKey()} is the canonical
 * property name used by the apps and every request/response DTO; the column
 * names in the tables are its snake_case form (storageCapacity → storage_capacity).
 *
 * Colour is not here: every category has one, and it already has its own
 * {@code color} column everywhere.
 */
public enum DeviceSpecKey {
    RAM("ram"),
    STORAGE_CAPACITY("storageCapacity"),
    STORAGE_TYPE("storageType"),
    CASE_SIZE("caseSize"),
    CONNECTIVITY("connectivity"),
    DEVICE_TYPE("deviceType");

    private final String jsonKey;

    DeviceSpecKey(String jsonKey) {
        this.jsonKey = jsonKey;
    }

    public String jsonKey() {
        return jsonKey;
    }

    /**
     * Master-data "Device Configuration" field codes (master_device_config_fields.code,
     * derived by master-data-service from the admin-entered field name) that mean
     * the same attribute. The admin portal already carries Laptop fields named
     * "Available RAM", "Size of Hard Disk / SSD" and "Storage Type"; an active
     * field like these on a category is how master data says that category
     * supports the attribute. Mirrors CONFIG_FIELD_CODES in the Partner App's
     * src/utils/deviceSpecs.js — keep the two lists in step.
     */
    private static final Map<String, DeviceSpecKey> BY_CONFIG_CODE = Map.ofEntries(
            Map.entry("RAM", RAM),
            Map.entry("AVAILABLE_RAM", RAM),
            Map.entry("RAM_SIZE", RAM),
            Map.entry("STORAGE", STORAGE_CAPACITY),
            Map.entry("STORAGE_CAPACITY", STORAGE_CAPACITY),
            Map.entry("INTERNAL_STORAGE", STORAGE_CAPACITY),
            Map.entry("SIZE_OF_HARD_DISK_SSD", STORAGE_CAPACITY),
            Map.entry("SIZE_OF_HARD_DISK", STORAGE_CAPACITY),
            Map.entry("HARD_DISK_SIZE", STORAGE_CAPACITY),
            Map.entry("STORAGE_TYPE", STORAGE_TYPE),
            Map.entry("HARD_DISK_TYPE", STORAGE_TYPE),
            Map.entry("CASE_SIZE", CASE_SIZE),
            Map.entry("DIAL_SIZE", CASE_SIZE),
            Map.entry("CONNECTIVITY", CONNECTIVITY),
            Map.entry("DEVICE_TYPE", DEVICE_TYPE),
            Map.entry("AUDIO_DEVICE_TYPE", DEVICE_TYPE)
    );

    public static Optional<DeviceSpecKey> fromConfigFieldCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(BY_CONFIG_CODE.get(code.trim().toUpperCase(Locale.ROOT)));
    }
}
