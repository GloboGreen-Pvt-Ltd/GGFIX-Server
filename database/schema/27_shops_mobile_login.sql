-- =============================================================================
-- 27_shops_mobile_login.sql
--
-- Adds shops.mobile_password_hash and shops.mobile_otp_code for the shop-mobile
-- login flow (shop owners log in with their registered phone number using
-- either a password or a dev OTP). Entity columns existed but the schema was
-- never migrated, so JPA schema validation rejected the columns as missing.
-- =============================================================================

BEGIN;

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS mobile_password_hash TEXT,
    ADD COLUMN IF NOT EXISTS mobile_otp_code      VARCHAR(8);

COMMIT;
