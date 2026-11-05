-- =============================================================================
-- 07_owner_profile_extended.sql
--
-- Adds the extra shop-owner profile fields the admin "Create Shop Owner" form
-- collects (per the reference design):
--   - username (separate login key; defaults to email if omitted)
--   - pin (short owner PIN, NOT the auth password)
--   - shop_control (e.g. "Inventory Only", "Full Control")
--   - secondary_mobile (companion to existing `phone` = primary)
--   - structured personal address: addr_state / district / taluk / area /
--     street / pincode (in addition to legacy free-text `personal_address`)
--
-- Idempotent.
-- =============================================================================

BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pin               VARCHAR(10),
    ADD COLUMN IF NOT EXISTS shop_control      VARCHAR(40),
    ADD COLUMN IF NOT EXISTS secondary_mobile  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS addr_state        VARCHAR(120),
    ADD COLUMN IF NOT EXISTS addr_district     VARCHAR(120),
    ADD COLUMN IF NOT EXISTS addr_taluk        VARCHAR(120),
    ADD COLUMN IF NOT EXISTS addr_area         VARCHAR(160),
    ADD COLUMN IF NOT EXISTS addr_street       VARCHAR(200),
    ADD COLUMN IF NOT EXISTS addr_pincode      VARCHAR(20);

-- Username unique per shop (mirrors how email is unique per shop) but allow
-- multiple NULLs so legacy rows aren't blocked.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_shop_username
    ON users(shop_id, username) WHERE username IS NOT NULL;

-- Backfill: stamp legacy seeded users with their email as the username.
-- email is already UNIQUE(shop_id, email) so this can't violate the new
-- UNIQUE(shop_id, username) constraint.
UPDATE users SET username = email WHERE username IS NULL AND email IS NOT NULL;

COMMIT;
