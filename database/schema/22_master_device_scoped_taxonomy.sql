-- =============================================================================
-- 22_master_device_scoped_taxonomy.sql
--
-- Brings the screening-question / condition-group / functional-issue /
-- device-config tables in sync with their JPA entities. The entities scope
-- each row to an optional device category (Mobile / Laptop / ...), and the
-- device-config tables didn't exist in Postgres at all -- only in the legacy
-- H2 dev DB where ddl-auto materialized them on the fly. Symptoms before
-- this migration:
--   GET  /master/screening-questions  -> 500 (column "device_category_id" missing)
--   POST /master/screening-questions  -> 500
--   GET  /master/condition-groups     -> 500 (column "device_category_id" missing)
--   GET  /master/functional-issues    -> 500 (column "device_category_id" missing)
--   GET  /master/config-fields        -> 500 (table missing)
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- Add device_category_id to existing taxonomy tables. Nullable -> a NULL row
-- is shared across every device category, matching the entity comment.
-- ---------------------------------------------------------------------------
ALTER TABLE master_screening_questions
    ADD COLUMN IF NOT EXISTS device_category_id UUID
    REFERENCES master_device_categories(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_master_screening_questions_category
    ON master_screening_questions (device_category_id);

ALTER TABLE master_condition_groups
    ADD COLUMN IF NOT EXISTS device_category_id UUID
    REFERENCES master_device_categories(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_master_condition_groups_category
    ON master_condition_groups (device_category_id);

ALTER TABLE master_functional_issues
    ADD COLUMN IF NOT EXISTS device_category_id UUID
    REFERENCES master_device_categories(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_master_functional_issues_category
    ON master_functional_issues (device_category_id);

-- ---------------------------------------------------------------------------
-- Device config fields / options. Used by the admin "Categories -> config
-- fields" UI to define attributes like "Device Processor", "Available RAM"
-- and their selectable values per device category.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS master_device_config_fields (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_category_id  UUID REFERENCES master_device_categories(id) ON DELETE CASCADE,
    code                VARCHAR(100),
    name                VARCHAR(150) NOT NULL,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    is_active           BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_device_config_field_category
    ON master_device_config_fields (device_category_id);

CREATE TABLE IF NOT EXISTS master_device_config_options (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    field_id        UUID NOT NULL REFERENCES master_device_config_fields(id) ON DELETE CASCADE,
    -- "value" is reserved in some dialects; entity maps to option_value
    option_value    VARCHAR(255) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_device_config_option_field
    ON master_device_config_options (field_id);

-- updated_at triggers (set_updated_at() defined in 01_schema.sql)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_master_device_config_fields_updated_at') THEN
        CREATE TRIGGER trg_master_device_config_fields_updated_at
            BEFORE UPDATE ON master_device_config_fields
            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_master_device_config_options_updated_at') THEN
        CREATE TRIGGER trg_master_device_config_options_updated_at
            BEFORE UPDATE ON master_device_config_options
            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    END IF;
END$$;

COMMIT;
