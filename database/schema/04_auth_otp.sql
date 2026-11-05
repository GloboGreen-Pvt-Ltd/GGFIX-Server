-- =============================================================================
-- 04_auth_otp.sql
--
-- Adds the otp_code column to users and customer_users so password-OR-OTP
-- login can be supported. Static dev OTP convention:
--   - SUPER_ADMIN: per-user static OTP (e.g. 801234, 592000)
--   - SHOP_OWNER / TECHNICIAN / CUSTOMER: 123456 by default
--
-- Idempotent: ADD COLUMN IF NOT EXISTS and re-runnable.
-- Apply with:
--   psql -h <host> -U <user> -d <db> -f 04_auth_otp.sql
-- =============================================================================

BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS otp_code VARCHAR(16);

ALTER TABLE customer_users
    ADD COLUMN IF NOT EXISTS otp_code VARCHAR(16);

CREATE INDEX IF NOT EXISTS idx_users_otp_lookup          ON users(email, otp_code);
CREATE INDEX IF NOT EXISTS idx_customer_users_otp_lookup ON customer_users(mobile, otp_code);

COMMIT;
