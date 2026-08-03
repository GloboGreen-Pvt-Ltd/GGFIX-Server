-- =============================================================================
-- 22_master_device_config.sql
--
-- Creates the master_device_config_fields and master_device_config_options
-- tables backing MasterDeviceConfigField / MasterDeviceConfigOption in
-- master-data-service. Per-category attribute keys (e.g. for "Laptop":
-- "Device Processor", "Available RAM") live in *_fields; their selectable
-- values (Intel/AMD/Apple Silicon/…) live in *_options.
--
-- Without these tables every request that hits the device-config endpoints
-- (e.g. GET /master/device-categories/{id}/config-fields) fails with HTTP 500
-- because the JPA repositories query non-existent tables.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS master_device_config_fields (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_category_id  UUID REFERENCES master_device_categories(id) ON DELETE CASCADE,
    code                VARCHAR(100),
    name                VARCHAR(150) NOT NULL,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    is_active           BOOLEAN DEFAULT true
);

CREATE INDEX IF NOT EXISTS idx_device_config_field_category
    ON master_device_config_fields (device_category_id);

CREATE TABLE IF NOT EXISTS master_device_config_options (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_id            UUID NOT NULL REFERENCES master_device_config_fields(id) ON DELETE CASCADE,
    option_value        VARCHAR(255) NOT NULL,
    sort_order          INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_device_config_option_field
    ON master_device_config_options (field_id);

COMMIT;
