-- =============================================================================
-- 21_master_models_slug_category.sql
--
-- Brings master_models in sync with MasterModel.java. The entity declares
--   slug      VARCHAR(180)
--   category  VARCHAR(50)
-- plus a UNIQUE (series_id, slug) constraint (uq_model_series_slug).
-- 01_schema.sql + 02_customer_app.sql added series_id / category_id /
-- image_url / image_base64 but never these two scalar columns, so every
-- JPA select on master_models (e.g. GET /master/series/{id}/models) fails
-- with `column "slug" does not exist` -> HTTP 500. These columns only
-- ever existed in the legacy H2 dev DB where ddl-auto created them.
-- =============================================================================

BEGIN;

ALTER TABLE master_models
    ADD COLUMN IF NOT EXISTS slug VARCHAR(180);

ALTER TABLE master_models
    ADD COLUMN IF NOT EXISTS category VARCHAR(50);

-- Unique within a series when slug is set. Postgres treats NULL as distinct,
-- so multiple rows with NULL slug under the same series remain allowed
-- (admin currently doesn't send a slug).
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
