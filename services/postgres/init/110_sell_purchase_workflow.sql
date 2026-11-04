-- =============================================================================
-- 110_sell_purchase_workflow.sql
--
-- Partner app purchase of a customer's device after the customer accepts a
-- shop's quote (sell_orders.shop_id set): pickup person (Employee App, via
-- ticket-service /technicians/me/sell-pickups), device receipt, inspection,
-- final price (revision needs the customer's approval), payout (the customer
-- confirms receipt) and completion. order-service also adds these columns at
-- startup (DatabaseSchemaInitializer).
--
-- Idempotent; nullable columns only, no data changes.
-- =============================================================================
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS assigned_pickup_person_id UUID;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_person_name VARCHAR(120);
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_person_phone VARCHAR(30);
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_assigned_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_accepted_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_started_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS picked_up_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS pickup_issue TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS inspection_json TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS inspected_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS revised_price NUMERIC(12, 2);
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS revision_reason TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS revision_status VARCHAR(20);
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS revision_note TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS payout_method VARCHAR(20);
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS payout_account VARCHAR(120);
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS payout_reference VARCHAR(80);
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS payout_amount NUMERIC(12, 2);
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS payout_recorded_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS payout_confirmed_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS payout_dispute_note TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS cancel_reason TEXT;
ALTER TABLE sell_orders ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_sell_orders_pickup_person
    ON sell_orders (assigned_pickup_person_id)
    WHERE assigned_pickup_person_id IS NOT NULL;
