-- =============================================================================
-- 113_sell_doorstep_handover.sql
--
-- Doorstep device purchase, after 111 / 112: when the pickup person arrived
-- (recorded at PIN verification) and opened the inspection, the hand-over at
-- the shop (Employee App) and the owner's verified receipt (IMEI / order /
-- condition), and the stock entry made for the purchased device
-- (inventory_items.id, one per sell order). ticket-service and order-service
-- also add these columns at startup (DatabaseSchemaInitializer).
--
-- One transaction; idempotent; nullable columns only, no data changes.
-- =============================================================================
BEGIN;

ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_arrived_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_inspection_started_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS handed_over_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS handover_json TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS inventory_item_id UUID;

COMMIT;
