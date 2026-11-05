-- =============================================================================
-- 06_email_verify_and_shop_meta.sql
--
-- Adds email_verified to users (default FALSE; flipped to TRUE only after the
-- OTP-confirm flow succeeds — the OTP itself is NEVER persisted; it lives in
-- an in-memory store inside auth-service for the duration of the verification
-- window).
--
-- Also adds the extended business-location metadata fields the admin "Add
-- Business Location" modal collects: shop_type, business_type, billing_type,
-- gst_number, taluk, area, street.
--
-- Idempotent.
-- =============================================================================

BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS shop_type     VARCHAR(80),
    ADD COLUMN IF NOT EXISTS business_type VARCHAR(80),
    ADD COLUMN IF NOT EXISTS billing_type  VARCHAR(40),
    ADD COLUMN IF NOT EXISTS gst_number    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS taluk         VARCHAR(120),
    ADD COLUMN IF NOT EXISTS area          VARCHAR(160),
    ADD COLUMN IF NOT EXISTS street        VARCHAR(200);

-- Backfill: any user whose stored email already looks like a real address
-- (contains '@') is considered verified for legacy seed data. New users default
-- to FALSE per the NOT NULL DEFAULT above; the admin must run the OTP flow.
UPDATE users SET email_verified = TRUE WHERE email LIKE '%@%' AND email_verified IS NOT TRUE;

COMMIT;
