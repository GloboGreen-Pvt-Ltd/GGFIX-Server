package com.repairshop.saas.common.devicespecs;

/**
 * The category-specific attributes of one device, in their canonical form.
 *
 * Every field is nullable. Values are the STORED form, not display labels:
 * ram "16GB", storageCapacity "512GB", storageType "NVME_SSD", caseSize "44MM",
 * connectivity "GPS_CELLULAR", deviceType "TWS_EARBUDS". The apps turn them
 * back into "16 GB" / "NVMe SSD" / "44 mm" for display.
 */
public record DeviceSpecs(
        String deviceCategory,
        String ram,
        String storageCapacity,
        String storageType,
        String caseSize,
        String connectivity,
        String deviceType
) {

    public static final DeviceSpecs EMPTY = new DeviceSpecs(null, null, null, null, null, null, null);

    public String get(DeviceSpecKey key) {
        return switch (key) {
            case RAM -> ram;
            case STORAGE_CAPACITY -> storageCapacity;
            case STORAGE_TYPE -> storageType;
            case CASE_SIZE -> caseSize;
            case CONNECTIVITY -> connectivity;
            case DEVICE_TYPE -> deviceType;
        };
    }

    /** True when any category-specific attribute (not the category itself) carries a value. */
    public boolean hasAnyAttribute() {
        for (DeviceSpecKey key : DeviceSpecKey.values()) {
            if (!isBlank(get(key))) return true;
        }
        return false;
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
