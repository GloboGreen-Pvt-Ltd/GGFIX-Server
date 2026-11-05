-- =============================================================================
-- 25_marketplace_products_extended.sql
--
-- Brings marketplace_products in sync with MarketplaceProduct.java by adding
-- the columns the entity references for the customer Sell flow and the
-- owner Buy/Sell board. Without these, requests to
--   POST   /marketplace/products
--   GET    /marketplace/products
--   GET    /marketplace/products/{id}
-- fail with HTTP 500 because Hibernate's validate-only schema rejects the
-- entity at startup. (`condition_label`, `color`, `storage_label`, `network`,
-- `image_url`, `extra_image_urls` are already added by 02_customer_app.sql.)
-- =============================================================================

BEGIN;

ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS seller_user_id     UUID,
    ADD COLUMN IF NOT EXISTS ram_option_id      UUID REFERENCES master_ram_options(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS storage_option_id  UUID REFERENCES master_storage_options(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS ram_label          VARCHAR(50),
    ADD COLUMN IF NOT EXISTS imei               VARCHAR(50),
    ADD COLUMN IF NOT EXISTS working_condition  VARCHAR(30),    -- WORKING | DEAD | UNKNOWN
    ADD COLUMN IF NOT EXISTS description_type   VARCHAR(30),    -- DETAILED | SHORT | DEAD_SHORT
    ADD COLUMN IF NOT EXISTS assessment_json    TEXT;

CREATE INDEX IF NOT EXISTS idx_marketplace_products_seller_user
    ON marketplace_products (seller_user_id);

COMMIT;
