-- =============================================================================
-- 08_drop_unused_profile_fields.sql
--
-- Removes fields that the product no longer collects:
--   users:  username, pin
--   shops:  shop_type, business_type, billing_type, city
--
-- Username uniqueness index must be dropped before the column. Idempotent.
-- =============================================================================

BEGIN;

DROP INDEX IF EXISTS uq_users_shop_username;

ALTER TABLE users
    DROP COLUMN IF EXISTS username,
    DROP COLUMN IF EXISTS pin;

ALTER TABLE shops
    DROP COLUMN IF EXISTS shop_type,
    DROP COLUMN IF EXISTS business_type,
    DROP COLUMN IF EXISTS billing_type,
    DROP COLUMN IF EXISTS city;

COMMIT;
