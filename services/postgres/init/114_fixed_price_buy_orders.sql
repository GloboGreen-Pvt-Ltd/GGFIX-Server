-- Fixed-price Buy flow (marketplace-service FixedPriceOrderService).
-- Additive only: existing marketplace_orders rows are untouched (product_id NULL).
ALTER TABLE marketplace_orders
    ADD COLUMN IF NOT EXISTS customer_user_id       UUID,
    ADD COLUMN IF NOT EXISTS product_id             UUID REFERENCES marketplace_products(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS quantity               INT,
    ADD COLUMN IF NOT EXISTS unit_price             DECIMAL(12, 2),
    ADD COLUMN IF NOT EXISTS fulfilment_type        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS payment_method         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS payment_status         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS payment_ref            VARCHAR(120),
    ADD COLUMN IF NOT EXISTS delivery_address       TEXT,
    ADD COLUMN IF NOT EXISTS customer_phone         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS idempotency_key        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS assigned_staff_user_id UUID,
    ADD COLUMN IF NOT EXISTS handover_otp           VARCHAR(6),
    ADD COLUMN IF NOT EXISTS stock_released         BOOLEAN,
    ADD COLUMN IF NOT EXISTS photos_json            TEXT,
    ADD COLUMN IF NOT EXISTS note                   TEXT,
    ADD COLUMN IF NOT EXISTS verified_imei          VARCHAR(40);

CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_orders_idem
    ON marketplace_orders(customer_user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_marketplace_orders_customer_user ON marketplace_orders(customer_user_id);
CREATE INDEX IF NOT EXISTS idx_marketplace_orders_staff ON marketplace_orders(assigned_staff_user_id);

CREATE TABLE IF NOT EXISTS marketplace_order_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
    status        VARCHAR(50) NOT NULL,
    actor_user_id UUID,
    actor_role    VARCHAR(20),
    note          TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_marketplace_order_events_order ON marketplace_order_events(order_id);
