-- =============================================================================
-- 107_listing_sell_order_link.sql
--
-- Customer Sell Leads quotes: link a customer's marketplace sell request
-- (marketplace_listings) to the sell order that holds the shop quotes
-- (sell_order_quotations), which the customer app shows and accepts.
-- order-service POST /sell-orders/listing-quotations resolves (or creates) the
-- sell order for a listing and stores the link here. order-service also adds
-- the column at startup (DatabaseSchemaInitializer).
--
-- Idempotent; one nullable column + index, no data changes. Skipped where
-- marketplace_listings doesn't exist (68_drop_marketplace_shopping.sql).
-- =============================================================================
DO $$
BEGIN
    IF to_regclass('public.marketplace_listings') IS NOT NULL THEN
        ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS sell_order_id UUID;
        CREATE INDEX IF NOT EXISTS idx_marketplace_listings_sell_order
            ON marketplace_listings (sell_order_id)
            WHERE sell_order_id IS NOT NULL;
    END IF;
END $$;
