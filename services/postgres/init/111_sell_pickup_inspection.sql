-- =============================================================================
-- 111_sell_pickup_inspection.sql
--
-- Employee App, device-purchase pickup (ticket-service SellPickupController):
-- at "Device Collected" the pickup person re-checks the device on the same
-- pages the customer filled in when selling (screening, screen / body
-- condition, functional issues, accessories & warranty) and the result is
-- kept next to the customer's own answers, for the shop's inspection.
-- ticket-service also adds these columns at startup (DatabaseSchemaInitializer).
--
-- Idempotent; nullable columns only, no data changes.
-- =============================================================================
BEGIN;

ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_inspection_json TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_inspected_at TIMESTAMPTZ;

COMMIT;
