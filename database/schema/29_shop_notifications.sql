-- =============================================================================
-- 29_shop_notifications.sql
--
-- Adds the shop-owner-side in-app notification feed. Mirrors
-- customer_notifications but scoped by shop_id (not customer_user_id), so the
-- owner Dashboard bell can show booking / pickup / approval updates without
-- pulling from the customer feed.
--
-- Idempotent. Safe to re-run.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS shop_notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id             UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    booking_id          UUID,
    booking_number      VARCHAR(60),
    status_key          VARCHAR(100),
    title               VARCHAR(200) NOT NULL,
    body                TEXT,
    type                VARCHAR(30) DEFAULT 'bookings',  -- bookings | pickups | system
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_shop_notifications_shop
    ON shop_notifications (shop_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_shop_notifications_unread
    ON shop_notifications (shop_id, is_read);

COMMIT;
