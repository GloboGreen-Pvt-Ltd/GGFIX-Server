-- =============================================================================
-- 54_shops_mobile_login.sql
--
-- Adds per-shop mobile-number login. A shop's mobile number can authenticate
-- directly into that one shop (single-shop session), separate from the owner
-- account which can access all shops linked to it.
--
--   mobile_password_hash : bcrypt-hashed password tied to shops.mobile. Owners
--                          set this from the Edit Business Location form (or
--                          via PATCH /auth/shop-owners/{ownerId}/locations/{shopId}).
--                          NULL means password login is not configured for that
--                          shop — OTP login still works.
--   mobile_otp_code      : dev-mode OTP mirroring the owner-user pattern
--                          (users.otp_code). Defaults to '123456' so the flow
--                          is testable without an SMS gateway. Production
--                          should rotate this on each request via a real OTP
--                          service.
--
-- Also indexes shops.mobile for the login lookup. Not unique because phone
-- numbers can legitimately repeat (test data, transferred numbers) — the
-- login resolver matches the FIRST shop with that mobile, so production
-- should enforce uniqueness at the application layer when assigning numbers.
-- =============================================================================

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS mobile_password_hash TEXT,
    ADD COLUMN IF NOT EXISTS mobile_otp_code      VARCHAR(8) DEFAULT '123456';

UPDATE shops SET mobile_otp_code = '123456' WHERE mobile_otp_code IS NULL;

CREATE INDEX IF NOT EXISTS idx_shops_mobile ON shops(mobile);
