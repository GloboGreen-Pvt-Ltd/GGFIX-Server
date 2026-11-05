-- =============================================================================
-- Mobile Repair Shop SaaS — PostgreSQL Schema
-- Multi-tenant, UUID PKs, created_at/updated_at, proper FKs
-- =============================================================================

-- Extend PostgreSQL to generate UUIDs
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- TENANT & IDENTITY
-- =============================================================================

CREATE TABLE shops (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255),
    phone           VARCHAR(50),
    address         TEXT,
    timezone        VARCHAR(50) DEFAULT 'Asia/Kolkata',
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255),
    name            VARCHAR(255),
    role            VARCHAR(50) NOT NULL,  -- 'SHOP_OWNER' | 'TECHNICIAN' | 'SUPER_ADMIN'
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (shop_id, email)
);

CREATE INDEX idx_users_shop_id ON users(shop_id);
CREATE INDEX idx_users_email ON users(email);

-- =============================================================================
-- SUBSCRIPTIONS (referenced by shops)
-- =============================================================================

CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE UNIQUE,
    plan_code       VARCHAR(50) NOT NULL,   -- 'STARTER' | 'PRO' | 'ENTERPRISE'
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',  -- 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'CANCELLED'
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_period_end TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscriptions_shop_id ON subscriptions(shop_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);

-- =============================================================================
-- TECHNICIANS & CUSTOMERS
-- =============================================================================

