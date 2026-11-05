-- =============================================================================
-- 14_shop_pickup_options.sql
--
-- Per-shop pickup service window — what hours the shop offers customer
-- pickup and the max radius it serves. Customers use these to discover
-- pickup-enabled shops nearby.
--
-- Times stored as free-form strings (e.g. "01:00 PM" or "13:00") to match
-- the admin/owner UI directly — JPA layer doesn't need to parse.
-- =============================================================================

BEGIN;

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS pickup_from_time    VARCHAR(16),
    ADD COLUMN IF NOT EXISTS pickup_to_time      VARCHAR(16),
    ADD COLUMN IF NOT EXISTS pickup_distance_km  INTEGER,
    ADD COLUMN IF NOT EXISTS pickup_enabled      BOOLEAN NOT NULL DEFAULT FALSE;

-- Customer "find pickup shops nearby" queries filter on pickup_enabled.
CREATE INDEX IF NOT EXISTS idx_shops_pickup_enabled ON shops(pickup_enabled) WHERE pickup_enabled = TRUE;

COMMIT;
