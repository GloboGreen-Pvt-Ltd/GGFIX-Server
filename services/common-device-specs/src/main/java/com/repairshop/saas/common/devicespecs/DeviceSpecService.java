package com.repairshop.saas.common.devicespecs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Entry point for services: validates a request's device attributes against
 * the category rules plus whatever master data explicitly enables.
 *
 * "Explicitly enables" means either
 *   - an active Device Configuration field (master_device_config_fields) on the
 *     device's category whose code names the attribute — e.g. an admin adding a
 *     "Storage" field to Smartwatch makes storageCapacity valid for smartwatches; or
 *   - the selected model's own master_models.ram_storage listing real sizes
 *     ("32 GB", "8 GB + 256 GB") — the model ships in storage variants.
 * Without either, a Smartwatch or Audio Device request carrying RAM/storage
 * values is rejected.
 */
@Component
public class DeviceSpecService {

    private static final Logger log = LoggerFactory.getLogger(DeviceSpecService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public DeviceSpecService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** See {@link DeviceSpecValidator#validate}. */
    public DeviceSpecs resolve(DeviceSpecs raw, UUID modelId, UUID ramOptionId, UUID storageOptionId) {
        return DeviceSpecValidator.validate(raw,
                category -> masterSupportedKeys(category, modelId),
                ramOptionId != null,
                storageOptionId != null);
    }

    Set<DeviceSpecKey> masterSupportedKeys(DeviceSpecCategory category, UUID modelId) {
        Set<DeviceSpecKey> out = EnumSet.noneOf(DeviceSpecKey.class);
        // A failed lookup must not take the write down with it: the only effect
        // of a miss is that an optional, master-enabled attribute is refused.
        try {
            jdbc.query(
                    "SELECT c.code AS category_code, f.code AS field_code"
                            + " FROM master_device_config_fields f"
                            + " JOIN master_device_categories c ON c.id = f.device_category_id"
                            + " WHERE f.is_active IS NOT FALSE",
                    rs -> {
                        if (DeviceSpecCategory.fromCode(rs.getString("category_code")).orElse(null) == category) {
                            DeviceSpecKey.fromConfigFieldCode(rs.getString("field_code")).ifPresent(out::add);
                        }
                    });
        } catch (Exception e) {
            log.warn("Device spec: could not read master_device_config_fields: {}", e.getMessage());
        }
        if (modelId != null) {
            try {
                List<String> rows = jdbc.queryForList(
                        "SELECT CAST(ram_storage AS VARCHAR) FROM master_models WHERE id = ?", String.class, modelId);
                for (String json : rows) out.addAll(keysFromRamStorage(json));
            } catch (Exception e) {
                log.warn("Device spec: could not read master_models.ram_storage for {}: {}", modelId, e.getMessage());
            }
        }
        return out;
    }

    /**
     * ["8 GB + 256 GB"] → RAM + STORAGE_CAPACITY; ["32 GB"] → STORAGE_CAPACITY.
     * Entries that aren't sizes (the catalogue has a watch whose ram_storage
     * holds strap names) contribute nothing.
     */
    static Set<DeviceSpecKey> keysFromRamStorage(String json) {
        Set<DeviceSpecKey> out = EnumSet.noneOf(DeviceSpecKey.class);
        if (json == null || json.isBlank()) return out;
        List<String> entries;
        try {
            entries = JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return out;
        }
        for (String entry : entries) {
            if (entry == null) continue;
            if (entry.contains("+")) {
                String[] parts = entry.split("\\+", 2);
                if (DeviceSpecNormalizer.capacityOrNull(parts[0]) != null) out.add(DeviceSpecKey.RAM);
                if (DeviceSpecNormalizer.capacityOrNull(parts[1]) != null) out.add(DeviceSpecKey.STORAGE_CAPACITY);
            } else if (DeviceSpecNormalizer.capacityOrNull(entry) != null) {
                out.add(DeviceSpecKey.STORAGE_CAPACITY);
            }
        }
        return out;
    }
}
