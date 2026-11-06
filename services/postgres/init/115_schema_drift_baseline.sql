-- Schema the running services map (ddl-auto: validate) but no earlier migration
-- creates. Found by comparing a migrations-only database with the entities.
-- Every statement is IF NOT EXISTS: a no-op on databases that already have it
-- (production), required on fresh installs. No data is dropped or rewritten.

-- Cart (customer app) — dropped by the old migration 68.
CREATE TABLE IF NOT EXISTS customer_cart_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_user_id UUID NOT NULL,
    product_id       UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE CASCADE,
    quantity         INT NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (customer_user_id, product_id)
);

-- Sell/buy listings board — dropped by the old migration 68.
CREATE TABLE IF NOT EXISTS marketplace_listings (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_type    VARCHAR(20) NOT NULL,
    seller_id      UUID NOT NULL,
    shop_id        UUID,
    category_id    UUID,
    brand_id       UUID,
    model_id       UUID,
    product_name   VARCHAR(255) NOT NULL,
    product_image  VARCHAR(1000),
    "condition"    VARCHAR(40),
    description    TEXT,
    expected_price DECIMAL(12, 2) NOT NULL,
    latitude       DECIMAL(10, 7),
    longitude      DECIMAL(10, 7),
    address        TEXT,
    city           VARCHAR(120),
    state          VARCHAR(120),
    pincode        VARCHAR(20),
    status         VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Customer <-> shop chat.
ALTER TABLE customer_chat_messages
    ADD COLUMN IF NOT EXISTS attachment_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS read_at         TIMESTAMPTZ;
ALTER TABLE customer_chat_threads
    ADD COLUMN IF NOT EXISTS customer_name          VARCHAR(120),
    ADD COLUMN IF NOT EXISTS customer_mobile        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS customer_avatar_url    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS shop_name              VARCHAR(160),
    ADD COLUMN IF NOT EXISTS shop_image_url         VARCHAR(500),
    ADD COLUMN IF NOT EXISTS customer_last_read_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS shop_last_read_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS customer_typing_until  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS shop_typing_until      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS unread_customer_count  INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unread_shop_count      INT NOT NULL DEFAULT 0;

-- Repair booking services / shops.
ALTER TABLE repair_booking_services ADD COLUMN IF NOT EXISTS warranty VARCHAR(20);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS city VARCHAR(255);
