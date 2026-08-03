-- =============================================================================
-- Mobile Repair Shop SaaS — Customer App Schema (Phase 2)
-- Adds platform-wide customer profile, addresses, saved devices, extended
-- master data, shop directory, pickup orders, unified orders, marketplace, etc.
-- =============================================================================

-- =============================================================================
-- PLATFORM-WIDE CUSTOMER IDENTITY
-- A platform customer is independent of any single shop and can interact with
-- many shops. Distinct from the per-shop `customers` row (which is created
-- lazily by tickets/orders against a particular shop).
-- =============================================================================

CREATE TABLE customer_users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name           VARCHAR(255),
    email               VARCHAR(255) UNIQUE,
    mobile              VARCHAR(50) UNIQUE,
    alternate_mobile    VARCHAR(50),
    profile_image_url   VARCHAR(500),
    id_proof_url        VARCHAR(1000),
    password_hash       VARCHAR(255),
    is_active           BOOLEAN DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_users_email ON customer_users(email);
CREATE INDEX idx_customer_users_mobile ON customer_users(mobile);

CREATE TABLE customer_addresses (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_user_id    UUID NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
    label               VARCHAR(50) NOT NULL DEFAULT 'Home', -- Home | Office | Other
    full_name           VARCHAR(255),
    mobile              VARCHAR(50),
    pincode             VARCHAR(20),
    locality            VARCHAR(255), -- LEGACY: prefer area. API dual-writes area -> locality for back-compat readers.
    area                VARCHAR(255), -- shown as "Area" in the customer-app form
    address_line        TEXT,         -- shown as "Door no. / Street" in the customer-app form
    city                VARCHAR(255), -- LEGACY: prefer district. API dual-writes district -> city for back-compat readers.
    district            VARCHAR(255),
    taluk               VARCHAR(255),
    state               VARCHAR(255),
    latitude            DECIMAL(10, 7),
    longitude           DECIMAL(10, 7),
    is_default          BOOLEAN DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_addresses_user ON customer_addresses(customer_user_id);

CREATE TABLE customer_saved_devices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_user_id    UUID NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
    category_id         UUID,  -- master_device_categories(id), nullable to avoid hard FK if seeded later
    brand_id            UUID,
    model_id            UUID,
    ram_option_id       UUID,
    storage_option_id   UUID,
    color               VARCHAR(100),
    imei                VARCHAR(50),
    note                TEXT,
    is_default          BOOLEAN DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_saved_devices_user ON customer_saved_devices(customer_user_id);

-- Category code + denormalized display fields (model/brand/ram/storage labels),
-- captured at save time so the app renders the device without joining master-data.
ALTER TABLE customer_saved_devices ADD COLUMN IF NOT EXISTS category_code VARCHAR(50);
ALTER TABLE customer_saved_devices ADD COLUMN IF NOT EXISTS model_name    VARCHAR(255);
ALTER TABLE customer_saved_devices ADD COLUMN IF NOT EXISTS brand_name    VARCHAR(150);
ALTER TABLE customer_saved_devices ADD COLUMN IF NOT EXISTS ram_label     VARCHAR(50);
ALTER TABLE customer_saved_devices ADD COLUMN IF NOT EXISTS storage_label VARCHAR(50);

-- =============================================================================
-- EXTENDED MASTER DATA
-- =============================================================================

CREATE TABLE master_device_categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL UNIQUE, -- SMARTPHONE, LAPTOP, etc.
    name            VARCHAR(100) NOT NULL,
    image_url       VARCHAR(500),
    image_base64    TEXT,
    sort_order      INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Make sure existing master_models gets a category_id reference (idempotent).
ALTER TABLE master_models ADD COLUMN IF NOT EXISTS category_id UUID REFERENCES master_device_categories(id) ON DELETE SET NULL;
ALTER TABLE master_models ADD COLUMN IF NOT EXISTS image_url   VARCHAR(500);
ALTER TABLE master_models ADD COLUMN IF NOT EXISTS image_base64 TEXT;
ALTER TABLE master_brands ADD COLUMN IF NOT EXISTS image_url   VARCHAR(500);
ALTER TABLE master_brands ADD COLUMN IF NOT EXISTS image_base64 TEXT;

-- Device sub-type within a category (Mobile -> Smartphones/Feature Phones/Foldables, etc.).
-- Selected as wizard step 2 (between Category and Brand); carried into the booking.
CREATE TABLE master_device_types (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id     UUID NOT NULL REFERENCES master_device_categories(id) ON DELETE CASCADE,
    code            VARCHAR(80),
    name            VARCHAR(120) NOT NULL,
    image_url       VARCHAR(500),
    image_base64    TEXT,
    sort_order      INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (category_id, name)
);

CREATE INDEX idx_master_device_types_category ON master_device_types(category_id);

CREATE TABLE master_device_series (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_id        UUID NOT NULL REFERENCES master_brands(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (brand_id, name)
);

CREATE INDEX idx_master_device_series_brand ON master_device_series(brand_id);

-- Allow models to belong to a series (optional)
ALTER TABLE master_models ADD COLUMN IF NOT EXISTS series_id UUID REFERENCES master_device_series(id) ON DELETE SET NULL;

CREATE TABLE master_colors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL UNIQUE,
    hex_code        VARCHAR(20),
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Variants tying brand+model+ram+storage+color with optional pricing reference
CREATE TABLE master_model_variants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id        UUID NOT NULL REFERENCES master_models(id) ON DELETE CASCADE,
    ram_option_id   UUID REFERENCES master_ram_options(id) ON DELETE SET NULL,
    storage_option_id UUID REFERENCES master_storage_options(id) ON DELETE SET NULL,
    color_id        UUID REFERENCES master_colors(id) ON DELETE SET NULL,
    reference_price DECIMAL(12, 2),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_master_model_variants_model ON master_model_variants(model_id);

-- Categorize repair services into groups (Display, Battery, Motherboard, ...)
CREATE TABLE master_repair_categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    icon_url        VARCHAR(500),
    icon_base64     TEXT,
    description     TEXT,
    sort_order      INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE master_repair_services ADD COLUMN IF NOT EXISTS category_id UUID REFERENCES master_repair_categories(id) ON DELETE SET NULL;
ALTER TABLE master_repair_services ADD COLUMN IF NOT EXISTS device_category_id UUID REFERENCES master_device_categories(id) ON DELETE SET NULL;
ALTER TABLE master_repair_services ADD COLUMN IF NOT EXISTS icon_url VARCHAR(500);
ALTER TABLE master_repair_services ADD COLUMN IF NOT EXISTS icon_base64 TEXT;

-- Repair categories (main categories) scoped to a device category, with a
-- customer-facing display name (e.g. "Display & Touch" -> "Screen / Touch Problems").
ALTER TABLE master_repair_categories ADD COLUMN IF NOT EXISTS device_category_id UUID REFERENCES master_device_categories(id) ON DELETE CASCADE;
ALTER TABLE master_repair_categories ADD COLUMN IF NOT EXISTS display_name VARCHAR(255);
ALTER TABLE master_repair_categories ALTER COLUMN code TYPE VARCHAR(80);

-- Sell flow: Screening questions
CREATE TABLE master_screening_questions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow            VARCHAR(50) NOT NULL DEFAULT 'WORKING', -- WORKING | DEAD | COMMON
    question        TEXT NOT NULL,
    helper_text     TEXT,
    sort_order      INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_master_screening_questions_flow ON master_screening_questions(flow);

-- Condition options (for screen, touch glass, back panel, side/frame ...)
CREATE TABLE master_condition_groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(100) NOT NULL UNIQUE, -- SCREEN_CONDITION, TOUCH_GLASS, BACK_PANEL, SIDE_PANEL, SCREEN_VISIBLE, SCREEN_SCRATCH, FRAME
    name            VARCHAR(255) NOT NULL,
    flow            VARCHAR(50) NOT NULL DEFAULT 'COMMON', -- WORKING | DEAD | COMMON
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE master_condition_options (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id            UUID NOT NULL REFERENCES master_condition_groups(id) ON DELETE CASCADE,
    label               VARCHAR(255) NOT NULL,
    icon_url            VARCHAR(500),
    icon_base64         TEXT,
    price_impact        DECIMAL(12, 2) DEFAULT 0,
    sort_order          INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_master_condition_options_group ON master_condition_options(group_id);

-- Functional issues (multi-select in sell flow)
CREATE TABLE master_functional_issues (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    icon_url        VARCHAR(500),
    icon_base64     TEXT,
    price_impact    DECIMAL(12, 2) DEFAULT 0,
    sort_order      INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Accessories and warranty options (sell flow)
CREATE TABLE master_accessory_options (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    icon_url        VARCHAR(500),
    icon_base64     TEXT,
    sort_order      INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE master_warranty_options (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL UNIQUE, -- LT_3M, 3_6M, 6_11M, GT_11M
    label           VARCHAR(100) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Home banners
CREATE TABLE master_banners (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255),
    image_url       TEXT,
    image_base64    TEXT,
    link_target     VARCHAR(255),
    sort_order      INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- FAQ entries
CREATE TABLE master_faq_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question        TEXT NOT NULL,
    answer          TEXT NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- App content pages (About, Terms & Conditions, Privacy, etc.)
CREATE TABLE master_app_content (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50) NOT NULL UNIQUE, -- ABOUT_US, TERMS, PRIVACY, SUPPORT
    title           VARCHAR(255) NOT NULL,
    body            TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Support contact info (shown on Customer Support card)
CREATE TABLE master_support_contacts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    label           VARCHAR(100) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(50),
    image_url       VARCHAR(500),
    sort_order      INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =============================================================================
-- SHOP DIRECTORY (platform-wide listing of shops, separate concern from `shops`)
-- For now we re-use `shops` for the directory and add location columns.
-- =============================================================================

ALTER TABLE shops ADD COLUMN IF NOT EXISTS latitude DECIMAL(10, 7);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS longitude DECIMAL(10, 7);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS city VARCHAR(255);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS state VARCHAR(255);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS pincode VARCHAR(20);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS rating DECIMAL(3, 1);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS hours_text VARCHAR(255);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS hero_image_url VARCHAR(500);
ALTER TABLE shops ADD COLUMN IF NOT EXISTS description TEXT;

CREATE TABLE shop_services (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    service_code    VARCHAR(50) NOT NULL, -- REPAIR | BUY | SELL | PICKUP | SMART_EXCHANGE
    is_enabled      BOOLEAN DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (shop_id, service_code)
);

CREATE TABLE shop_images (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    image_url       VARCHAR(500) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_shop_images_shop ON shop_images(shop_id);

CREATE TABLE shop_pickup_slots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id         UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    day_of_week     SMALLINT, -- 0=Sun ... 6=Sat; null = any day
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    capacity        INT DEFAULT 10,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_shop_pickup_slots_shop ON shop_pickup_slots(shop_id);

-- =============================================================================
-- CUSTOMER-FACING ORDER FLOWS
-- =============================================================================

-- Pickup orders (extended pickup_requests but tied to platform customer_user)
CREATE TABLE customer_pickup_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number        VARCHAR(60) NOT NULL UNIQUE,
    customer_user_id    UUID NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
    shop_id             UUID REFERENCES shops(id) ON DELETE SET NULL,
    ticket_id           UUID REFERENCES tickets(id) ON DELETE SET NULL,
    address_id          UUID REFERENCES customer_addresses(id) ON DELETE SET NULL,
    flow_type           VARCHAR(50) NOT NULL DEFAULT 'REPAIR_PICKUP', -- REPAIR_PICKUP | SELL_PICKUP | DELIVERY
    pickup_date         DATE,
    pickup_slot_start   TIME,
    pickup_slot_end     TIME,
    status              VARCHAR(50) NOT NULL DEFAULT 'ORDER_PLACED',
    estimate_amount     DECIMAL(12, 2),
    final_amount        DECIMAL(12, 2),
    note                TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_pickup_orders_user ON customer_pickup_orders(customer_user_id);
CREATE INDEX idx_customer_pickup_orders_shop ON customer_pickup_orders(shop_id);
CREATE INDEX idx_customer_pickup_orders_status ON customer_pickup_orders(status);

CREATE TABLE customer_pickup_order_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pickup_order_id     UUID NOT NULL REFERENCES customer_pickup_orders(id) ON DELETE CASCADE,
    status              VARCHAR(100) NOT NULL,
    note                TEXT,
    actor               VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_pickup_order_events_order ON customer_pickup_order_events(pickup_order_id);

-- Unified customer order (BUY/SELL/REPAIR/ENQUIRY view) — lightweight wrapper
CREATE TABLE customer_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number        VARCHAR(60) NOT NULL UNIQUE,
    customer_user_id    UUID NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
    shop_id             UUID REFERENCES shops(id) ON DELETE SET NULL,
    order_type          VARCHAR(50) NOT NULL, -- BUY | SELL | REPAIR | PICKUP | ENQUIRY
    reference_id        UUID, -- ticket_id / sell_order_id / marketplace_order_id / pickup_id
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_amount        DECIMAL(12, 2),
    payload_json        TEXT, -- flexible snapshot of summary fields
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_orders_user ON customer_orders(customer_user_id);
CREATE INDEX idx_customer_orders_type ON customer_orders(order_type);
CREATE INDEX idx_customer_orders_status ON customer_orders(status);

-- =============================================================================
-- SELL ORDERS (customer wants to sell a device)
-- =============================================================================

CREATE TABLE sell_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sell_number         VARCHAR(60) NOT NULL UNIQUE,
    customer_user_id    UUID NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
    shop_id             UUID REFERENCES shops(id) ON DELETE SET NULL, -- chosen shop after quotation
    address_id          UUID REFERENCES customer_addresses(id) ON DELETE SET NULL,
    brand_id            UUID REFERENCES master_brands(id) ON DELETE SET NULL,
    model_id            UUID REFERENCES master_models(id) ON DELETE SET NULL,
    ram_option_id       UUID REFERENCES master_ram_options(id) ON DELETE SET NULL,
    storage_option_id   UUID REFERENCES master_storage_options(id) ON DELETE SET NULL,
    color               VARCHAR(100),
    imei                VARCHAR(50),
    working_condition   VARCHAR(50), -- WORKING | DEAD | UNKNOWN
    warranty_code       VARCHAR(50),
    device_condition_summary TEXT,
    payload_json        TEXT,
    front_image_url     VARCHAR(500),
    back_image_url      VARCHAR(500),
    side_image_url      VARCHAR(500),
    camera_image_url    VARCHAR(500),
    other_image_url     VARCHAR(500),
    status              VARCHAR(50) NOT NULL DEFAULT 'AWAITING_QUOTATION',
    final_price         DECIMAL(12, 2),
    pickup_date         DATE,
    pickup_slot_start   TIME,
    pickup_slot_end     TIME,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sell_orders_user ON sell_orders(customer_user_id);
CREATE INDEX idx_sell_orders_status ON sell_orders(status);

CREATE TABLE sell_order_screening_answers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sell_order_id       UUID NOT NULL REFERENCES sell_orders(id) ON DELETE CASCADE,
    question_id         UUID REFERENCES master_screening_questions(id) ON DELETE SET NULL,
    answer              VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sell_order_screening_answers_order ON sell_order_screening_answers(sell_order_id);

CREATE TABLE sell_order_conditions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sell_order_id       UUID NOT NULL REFERENCES sell_orders(id) ON DELETE CASCADE,
    group_code          VARCHAR(100) NOT NULL,
    option_id           UUID REFERENCES master_condition_options(id) ON DELETE SET NULL,
    option_label        VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sell_order_conditions_order ON sell_order_conditions(sell_order_id);

CREATE TABLE sell_order_issues (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sell_order_id       UUID NOT NULL REFERENCES sell_orders(id) ON DELETE CASCADE,
    issue_id            UUID REFERENCES master_functional_issues(id) ON DELETE SET NULL,
    issue_code          VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sell_order_issues_order ON sell_order_issues(sell_order_id);

CREATE TABLE sell_order_accessories (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sell_order_id       UUID NOT NULL REFERENCES sell_orders(id) ON DELETE CASCADE,
    accessory_id        UUID REFERENCES master_accessory_options(id) ON DELETE SET NULL,
    accessory_code      VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sell_order_accessories_order ON sell_order_accessories(sell_order_id);

-- Quotations submitted by shops for a sell order
CREATE TABLE sell_order_quotations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sell_order_id       UUID NOT NULL REFERENCES sell_orders(id) ON DELETE CASCADE,
    shop_id             UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    shop_name           VARCHAR(255),
    shop_phone          VARCHAR(50),
    shop_city           VARCHAR(255),
    quotation_price     DECIMAL(12, 2) NOT NULL,
    note                TEXT,
    status              VARCHAR(50) NOT NULL DEFAULT 'PROPOSED',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (sell_order_id, shop_id)
);

CREATE INDEX idx_sell_order_quotations_order ON sell_order_quotations(sell_order_id);

-- =============================================================================
-- REPAIR BOOKINGS (customer-side wrapper around tickets)
-- =============================================================================

CREATE TABLE repair_bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_number      VARCHAR(60) NOT NULL UNIQUE,
    customer_user_id    UUID NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
    customer_name       VARCHAR(255),
    customer_mobile     VARCHAR(50),
    shop_id             UUID REFERENCES shops(id) ON DELETE SET NULL,
    ticket_id           UUID REFERENCES tickets(id) ON DELETE SET NULL,
    assigned_technician_id UUID,
    saved_device_id     UUID REFERENCES customer_saved_devices(id) ON DELETE SET NULL,
    brand_id            UUID REFERENCES master_brands(id) ON DELETE SET NULL,
    model_id            UUID REFERENCES master_models(id) ON DELETE SET NULL,
    ram_option_id       UUID REFERENCES master_ram_options(id) ON DELETE SET NULL,
    storage_option_id   UUID REFERENCES master_storage_options(id) ON DELETE SET NULL,
    color               VARCHAR(100),
    service_mode        VARCHAR(50) NOT NULL DEFAULT 'PICKUP', -- ENQUIRY | PICKUP | WALK_IN
    front_image_url     VARCHAR(500),
    back_image_url      VARCHAR(500),
    video_url           VARCHAR(500),
    issue_summary       TEXT,
    estimate_amount     DECIMAL(12, 2),
    final_amount        DECIMAL(12, 2),
    status              VARCHAR(50) NOT NULL DEFAULT 'ORDER_PLACED',
    pickup_address_id   UUID REFERENCES customer_addresses(id) ON DELETE SET NULL,
    pickup_date         DATE,
    pickup_slot_start   TIME,
    pickup_slot_end     TIME,
    -- Service workflow details (populated by shop/technician side)
    estimated_ready_at      TIMESTAMPTZ,
    estimated_duration_hours INT,
    estimated_delivery_at   TIMESTAMPTZ,
    customer_approval       VARCHAR(20),
    device_pin              VARCHAR(20),
    missing_damage_parts    TEXT,
    technician_name         VARCHAR(120),
    technician_code         VARCHAR(40),
    technician_photos       TEXT,
    assigned_pickup_person_id UUID,
    pickup_person_name     VARCHAR(120),
    pickup_person_phone    VARCHAR(30),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_repair_bookings_user ON repair_bookings(customer_user_id);

-- In-app notifications shown in the customer's notification list (one row per
-- shop status update / system event).
CREATE TABLE customer_notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_user_id    UUID NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
    booking_id          UUID,
    booking_number      VARCHAR(60),
    status_key          VARCHAR(100),
    title               VARCHAR(200) NOT NULL,
    body                TEXT,
    type                VARCHAR(30) DEFAULT 'orders',
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_notifications_user ON customer_notifications(customer_user_id, created_at DESC);
CREATE INDEX idx_repair_bookings_status ON repair_bookings(status);

CREATE TABLE repair_booking_services (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id          UUID NOT NULL REFERENCES repair_bookings(id) ON DELETE CASCADE,
    repair_service_id   UUID REFERENCES master_repair_services(id) ON DELETE SET NULL,
    service_code        VARCHAR(50),
    service_name        VARCHAR(255),
    estimated_price     DECIMAL(12, 2),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_repair_booking_services_booking ON repair_booking_services(booking_id);

CREATE TABLE repair_booking_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id          UUID NOT NULL REFERENCES repair_bookings(id) ON DELETE CASCADE,
    status              VARCHAR(100) NOT NULL,
    note                TEXT,
    actor               VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_repair_booking_events_booking ON repair_booking_events(booking_id);

-- =============================================================================
-- MARKETPLACE: cart for platform customers
-- =============================================================================

CREATE TABLE customer_cart_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_user_id    UUID NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES marketplace_products(id) ON DELETE CASCADE,
    quantity            INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (customer_user_id, product_id)
);

CREATE INDEX idx_customer_cart_items_user ON customer_cart_items(customer_user_id);

ALTER TABLE marketplace_products ADD COLUMN IF NOT EXISTS condition_label VARCHAR(50);
ALTER TABLE marketplace_products ADD COLUMN IF NOT EXISTS color VARCHAR(100);
ALTER TABLE marketplace_products ADD COLUMN IF NOT EXISTS storage_label VARCHAR(50);
ALTER TABLE marketplace_products ADD COLUMN IF NOT EXISTS network VARCHAR(50);
ALTER TABLE marketplace_products ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
ALTER TABLE marketplace_products ADD COLUMN IF NOT EXISTS extra_image_urls TEXT;

-- Customer chat enquiries (Globo Green chat)
CREATE TABLE customer_chat_threads (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_user_id    UUID NOT NULL REFERENCES customer_users(id) ON DELETE CASCADE,
    shop_id             UUID REFERENCES shops(id) ON DELETE SET NULL,
    subject             VARCHAR(255),
    last_message_preview VARCHAR(255),
    last_message_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_chat_threads_user ON customer_chat_threads(customer_user_id);

CREATE TABLE customer_chat_messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id           UUID NOT NULL REFERENCES customer_chat_threads(id) ON DELETE CASCADE,
    sender              VARCHAR(50) NOT NULL, -- CUSTOMER | SHOP | SYSTEM
    body                TEXT NOT NULL,
    attachment_url      VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_chat_messages_thread ON customer_chat_messages(thread_id);

-- =============================================================================
-- UPDATED_AT TRIGGERS for new tables
-- =============================================================================

CREATE TRIGGER trg_customer_users_updated_at BEFORE UPDATE ON customer_users FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_customer_addresses_updated_at BEFORE UPDATE ON customer_addresses FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_customer_saved_devices_updated_at BEFORE UPDATE ON customer_saved_devices FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_device_categories_updated_at BEFORE UPDATE ON master_device_categories FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_device_series_updated_at BEFORE UPDATE ON master_device_series FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_colors_updated_at BEFORE UPDATE ON master_colors FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_model_variants_updated_at BEFORE UPDATE ON master_model_variants FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_repair_categories_updated_at BEFORE UPDATE ON master_repair_categories FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_screening_questions_updated_at BEFORE UPDATE ON master_screening_questions FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_condition_groups_updated_at BEFORE UPDATE ON master_condition_groups FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_condition_options_updated_at BEFORE UPDATE ON master_condition_options FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_functional_issues_updated_at BEFORE UPDATE ON master_functional_issues FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_accessory_options_updated_at BEFORE UPDATE ON master_accessory_options FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_warranty_options_updated_at BEFORE UPDATE ON master_warranty_options FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_banners_updated_at BEFORE UPDATE ON master_banners FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_faq_items_updated_at BEFORE UPDATE ON master_faq_items FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_app_content_updated_at BEFORE UPDATE ON master_app_content FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_master_support_contacts_updated_at BEFORE UPDATE ON master_support_contacts FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_shop_pickup_slots_updated_at BEFORE UPDATE ON shop_pickup_slots FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_customer_pickup_orders_updated_at BEFORE UPDATE ON customer_pickup_orders FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_customer_orders_updated_at BEFORE UPDATE ON customer_orders FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_sell_orders_updated_at BEFORE UPDATE ON sell_orders FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_sell_order_quotations_updated_at BEFORE UPDATE ON sell_order_quotations FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_repair_bookings_updated_at BEFORE UPDATE ON repair_bookings FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_customer_cart_items_updated_at BEFORE UPDATE ON customer_cart_items FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
CREATE TRIGGER trg_customer_chat_threads_updated_at BEFORE UPDATE ON customer_chat_threads FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
