-- =============================================================================
-- 20_master_device_series_category_brand.sql
--
-- Brings master_device_series in sync with MasterDeviceSeries.java by adding
-- the category_brand_id (FK to master_category_brand_mapping) and slug columns
-- the JPA repository queries against. Without these columns every request to
--   GET  /master/category-brand-mappings/{id}/series
--   GET  /master/brands/{brandId}/series  (legacy fallback in admin)
--   POST /master/series
-- fails with HTTP 500 because Postgres rejects the generated SQL
-- (column "category_brand_id" does not exist). These columns only ever existed
-- in the legacy H2 dev DB where Hibernate's ddl-auto created them on the fly.
-- =============================================================================

BEGIN;

ALTER TABLE master_device_series
    ADD COLUMN IF NOT EXISTS category_brand_id UUID
    REFERENCES master_category_brand_mapping(id) ON DELETE CASCADE;

ALTER TABLE master_device_series
    ADD COLUMN IF NOT EXISTS slug VARCHAR(180);

-- Widen name to match entity (@Column(length = 150)).
ALTER TABLE master_device_series
    ALTER COLUMN name TYPE VARCHAR(150);

CREATE INDEX IF NOT EXISTS idx_master_device_series_category_brand
    ON master_device_series (category_brand_id);

-- Unique within a (category, brand) pair when slug is set. Postgres treats
-- NULL as distinct, so multiple rows with NULL slug under the same mapping
-- remain allowed (admin currently doesn't send a slug).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_series_cb_slug'
    ) THEN
        ALTER TABLE master_device_series
            ADD CONSTRAINT uq_series_cb_slug UNIQUE (category_brand_id, slug);
    END IF;
END$$;

COMMIT;
