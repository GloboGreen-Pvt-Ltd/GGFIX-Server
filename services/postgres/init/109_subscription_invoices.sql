-- =============================================================================
-- 109_subscription_invoices.sql
--
-- NearGFix subscription invoices / payment receipts (subscription-service
-- invoice package). ONE document per captured, activated payment — enforced by
-- unique(payment_id) and unique(order_id), so a repeated /payment/verify or
-- Razorpay webhook never issues a second one.
--
-- invoice_no = {prefix}/{FY}/{seq:05} (e.g. NGF/26-27/00001, ≤ 16 chars, FY
-- April–March IST) from subscription_invoice_counters: one row per series,
-- incremented with INSERT … ON CONFLICT DO UPDATE … RETURNING inside the same
-- transaction as the invoice insert, so a rollback never leaves a gap. Razorpay
-- TEST-mode payments use the separate series "TEST:{prefix}/{FY}" (NGF/26-27/T0001).
--
-- document_type: TAX_INVOICE when a supplier GSTIN is configured AND taxPercent
-- > 0, otherwise PAYMENT_RECEIPT. view_json is the frozen invoice view; pdf is
-- the rendered A4 PDF (served only to the owner/admin or via a ≤60-minute
-- HMAC-signed link for the WhatsApp document header).
-- whatsapp_status: PENDING | SENT | FAILED | SKIPPED (BotBee). whatsapp_recipient
-- is stored masked only.
--
-- Idempotent (IF NOT EXISTS). Apply BEFORE deploying the subscription-service
-- build that writes it. Without the tables the service still starts (JDBC only,
-- no JPA entity): checkout/activation are unaffected and the recovery sweep
-- issues the missing invoices once the migration has run.
-- =============================================================================

CREATE TABLE IF NOT EXISTS subscription_invoice_counters (
    series      VARCHAR(40)  PRIMARY KEY,
    last_seq    BIGINT       NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS subscription_invoices (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_no           VARCHAR(32)  NOT NULL,
    financial_year       VARCHAR(5)   NOT NULL,
    seq                  BIGINT       NOT NULL,
    payment_id           UUID         NOT NULL REFERENCES subscription_payments(id),
    order_id             UUID         NOT NULL REFERENCES subscription_orders(id),
    owner_user_id        UUID         NOT NULL,
    document_type        VARCHAR(20)  NOT NULL,
    title                VARCHAR(120) NOT NULL,
    payment_mode         VARCHAR(10),
    total_paise          BIGINT       NOT NULL,
    taxable_paise        BIGINT       NOT NULL DEFAULT 0,
    cgst_paise           BIGINT       NOT NULL DEFAULT 0,
    sgst_paise           BIGINT       NOT NULL DEFAULT 0,
    igst_paise           BIGINT       NOT NULL DEFAULT 0,
    tax_paise            BIGINT       NOT NULL DEFAULT 0,
    place_of_supply      VARCHAR(80),
    view_json            TEXT         NOT NULL,
    pdf                  BYTEA,
    pdf_generated_at     TIMESTAMPTZ,
    whatsapp_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    whatsapp_reason      VARCHAR(500),
    whatsapp_message_id  VARCHAR(160),
    whatsapp_recipient   VARCHAR(20),
    whatsapp_attempts    INT          NOT NULL DEFAULT 0,
    whatsapp_claimed_at  TIMESTAMPTZ,
    whatsapp_sent_at     TIMESTAMPTZ,
    issued_at            TIMESTAMPTZ  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_invoices_payment UNIQUE (payment_id),
    CONSTRAINT uq_subscription_invoices_order   UNIQUE (order_id),
    CONSTRAINT uq_subscription_invoices_no      UNIQUE (invoice_no),
    CONSTRAINT ck_subscription_invoices_doc     CHECK (document_type IN ('TAX_INVOICE', 'PAYMENT_RECEIPT')),
    CONSTRAINT ck_subscription_invoices_wa      CHECK (whatsapp_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX IF NOT EXISTS idx_subscription_invoices_owner
    ON subscription_invoices (owner_user_id, issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_subscription_invoices_wa_pending
    ON subscription_invoices (issued_at) WHERE whatsapp_status = 'PENDING';
