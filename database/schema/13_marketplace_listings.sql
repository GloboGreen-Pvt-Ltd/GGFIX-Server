-- =============================================================================
-- 13_marketplace_listings.sql
--
-- The Buy/Sell board the shop-owner Buy screen reads from. Each row is a
-- product offered for sale by either a CUSTOMER (mobile app sell flow) or
-- a SHOP (owner-to-owner trade). The Buy screen filters by distance from
-- the current shop's lat/lng (Haversine, default 20 km).
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS marketplace_listings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_type     VARCHAR(20) NOT NULL,           -- 'CUSTOMER' | 'SHOP'
    seller_id       UUID NOT NULL,                  -- customer_users.id or shops.owner_user_id
    shop_id         UUID,                           -- set when seller_type='SHOP'
    category_id     UUID,
    brand_id        UUID,
    model_id        UUID,
    product_name    VARCHAR(255) NOT NULL,
    product_image   VARCHAR(1000),
    "condition"     VARCHAR(40),                    -- e.g. 'Good', 'Like New', 'Fair'
    description     TEXT,
    expected_price  DECIMAL(12, 2) NOT NULL,
    latitude        NUMERIC(10, 7),
    longitude       NUMERIC(10, 7),
    address         TEXT,
    city            VARCHAR(120),
    state           VARCHAR(120),
    pincode         VARCHAR(20),
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE | SOLD | CANCELLED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_marketplace_listings_status   ON marketplace_listings(status);
CREATE INDEX IF NOT EXISTS idx_marketplace_listings_seller   ON marketplace_listings(seller_type, seller_id);
CREATE INDEX IF NOT EXISTS idx_marketplace_listings_geo      ON marketplace_listings(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_marketplace_listings_created  ON marketplace_listings(created_at DESC);

COMMIT;
