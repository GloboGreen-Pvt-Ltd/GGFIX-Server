-- =============================================================================
-- 106: Subscription v2 — plan master, settings, Razorpay (UPI) orders/payments,
--      per-shop coverage, audit log and webhook dedupe.
--
-- Idempotent: every statement is IF NOT EXISTS / ON CONFLICT DO NOTHING, so it
-- is safe to re-run in pgAdmin. It NEVER rewrites existing `subscriptions`
-- rows or amounts — legacy FREE_TRIAL / BASIC rows keep their limits and price
-- and stay governed by the SubscriptionPlan enum in common-subscription.
--
-- Run this on RDS BEFORE deploying subscription-service (ddl-auto: validate).
-- =============================================================================

-- ── Plan master ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subscription_plans (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                         VARCHAR(50)  NOT NULL,
    name                         VARCHAR(120) NOT NULL,
    plan_type                    VARCHAR(30)  NOT NULL,
    description                  TEXT,
    price_paise                  BIGINT       NOT NULL DEFAULT 0,
    currency                     VARCHAR(3)   NOT NULL DEFAULT 'INR',
    duration_value               INT          NOT NULL,
    duration_unit                VARCHAR(10)  NOT NULL,
    employee_limit               INT,
    shop_limit                   INT,
    sell_order_limit             INT,
    buy_order_limit              INT,
    service_booking_enabled      BOOLEAN      NOT NULL DEFAULT true,
    pickup_enabled               BOOLEAN      NOT NULL DEFAULT false,
    sms_enabled                  BOOLEAN      NOT NULL DEFAULT false,
    whatsapp_enabled             BOOLEAN      NOT NULL DEFAULT false,
    reports_enabled              BOOLEAN      NOT NULL DEFAULT false,
    export_enabled               BOOLEAN      NOT NULL DEFAULT false,
    priority_support_enabled     BOOLEAN      NOT NULL DEFAULT false,
    additional_shop_discount_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
    status                       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    display_order                INT          NOT NULL DEFAULT 0,
    color                        VARCHAR(20),
    icon                         VARCHAR(60),
    version                      INT          NOT NULL DEFAULT 1,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_plans_code UNIQUE (code),
    CONSTRAINT ck_subscription_plans_price CHECK (price_paise >= 0),
    CONSTRAINT ck_subscription_plans_discount CHECK (additional_shop_discount_pct >= 0 AND additional_shop_discount_pct <= 100),
    CONSTRAINT ck_subscription_plans_duration CHECK (duration_value > 0 AND duration_unit IN ('DAYS', 'MONTHS')),
    CONSTRAINT ck_subscription_plans_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'COMING_SOON')),
    CONSTRAINT ck_subscription_plans_limits CHECK (
        (employee_limit IS NULL OR employee_limit >= 0) AND (shop_limit IS NULL OR shop_limit > 0)
        AND (sell_order_limit IS NULL OR sell_order_limit >= 0) AND (buy_order_limit IS NULL OR buy_order_limit >= 0))
);

CREATE INDEX IF NOT EXISTS idx_subscription_plans_status ON subscription_plans(status, display_order);

-- Seed: prices are integer paise for the WHOLE term, per shop.
INSERT INTO subscription_plans (
    id, code, name, plan_type, description, price_paise, currency, duration_value, duration_unit,
    employee_limit, shop_limit, sell_order_limit, buy_order_limit,
    service_booking_enabled, pickup_enabled, sms_enabled, whatsapp_enabled, reports_enabled,
    export_enabled, priority_support_enabled, additional_shop_discount_pct, status, display_order, color, icon)
VALUES
    ('a1060000-0000-4000-8000-000000000001', 'FREE_TRIAL', 'Free Trial', 'TRIAL',
     'Try NearGFix free for 15 days on up to 2 shops.', 0, 'INR', 15, 'DAYS',
     3, 2, 5, NULL, true, true, false, false, false, false, false, 0, 'ACTIVE', 0, '#64748B', 'gift'),
    ('a1060000-0000-4000-8000-000000000002', 'BASIC_6M', 'Basic', 'BASIC',
     'Everything a repair shop needs, for 6 months.', 100, 'INR', 6, 'MONTHS',
     4, NULL, NULL, NULL, true, true, false, true, false, false, false, 5, 'ACTIVE', 10, '#035C6C', 'store'),
    ('a1060000-0000-4000-8000-000000000003', 'BASIC_12M', 'Basic', 'BASIC',
     'Everything a repair shop needs, for 12 months.', 100, 'INR', 12, 'MONTHS',
     4, NULL, NULL, NULL, true, true, false, true, false, false, false, 5, 'ACTIVE', 20, '#035C6C', 'store'),
    ('a1060000-0000-4000-8000-000000000004', 'PREMIUM_6M', 'Premium', 'PREMIUM',
     'Bigger teams, SMS, reports and priority support, for 6 months.', 100, 'INR', 6, 'MONTHS',
     10, NULL, NULL, NULL, true, true, true, true, true, true, true, 10, 'ACTIVE', 30, '#F97316', 'crown'),
    ('a1060000-0000-4000-8000-000000000005', 'PREMIUM_12M', 'Premium', 'PREMIUM',
     'Bigger teams, SMS, reports and priority support, for 12 months.', 100, 'INR', 12, 'MONTHS',
     10, NULL, NULL, NULL, true, true, true, true, true, true, true, 10, 'ACTIVE', 40, '#F97316', 'crown')
