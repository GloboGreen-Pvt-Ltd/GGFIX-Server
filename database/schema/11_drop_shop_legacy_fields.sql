-- =============================================================================
-- 11_drop_shop_legacy_fields.sql
--
-- Drops shops.phone (redundant with shops.mobile), and three legacy display
-- columns no longer collected by the admin form:
--   hours_text, hero_image_url, description
-- =============================================================================

BEGIN;

ALTER TABLE shops
    DROP COLUMN IF EXISTS phone,
    DROP COLUMN IF EXISTS hours_text,
    DROP COLUMN IF EXISTS hero_image_url,
    DROP COLUMN IF EXISTS description;

COMMIT;
