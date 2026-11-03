-- =============================================================================
-- 106_marketplace_sell_module.sql
--
-- Partner Sell module (contract: partner app docs/SELL-MODULE-API.md).
--   * marketplace_products: listing type, item condition, stock (quantity /
--     sold_quantity), negotiable flag, fulfilment options, video, bulk-upload
--     idempotency (client_ref, unique per shop) and batch id.
--   * marketplace_enquiries: buyer <-> shop enquiry / purchase request on a
--     listing, with the negotiation state (OPEN, QUOTED, COUNTERED, ACCEPTED,
--     CONFIRMED, REJECTED, CANCELLED). `version` is the JPA optimistic lock.
--   * marketplace_enquiry_offers: the negotiation timeline of an enquiry.
--
-- Separate from customer -> shop sell-order quotations (order-service).
-- marketplace-service runs with ddl-auto=validate: apply this BEFORE deploying
-- the jar that maps these columns.
--
-- Idempotent. Safe to re-run.
-- =============================================================================

BEGIN;

-- ---------- marketplace_products ---------------------------------------------
-- Some environments ran services/postgres/init/68_drop_marketplace_shopping.sql,
-- which drops this table. Recreate it (with every column MarketplaceProduct.java
-- maps) so the ALTERs below and Hibernate validation succeed there too. No-op
-- where the table exists.
CREATE TABLE IF NOT EXISTS marketplace_products (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id             UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    seller_user_id      UUID,
    brand_id            UUID,
    model_id            UUID,
    ram_option_id       UUID,
    storage_option_id   UUID,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    type                VARCHAR(50) NOT NULL,                    -- SELL | BUY
    price               DECIMAL(12, 2),
    status              VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    condition_label     VARCHAR(50),
    color               VARCHAR(100),
    ram_label           VARCHAR(50),
    storage_label       VARCHAR(50),
    network             VARCHAR(50),
    imei                VARCHAR(50),
    working_condition   VARCHAR(30),
    description_type    VARCHAR(30),
    image_url           VARCHAR(500),
    extra_image_urls    TEXT,
    assessment_json     TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Columns the existing entity already maps that only the docker init set's
-- 25_marketplace_products_extended.sql adds (a database built from this
-- folder's 01..29 lacks them). No-ops where they exist; no data changes.
ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS seller_user_id      UUID,
    ADD COLUMN IF NOT EXISTS ram_option_id       UUID,
    ADD COLUMN IF NOT EXISTS storage_option_id   UUID,
    ADD COLUMN IF NOT EXISTS ram_label           VARCHAR(50),
    ADD COLUMN IF NOT EXISTS imei                VARCHAR(50),
    ADD COLUMN IF NOT EXISTS working_condition   VARCHAR(30),
    ADD COLUMN IF NOT EXISTS description_type    VARCHAR(30),
    ADD COLUMN IF NOT EXISTS assessment_json     TEXT;

ALTER TABLE marketplace_products
    ADD COLUMN IF NOT EXISTS listing_type        VARCHAR(20),                       -- DEVICE | SPARE_PART | ACCESSORY
    ADD COLUMN IF NOT EXISTS item_condition      VARCHAR(20),                       -- NEW | USED | REFURBISHED
    ADD COLUMN IF NOT EXISTS quantity            INT NOT NULL DEFAULT 1,            -- remaining stock
    ADD COLUMN IF NOT EXISTS sold_quantity       INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS negotiable          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS fulfilment_options  VARCHAR(100) DEFAULT 'PICKUP',     -- comma-separated PICKUP,SHIPPING
    ADD COLUMN IF NOT EXISTS video_url           VARCHAR(500),
    ADD COLUMN IF NOT EXISTS client_ref          VARCHAR(64),                       -- idempotency key
    ADD COLUMN IF NOT EXISTS batch_id            UUID,                              -- bulk upload batch
    ADD COLUMN IF NOT EXISTS updated_at          TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_products_shop_client_ref
    ON marketplace_products (shop_id, client_ref)
    WHERE client_ref IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_marketplace_products_batch
    ON marketplace_products (batch_id)
    WHERE batch_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_marketplace_products_shop_updated
    ON marketplace_products (shop_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_marketplace_products_shop_imei
    ON marketplace_products (shop_id, imei)
    WHERE imei IS NOT NULL;

-- Backfill from assessment_json.sellMeta: the Partner app stores the new fields
-- there while it talks to a server without these columns, so a listing saved
-- with quantity 5 must not read back as the column default 1. Only rows not yet
-- backfilled (listing_type IS NULL); invalid JSON is skipped, never fatal.
CREATE OR REPLACE FUNCTION pg_temp.try_jsonb(t TEXT) RETURNS JSONB LANGUAGE plpgsql AS $f$
BEGIN
    RETURN t::jsonb;
EXCEPTION WHEN others THEN
    RETURN NULL;
END
$f$;

WITH meta AS (
    SELECT id, pg_temp.try_jsonb(assessment_json) -> 'sellMeta' AS m
    FROM marketplace_products
    WHERE listing_type IS NULL AND assessment_json LIKE '%"sellMeta"%'
)
UPDATE marketplace_products p SET
    listing_type       = CASE WHEN meta.m ->> 'listingType' IN ('DEVICE', 'SPARE_PART', 'ACCESSORY') THEN meta.m ->> 'listingType' ELSE p.listing_type END,
    item_condition     = CASE WHEN meta.m ->> 'itemCondition' IN ('NEW', 'USED', 'REFURBISHED') THEN meta.m ->> 'itemCondition' ELSE p.item_condition END,
    quantity           = CASE WHEN meta.m ->> 'quantity' ~ '^[0-9]{1,6}$' THEN (meta.m ->> 'quantity')::INT ELSE p.quantity END,
    sold_quantity      = CASE WHEN meta.m ->> 'soldQuantity' ~ '^[0-9]{1,6}$' THEN (meta.m ->> 'soldQuantity')::INT ELSE p.sold_quantity END,
    negotiable         = CASE WHEN jsonb_typeof(meta.m -> 'negotiable') = 'boolean' THEN (meta.m ->> 'negotiable')::BOOLEAN ELSE p.negotiable END,
    fulfilment_options = COALESCE(
        CASE WHEN jsonb_typeof(meta.m -> 'fulfilmentOptions') = 'array' THEN
            (SELECT string_agg(x, ',') FROM jsonb_array_elements_text(meta.m -> 'fulfilmentOptions') AS x WHERE x IN ('PICKUP', 'SHIPPING'))
        END,
        p.fulfilment_options),
    video_url          = COALESCE(p.video_url, NULLIF(meta.m ->> 'videoUrl', ''))
FROM meta
WHERE p.id = meta.id AND meta.m IS NOT NULL AND jsonb_typeof(meta.m) = 'object';

-- Listings already sold before stock was tracked: nothing left to sell.
UPDATE marketplace_products
SET quantity = 0, sold_quantity = GREATEST(sold_quantity, 1)
WHERE UPPER(status) IN ('SOLD', 'COMPLETED') AND quantity > 0 AND sold_quantity = 0
  AND (assessment_json IS NULL OR assessment_json NOT LIKE '%"sellMeta"%');

-- ---------- marketplace_enquiries --------------------------------------------
-- buyer_user_id has no FK on purpose: it is the JWT subject of whoever calls
-- /customer/marketplace/** (normally customer_users.id), mirroring how the
-- service already trusts the token for chat.
CREATE TABLE IF NOT EXISTS marketplace_enquiries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id          UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE CASCADE,
    shop_id             UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    buyer_user_id       UUID NOT NULL,
    kind                VARCHAR(20) NOT NULL DEFAULT 'ENQUIRY',   -- ENQUIRY | PURCHASE_REQUEST
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',      -- OPEN | QUOTED | COUNTERED | ACCEPTED | CONFIRMED | REJECTED | CANCELLED
    message             TEXT,
    quantity            INT NOT NULL DEFAULT 1,
    last_offer_amount   DECIMAL(12, 2),
    last_offer_by       VARCHAR(10),                              -- SHOP | BUYER
    agreed_amount       DECIMAL(12, 2),
    fulfilment          VARCHAR(20),                              -- PICKUP | SHIPPING
    buyer_name          VARCHAR(255),                             -- snapshot (customer_users.full_name)
    buyer_phone         VARCHAR(20),                              -- snapshot (customer_users.mobile)
    shop_unread         BOOLEAN NOT NULL DEFAULT TRUE,
    buyer_unread        BOOLEAN NOT NULL DEFAULT FALSE,
    closed_reason       TEXT,
    version             BIGINT NOT NULL DEFAULT 0,                -- optimistic lock
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_marketplace_enquiries_shop_status
    ON marketplace_enquiries (shop_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_marketplace_enquiries_product
    ON marketplace_enquiries (product_id);

CREATE INDEX IF NOT EXISTS idx_marketplace_enquiries_buyer
    ON marketplace_enquiries (buyer_user_id, updated_at DESC);

-- One non-terminal enquiry per buyer and listing.
CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_enquiries_open_per_buyer
    ON marketplace_enquiries (product_id, buyer_user_id)
    WHERE status IN ('OPEN', 'QUOTED', 'COUNTERED', 'ACCEPTED');

-- ---------- marketplace_enquiry_offers ---------------------------------------
CREATE TABLE IF NOT EXISTS marketplace_enquiry_offers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enquiry_id          UUID NOT NULL REFERENCES marketplace_enquiries(id) ON DELETE CASCADE,
    author              VARCHAR(10) NOT NULL,                     -- SHOP | BUYER | SYSTEM
    action              VARCHAR(20) NOT NULL,                     -- ENQUIRY | OFFER | COUNTER | ACCEPT | REJECT | CONFIRM | CANCEL
    amount              DECIMAL(12, 2),
    note                TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_marketplace_enquiry_offers_enquiry
    ON marketplace_enquiry_offers (enquiry_id, created_at);

-- ---------- updated_at triggers (set_updated_at() from 01_schema.sql) --------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'set_updated_at') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_marketplace_products_updated_at') THEN
            CREATE TRIGGER trg_marketplace_products_updated_at
                BEFORE UPDATE ON marketplace_products
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_marketplace_enquiries_updated_at') THEN
            CREATE TRIGGER trg_marketplace_enquiries_updated_at
                BEFORE UPDATE ON marketplace_enquiries
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;
    END IF;
END$$;

COMMIT;
