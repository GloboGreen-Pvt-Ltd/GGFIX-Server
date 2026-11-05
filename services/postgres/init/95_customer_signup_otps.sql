-- 95_customer_signup_otps.sql
--
-- Holds the OTP issued to a mobile number that is NOT yet a customer, so the
-- customer app's Create Account screen can verify the number BEFORE the account
-- row exists.
--
-- Why a separate table rather than customer_users.otp_code: that column is on
-- the account row, and at sign-up time there is no account row. The alternative
-- - inserting a half-built customer_users row and filling it in after the code
-- is verified - would put unverified, nameless rows behind the `mobile` UNIQUE
-- constraint, where they would block the real sign-up on any abandoned attempt.
-- This table is throwaway state keyed by mobile; the account is created only
-- once the code checks out.
--
-- One row per mobile (PK), upserted on resend, deleted when consumed by
-- /auth/customer-register.

CREATE TABLE IF NOT EXISTS customer_signup_otps (
    mobile      VARCHAR(50) PRIMARY KEY,
    otp_code    VARCHAR(16)  NOT NULL,
    -- Wrong-code counter, reset on every resend. Caps brute force on a 6-digit
    -- code that no logged-in session protects (this endpoint is pre-auth).
    attempts    INT          NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Sweeping expired rows is a range scan over expires_at, not a PK lookup.
CREATE INDEX IF NOT EXISTS idx_customer_signup_otps_expires_at
    ON customer_signup_otps(expires_at);
