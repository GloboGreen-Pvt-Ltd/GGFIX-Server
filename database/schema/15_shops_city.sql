-- =============================================================================
-- 15_shops_city.sql
--
-- Adds the missing `city` column to shops. Entity `Shop.city` exists in
-- shop-service but the column was never created, causing JPA schema
-- validation to fail at boot.
-- =============================================================================

BEGIN;

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS city VARCHAR(255);

COMMIT;
