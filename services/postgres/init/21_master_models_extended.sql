-- =============================================================================
-- 21_master_models_extended.sql
--
-- Brings master_models in sync with MasterModel.java by adding the slug and
-- category columns and the uq_model_series_slug UNIQUE constraint declared on
-- the entity's @Table annotation. Without these, any request that touches
-- master_models (GET /master/models, POST /master/models, GET .../series, etc.)
-- fails with HTTP 500 because Hibernate's validate-only ddl mode rejects the
-- entity at startup or the generated SQL references missing columns.
-- These columns only ever existed in the legacy H2 dev DB.
-- =============================================================================

BEGIN;

ALTER TABLE master_models
    ADD COLUMN IF NOT EXISTS slug VARCHAR(180);

-- Free-form classification label for the UI (e.g. DEVICE / SPARE_PART).
ALTER TABLE master_models
    ADD COLUMN IF NOT EXISTS category VARCHAR(50);

-- Unique within a (series_id, slug) pair. Postgres treats NULL as distinct, so
-- multiple rows with NULL slug or NULL series_id remain allowed.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_model_series_slug'
    ) THEN
        ALTER TABLE master_models
            ADD CONSTRAINT uq_model_series_slug UNIQUE (series_id, slug);
    END IF;
END$$;

COMMIT;
