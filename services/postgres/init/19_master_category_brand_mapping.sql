-- =============================================================================
-- 19_master_category_brand_mapping.sql
--
-- Adds the master_category_brand_mapping table that links categories to brands
-- (a brand can appear under multiple categories, and a category can host many
-- brands). The table is referenced by MasterCategoryBrandMapping.java in
-- master-data-service and by MasterDeviceSeries.category_brand_id, but was
-- never added to the Postgres init scripts -- it only ever existed in the
-- earlier H2 dev DB where Hibernate's ddl-auto created it on the fly.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS master_category_brand_mapping (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL,
    brand_id    UUID NOT NULL,
    CONSTRAINT uq_category_brand UNIQUE (category_id, brand_id)
);

CREATE INDEX IF NOT EXISTS idx_mcbm_category_id ON master_category_brand_mapping (category_id);
CREATE INDEX IF NOT EXISTS idx_mcbm_brand_id    ON master_category_brand_mapping (brand_id);

COMMIT;