ON CONFLICT (code) DO NOTHING;

-- ── Settings (key/value; NULL value = not set) ──────────────────────────────
CREATE TABLE IF NOT EXISTS subscription_settings (
    setting_key   VARCHAR(64) PRIMARY KEY,
    setting_value TEXT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    UUID
);

INSERT INTO subscription_settings (setting_key, setting_value) VALUES
    ('taxPercent', '0'),
    ('taxInclusive', 'true'),
    ('trialDays', '15'),
    ('trialMaxShops', '2'),
    ('trialOncePerOrg', 'true'),
    ('gracePeriodDays', '0'),
    ('quoteTtlMinutes', '30'),
    ('paymentRetryLimit', '3'),
    ('renewalReminderDays', '7'),
    ('expiryAlertDays', '7'),
    ('webCheckoutEnabled', 'true'),
    ('androidCheckoutEnabled', 'false'),
    ('iosCheckoutEnabled', 'false'),
    ('receiptBusinessName', 'Globogreen System & Technology Private Limited'),
    ('receiptAddress', NULL),
    ('receiptGstin', NULL),
    ('receiptEmail', NULL),
    ('receiptPhone', NULL)
ON CONFLICT (setting_key) DO NOTHING;

-- ── Number sequences (order no / receipt no) ────────────────────────────────
CREATE SEQUENCE IF NOT EXISTS subscription_order_no_seq;
CREATE SEQUENCE IF NOT EXISTS subscription_receipt_no_seq;

