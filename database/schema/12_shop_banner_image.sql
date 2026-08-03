-- =============================================================================
-- 12_shop_banner_image.sql
--
-- Adds shops.banner_image_url for the "Shop Banner or Visiting Card" photo
-- collected alongside the front image.
-- =============================================================================

BEGIN;

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS banner_image_url VARCHAR(1000);

COMMIT;
