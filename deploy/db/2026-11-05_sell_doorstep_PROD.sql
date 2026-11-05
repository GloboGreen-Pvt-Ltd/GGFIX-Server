-- =============================================================================
-- 2026-11-05_sell_doorstep_PROD.sql  —  NOT APPLIED. Run only with approval.
--
-- Production schema for the doorstep device-purchase flow = migrations
-- 111_sell_pickup_inspection + 112_sell_doorstep_pickup + 113_sell_doorstep_handover
-- in one transaction. Requires 110_sell_purchase_workflow (deploy/db/
-- 2026-11-04_sell_purchase_workflow_PROD.sql) to be applied first.
--
-- Idempotent (ADD COLUMN IF NOT EXISTS), additive only: nullable columns plus
-- pickup_otp_attempts INTEGER NOT NULL DEFAULT 0 (a constant default — no table
-- rewrite on PostgreSQL 11+). No data is changed or deleted.
--
-- Run BEFORE deploying the new order-service / ticket-service jars (their
-- startup initializers add the same columns, but a failed ALTER there is only
-- logged as a warning).
-- =============================================================================
BEGIN;

-- 111
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_inspection_json TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_inspected_at TIMESTAMPTZ;

-- 112
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_otp VARCHAR(6);
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_otp_sent_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_otp_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_otp_verified_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS revision_pickup_note TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS revision_shop_note TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS payout_proof_url VARCHAR(500);

-- 113
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_arrived_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_inspection_started_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS handed_over_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS handover_json TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS inventory_item_id UUID;

COMMIT;

-- Verify (read-only):
-- SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns
--  WHERE table_name = 'sell_orders' AND column_name IN ('pickup_otp', 'pickup_otp_attempts', 'pickup_inspection_json',
--  'pickup_arrived_at', 'pickup_inspection_started_at', 'handed_over_at', 'handover_json', 'inventory_item_id') ORDER BY 1;
-- SELECT to_regclass('public.inventory_items');  -- the stock entry is skipped when this is NULL