CREATE TABLE technicians (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(50),
    role_label      VARCHAR(100),          -- e.g. 'Senior Technician'
    is_available    BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_technicians_shop_id ON technicians(shop_id);
CREATE INDEX idx_technicians_user_id ON technicians(user_id);

CREATE TABLE customers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(50) NOT NULL,
    address         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customers_shop_id ON customers(shop_id);
CREATE INDEX idx_customers_phone ON customers(shop_id, phone);

-- =============================================================================
-- MASTER DATA (platform-wide; optional shop_id for shop-specific overrides)
-- =============================================================================

CREATE TABLE master_brands (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE master_models (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id        UUID NOT NULL REFERENCES master_brands(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (brand_id, name)
);

CREATE INDEX idx_master_models_brand_id ON master_models(brand_id);

CREATE TABLE master_ram_options (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    value_gb        INT NOT NULL UNIQUE,    -- 4, 6, 8, 12, 16, etc.
    label           VARCHAR(20) NOT NULL,    -- '4 GB', '8 GB'
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE master_storage_options (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    value_gb        INT NOT NULL UNIQUE,
    label           VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE master_repair_services (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- TICKETS (REPAIR)
-- =============================================================================

CREATE TABLE tickets (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id             UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    customer_id         UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    customer_name       VARCHAR(200),
    customer_phone      VARCHAR(30),
    assigned_technician_id UUID REFERENCES technicians(id) ON DELETE SET NULL,
    tracking_id         VARCHAR(50) NOT NULL,
    brand_id            UUID REFERENCES master_brands(id) ON DELETE SET NULL,
    model_id            UUID REFERENCES master_models(id) ON DELETE SET NULL,
    ram_option_id       UUID REFERENCES master_ram_options(id) ON DELETE SET NULL,
    storage_option_id  UUID REFERENCES master_storage_options(id) ON DELETE SET NULL,
    color               VARCHAR(100),
    imei                VARCHAR(50),
    status              VARCHAR(50) NOT NULL DEFAULT 'CREATED',  -- CREATED, IN_DIAGNOSIS, QUOTED, APPROVED, IN_REPAIR, READY, DELIVERED, CANCELLED
    estimated_price     DECIMAL(12, 2),
    final_price         DECIMAL(12, 2),
    issue_description  TEXT,
    device_display_name VARCHAR(200),
    device_image_url    VARCHAR(1000),
    repair_services_summary VARCHAR(500),
    price_items_json    TEXT,
    missing_parts_json TEXT,
    device_photos_json TEXT,
    device_security_type VARCHAR(20),
    device_security_value VARCHAR(255),
    customer_approval  BOOLEAN,
    estimated_ready_at  TIMESTAMPTZ,
    estimated_delivery_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (shop_id, tracking_id)
);

CREATE INDEX idx_tickets_shop_id ON tickets(shop_id);
CREATE INDEX idx_tickets_customer_id ON tickets(customer_id);
CREATE INDEX idx_tickets_customer_phone ON tickets(shop_id, customer_phone);
CREATE INDEX idx_tickets_status ON tickets(shop_id, status);
CREATE INDEX idx_tickets_tracking_id ON tickets(shop_id, tracking_id);
CREATE INDEX idx_tickets_created_at ON tickets(shop_id, created_at DESC);

CREATE TABLE ticket_status_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    from_status     VARCHAR(50),
    to_status       VARCHAR(50) NOT NULL,
    changed_by_id   UUID REFERENCES users(id) ON DELETE SET NULL,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ticket_status_history_ticket_id ON ticket_status_history(ticket_id);

CREATE TABLE repair_notes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    author_id       UUID REFERENCES users(id) ON DELETE SET NULL,
    note            TEXT NOT NULL,
    is_internal     BOOLEAN DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_repair_notes_ticket_id ON repair_notes(ticket_id);

-- =============================================================================
-- PICKUP REQUESTS
-- =============================================================================

CREATE TABLE pickup_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    ticket_id       UUID REFERENCES tickets(id) ON DELETE SET NULL,
    customer_id     UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    type            VARCHAR(50) NOT NULL,   -- 'PICKUP' | 'DELIVERY'
    scheduled_slot  TIMESTAMPTZ,
    address         TEXT,
    status          VARCHAR(50) NOT NULL DEFAULT 'REQUESTED',  -- REQUESTED, SCHEDULED, IN_TRANSIT, COMPLETED, CANCELLED
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pickup_requests_shop_id ON pickup_requests(shop_id);
CREATE INDEX idx_pickup_requests_ticket_id ON pickup_requests(ticket_id);
CREATE INDEX idx_pickup_requests_customer_id ON pickup_requests(customer_id);
CREATE INDEX idx_pickup_requests_status ON pickup_requests(shop_id, status);

-- =============================================================================
-- INVENTORY
-- =============================================================================

CREATE TABLE inventory_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    sku             VARCHAR(100),
    name            VARCHAR(255) NOT NULL,
    category        VARCHAR(100),          -- 'PART' | 'ACCESSORY' | 'CONSUMABLE'
    quantity        INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    unit_price      DECIMAL(12, 2),
    reorder_level   INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (shop_id, sku)
);

CREATE INDEX idx_inventory_items_shop_id ON inventory_items(shop_id);
CREATE INDEX idx_inventory_items_category ON inventory_items(shop_id, category);

-- =============================================================================
-- MARKETPLACE (BUY/SELL)
-- =============================================================================

CREATE TABLE marketplace_products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    brand_id        UUID REFERENCES master_brands(id) ON DELETE SET NULL,
    model_id        UUID REFERENCES master_models(id) ON DELETE SET NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    type            VARCHAR(50) NOT NULL,   -- 'SELL' | 'BUY'
    price           DECIMAL(12, 2),
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',  -- DRAFT, ACTIVE, SOLD, INACTIVE
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_marketplace_products_shop_id ON marketplace_products(shop_id);
CREATE INDEX idx_marketplace_products_type ON marketplace_products(shop_id, type);
CREATE INDEX idx_marketplace_products_status ON marketplace_products(shop_id, status);

CREATE TABLE marketplace_orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    customer_id     UUID REFERENCES customers(id) ON DELETE SET NULL,
    order_number    VARCHAR(50) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, CONFIRMED, PAID, SHIPPED, DELIVERED, CANCELLED
    total_amount    DECIMAL(12, 2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (shop_id, order_number)
);

CREATE INDEX idx_marketplace_orders_shop_id ON marketplace_orders(shop_id);
CREATE INDEX idx_marketplace_orders_customer_id ON marketplace_orders(customer_id);

CREATE TABLE marketplace_order_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE RESTRICT,
    quantity        INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price      DECIMAL(12, 2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_marketplace_order_items_order_id ON marketplace_order_items(order_id);

-- =============================================================================
-- SALES HISTORY (aggregated / audit for billing & reporting)
-- =============================================================================

CREATE TABLE sales_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    source          VARCHAR(50) NOT NULL,   -- 'REPAIR' | 'MARKETPLACE'
    source_id       UUID NOT NULL,          -- ticket_id or order_id
    amount          DECIMAL(12, 2) NOT NULL,
    sale_date       DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sales_history_shop_id ON sales_history(shop_id);
CREATE INDEX idx_sales_history_sale_date ON sales_history(shop_id, sale_date DESC);
CREATE INDEX idx_sales_history_source ON sales_history(shop_id, source);

-- =============================================================================
-- UPDATED_AT TRIGGER
-- =============================================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply updated_at trigger to each table (use EXECUTE PROCEDURE for PG 10; EXECUTE FUNCTION for PG 11+)
CREATE TRIGGER trg_shops_updated_at BEFORE UPDATE ON shops FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_technicians_updated_at BEFORE UPDATE ON technicians FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_customers_updated_at BEFORE UPDATE ON customers FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_subscriptions_updated_at BEFORE UPDATE ON subscriptions FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_brands_updated_at BEFORE UPDATE ON master_brands FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_models_updated_at BEFORE UPDATE ON master_models FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_ram_options_updated_at BEFORE UPDATE ON master_ram_options FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_storage_options_updated_at BEFORE UPDATE ON master_storage_options FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_repair_services_updated_at BEFORE UPDATE ON master_repair_services FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_tickets_updated_at BEFORE UPDATE ON tickets FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_repair_notes_updated_at BEFORE UPDATE ON repair_notes FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_pickup_requests_updated_at BEFORE UPDATE ON pickup_requests FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_inventory_items_updated_at BEFORE UPDATE ON inventory_items FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_marketplace_products_updated_at BEFORE UPDATE ON marketplace_products FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_marketplace_orders_updated_at BEFORE UPDATE ON marketplace_orders FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_marketplace_order_items_updated_at BEFORE UPDATE ON marketplace_order_items FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_sales_history_updated_at BEFORE UPDATE ON sales_history FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
