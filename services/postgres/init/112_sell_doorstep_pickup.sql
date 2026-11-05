-- =============================================================================
-- 112_sell_doorstep_pickup.sql
--
-- Employee App doorstep flow for a device purchase pickup (ticket-service
-- SellPickupController): the customer's pickup OTP, the pickup person's revised
-- price (revision_status SHOP_PENDING → SHOP_APPROVED / SHOP_REJECTED, then the
-- customer APPROVED / REJECTED) with notes, and the payment proof photo.
-- ticket-service and order-service also add these columns at startup
-- (DatabaseSchemaInitializer).
--
-- One transaction; idempotent; nullable columns only, no data changes.
-- =============================================================================
BEGIN;

ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_otp VARCHAR(6);
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_otp_sent_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_otp_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_otp_verified_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS revision_pickup_note TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS revision_shop_note TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS payout_proof_url VARCHAR(500);

COMMIT;