-- ── Orders (immutable quote snapshot) ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS subscription_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_no            VARCHAR(40)  NOT NULL,
    owner_user_id       UUID         NOT NULL,
    subscription_id     UUID,
    plan_id             UUID REFERENCES subscription_plans(id),
    plan_code           VARCHAR(50)  NOT NULL,
    plan_name           VARCHAR(120),
    duration_label      VARCHAR(40),
    purpose             VARCHAR(20)  NOT NULL,
    channel             VARCHAR(20)  NOT NULL,
    source              VARCHAR(20)  NOT NULL DEFAULT 'RAZORPAY',
    shop_count          INT          NOT NULL,
    shops_snapshot      JSONB        NOT NULL,
    quote_snapshot      JSONB        NOT NULL,
    plan_snapshot       JSONB        NOT NULL,
    total_paise         BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL DEFAULT 'INR',
    payment_mode        VARCHAR(10)  CHECK (payment_mode IN ('TEST', 'LIVE')),
    status              VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    razorpay_order_id   VARCHAR(64),
    razorpay_payment_id VARCHAR(64),
    payment_method      VARCHAR(30),
    payment_attempts    INT          NOT NULL DEFAULT 0,
    failure_reason      TEXT,
    note                TEXT,
    created_by          UUID,
    expires_at          TIMESTAMPTZ,
    term_starts_at      TIMESTAMPTZ,
    term_ends_at        TIMESTAMPTZ,
    paid_at             TIMESTAMPTZ,
    activated_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_orders_order_no UNIQUE (order_no),
    CONSTRAINT uq_subscription_orders_rzp_order UNIQUE (razorpay_order_id),
    CONSTRAINT ck_subscription_orders_status CHECK (status IN
        ('CREATED', 'AWAITING_PAYMENT', 'PAID', 'ACTIVATED', 'FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_subscription_orders_purpose CHECK (purpose IN ('NEW', 'RENEW', 'UPGRADE', 'TRIAL')),
    CONSTRAINT ck_subscription_orders_channel CHECK (channel IN ('WEB', 'ANDROID', 'IOS', 'ADMIN')),
    CONSTRAINT ck_subscription_orders_amount CHECK (total_paise >= 0 AND shop_count > 0)
);

CREATE INDEX IF NOT EXISTS idx_subscription_orders_owner ON subscription_orders(owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_subscription_orders_status ON subscription_orders(status);
-- TEST | LIVE stamp (RAZORPAY_MODE at creation). Repeated as ADD COLUMN IF NOT EXISTS
-- so a database that ran an earlier draft of this file gets the column too.
ALTER TABLE subscription_orders ADD COLUMN IF NOT EXISTS payment_mode VARCHAR(10)
    CHECK (payment_mode IN ('TEST', 'LIVE'));
CREATE INDEX IF NOT EXISTS idx_subscription_orders_payment_mode ON subscription_orders(payment_mode);

-- At most one unpaid order per owner: a concurrent second purchase fails fast.
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_orders_owner_pending
    ON subscription_orders(owner_user_id) WHERE status IN ('CREATED', 'AWAITING_PAYMENT');

-- The quote an owner agreed to is never edited after the fact.
CREATE OR REPLACE FUNCTION subscription_orders_freeze_quote() RETURNS trigger AS $$
BEGIN
    IF NEW.quote_snapshot IS DISTINCT FROM OLD.quote_snapshot
       OR NEW.plan_snapshot IS DISTINCT FROM OLD.plan_snapshot
       OR NEW.shops_snapshot IS DISTINCT FROM OLD.shops_snapshot
       OR NEW.total_paise IS DISTINCT FROM OLD.total_paise
       OR NEW.payment_mode IS DISTINCT FROM OLD.payment_mode THEN
        RAISE EXCEPTION 'subscription_orders quote snapshot is immutable (order %)', OLD.order_no;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_subscription_orders_freeze_quote ON subscription_orders;
CREATE TRIGGER trg_subscription_orders_freeze_quote
    BEFORE UPDATE ON subscription_orders
    FOR EACH ROW EXECUTE FUNCTION subscription_orders_freeze_quote();

-- ── Payments ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subscription_payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID         NOT NULL REFERENCES subscription_orders(id),
    owner_user_id       UUID         NOT NULL,
    subscription_id     UUID,
    razorpay_order_id   VARCHAR(64),
    razorpay_payment_id VARCHAR(64)  NOT NULL,
    receipt_no          VARCHAR(40)  NOT NULL,
    amount_paise        BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL DEFAULT 'INR',
    payment_mode        VARCHAR(10)  CHECK (payment_mode IN ('TEST', 'LIVE')),
    method              VARCHAR(30),
    vpa                 VARCHAR(120),
    payer_email         VARCHAR(255),
    payer_contact       VARCHAR(50),
    status              VARCHAR(30)  NOT NULL DEFAULT 'CAPTURED',
    activation_status   VARCHAR(30)  NOT NULL DEFAULT 'ACTIVATED',
    refunded_paise      BIGINT       NOT NULL DEFAULT 0,
    last_refund_id      VARCHAR(64),
    refund_reason       TEXT,
    refunded_at         TIMESTAMPTZ,
    paid_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_payments_rzp_payment UNIQUE (razorpay_payment_id),
    CONSTRAINT uq_subscription_payments_receipt_no UNIQUE (receipt_no),
    CONSTRAINT ck_subscription_payments_status CHECK (status IN ('CAPTURED', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    CONSTRAINT ck_subscription_payments_activation CHECK (activation_status IN ('ACTIVATED', 'DUPLICATE_PAYMENT', 'NEEDS_REVIEW')),
    CONSTRAINT ck_subscription_payments_amounts CHECK (amount_paise >= 0 AND refunded_paise >= 0 AND refunded_paise <= amount_paise)
);

CREATE INDEX IF NOT EXISTS idx_subscription_payments_owner ON subscription_payments(owner_user_id, paid_at DESC);
CREATE INDEX IF NOT EXISTS idx_subscription_payments_order ON subscription_payments(order_id);
ALTER TABLE subscription_payments ADD COLUMN IF NOT EXISTS payment_mode VARCHAR(10)
    CHECK (payment_mode IN ('TEST', 'LIVE'));

-- ── Shops covered by a subscription ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subscription_shops (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID        NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    shop_id         UUID        NOT NULL,
    order_id        UUID,
    starts_at       TIMESTAMPTZ,
    ends_at         TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_shops_sub_shop UNIQUE (subscription_id, shop_id),
    CONSTRAINT ck_subscription_shops_status CHECK (status IN ('ACTIVE', 'TRIAL', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_subscription_shops_shop ON subscription_shops(shop_id);

-- ── Audit log ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subscription_audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_user_id UUID,
    actor_role    VARCHAR(50),
    action        VARCHAR(60) NOT NULL,
    entity_type   VARCHAR(40),
    entity_id     VARCHAR(64),
    details       JSONB
);

CREATE INDEX IF NOT EXISTS idx_subscription_audit_logs_at ON subscription_audit_logs(at DESC);
CREATE INDEX IF NOT EXISTS idx_subscription_audit_logs_entity ON subscription_audit_logs(entity_type, entity_id);

-- ── Razorpay webhook events (dedupe by X-Razorpay-Event-Id) ─────────────────
CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider            VARCHAR(20)  NOT NULL DEFAULT 'RAZORPAY',
    event_id            VARCHAR(100) NOT NULL,
    event_type          VARCHAR(60),
    razorpay_order_id   VARCHAR(64),
    razorpay_payment_id VARCHAR(64),
    payload             JSONB,
    processed           BOOLEAN      NOT NULL DEFAULT false,
    processing_error    TEXT,
    attempts            INT          NOT NULL DEFAULT 1,
    received_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ,
    CONSTRAINT uq_payment_webhook_events_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_payment_webhook_events_rzp_order ON payment_webhook_events(razorpay_order_id);

-- ── subscriptions: v2 columns (all nullable; legacy rows untouched) ─────────
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS plan_id       UUID;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS order_id      UUID;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS plan_snapshot JSONB;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS source        VARCHAR(20);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS cancelled_at  TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS cancel_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_subscriptions_plan_code ON subscriptions(plan_code);
