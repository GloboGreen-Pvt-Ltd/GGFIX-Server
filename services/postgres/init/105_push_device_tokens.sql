-- =============================================================================
-- 105_push_device_tokens.sql
--
-- Push tokens for the three NearGFix apps. One row per app install: each app
-- registers its token after sign-in (order-service POST /push-tokens, or the
-- Partner app's POST /shop/push-tokens) and removes it on sign-out.
--
--   app       PARTNER  — addressed by shop_id (owner phone + SHOP-login devices)
--             CUSTOMER — addressed by user_id
--             EMPLOYEE — addressed by user_id (technician / staff / pickup person)
--   provider  FCM  — native Android FCM token, sent with the Firebase Admin SDK
--             EXPO — Expo push token (iOS / Expo-token builds), sent via Expo
--
-- token is UNIQUE because it identifies the install, not the person: when a
-- device signs in to another account or shop the row is re-pointed (upsert).
--
-- No FK on shop_id / user_id: the writer is common-notify (plain JDBC, used
-- from several services) and a dangling token is harmless — it simply matches
-- nobody. Tokens FCM / Expo report as unregistered are deleted on send.
--
-- shop_notifications.type has no CHECK constraint (29_shop_notifications.sql),
-- so the new feed types ('enquiry', 'sell', 'buy') need no change here.
--
-- Idempotent. Apply BEFORE deploying order-service / ticket-service /
-- marketplace-service. Numbered 105 because 104 is taken by pending branches
-- (104_booking_drafts, 104_invoice_signatures); it only adds a new table, so
-- its place in the init order doesn't matter.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS push_device_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       TEXT NOT NULL UNIQUE,
    app         TEXT NOT NULL CHECK (app IN ('PARTNER', 'CUSTOMER', 'EMPLOYEE')),
    provider    TEXT NOT NULL CHECK (provider IN ('FCM', 'EXPO')),
    user_id     UUID,
    shop_id     UUID,
    login_type  TEXT,                -- OWNER | SHOP (JWT loginScope), Partner only
    platform    TEXT,                -- android | ios
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (app <> 'PARTNER' OR shop_id IS NOT NULL),
    CHECK (app = 'PARTNER' OR user_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_push_device_tokens_shop
    ON push_device_tokens (shop_id) WHERE app = 'PARTNER';
CREATE INDEX IF NOT EXISTS idx_push_device_tokens_user
    ON push_device_tokens (app, user_id);

COMMIT;
