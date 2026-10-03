package com.repairshop.saas.common.devicespecs;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which specification schema a device follows. Stored verbatim in the
 * {@code device_category} column of tickets / marketplace_products.
 *
 * MOBILE and TABLET keep their existing representation — master RAM/storage
 * option ids (ram_option_id / storage_option_id) — and take none of the
 * attributes below. The other three use the canonical text attributes.
 */
public enum DeviceSpecCategory {
    MOBILE(EnumSet.noneOf(DeviceSpecKey.class)),
    TABLET(EnumSet.noneOf(DeviceSpecKey.class)),
    LAPTOP(EnumSet.of(DeviceSpecKey.RAM, DeviceSpecKey.STORAGE_CAPACITY, DeviceSpecKey.STORAGE_TYPE)),
    SMARTWATCH(EnumSet.of(DeviceSpecKey.CASE_SIZE, DeviceSpecKey.CONNECTIVITY)),
    AUDIO_DEVICE(EnumSet.of(DeviceSpecKey.DEVICE_TYPE, DeviceSpecKey.CONNECTIVITY));

    private final EnumSet<DeviceSpecKey> baseKeys;

    DeviceSpecCategory(EnumSet<DeviceSpecKey> baseKeys) {
        this.baseKeys = baseKeys;
    }

    /** The attributes this category always takes. A copy — callers may add to it. */
    public Set<DeviceSpecKey> baseKeys() {
        return baseKeys.clone();
    }

    /** MOBILE / TABLET: specs live in ram_option_id / storage_option_id, not in the text attributes. */
    public boolean usesMasterOptionIds() {
        return this == MOBILE || this == TABLET;
    }

    /**
     * master_device_categories.code values seen in the platform, mapped to the
     * schema they follow. The live catalogue uses MOBILE, TABLET, LAPTOP,
     * SMARTWATCHES and AUDIO_DEVICE; the rest are spellings older clients,
     * saved devices and the admin's name-derived codes have produced.
     * Mirrors CATEGORY_ALIASES in the Partner App's src/utils/deviceSpecs.js.
     */
    private static final Map<String, DeviceSpecCategory> ALIASES = Map.ofEntries(
            Map.entry("MOBILE", MOBILE),
            Map.entry("MOBILES", MOBILE),
            Map.entry("SMARTPHONE", MOBILE),
            Map.entry("SMARTPHONES", MOBILE),
            Map.entry("PHONE", MOBILE),
            Map.entry("TABLET", TABLET),
            Map.entry("TABLETS", TABLET),
            Map.entry("LAPTOP", LAPTOP),
            Map.entry("LAPTOPS", LAPTOP),
            Map.entry("SMARTWATCH", SMARTWATCH),
            Map.entry("SMARTWATCHES", SMARTWATCH),
            Map.entry("SMART_WATCH", SMARTWATCH),
            Map.entry("SMART_WATCHES", SMARTWATCH),
            Map.entry("WATCH", SMARTWATCH),
            Map.entry("WATCHES", SMARTWATCH),
            Map.entry("AUDIO_DEVICE", AUDIO_DEVICE),
            Map.entry("AUDIO_DEVICES", AUDIO_DEVICE),
            Map.entry("AUDIO", AUDIO_DEVICE)
    );

    /** Resolve a category code (any casing, spaces or hyphens allowed). */
    public static Optional<DeviceSpecCategory> fromCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String key = code.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_");
        return Optional.ofNullable(ALIASES.get(key));
    }
}
