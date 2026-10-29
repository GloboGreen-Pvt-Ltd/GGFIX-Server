-- =============================================================================
-- 104_booking_drafts.sql
--
-- Server-side booking drafts for the owner app's New Booking wizard
-- (ticket-service BookingDraftController, public path /ticket/booking-drafts).
-- One row per client-generated draft id, scoped to a shop. Soft-deleted:
-- discard / submit only flip status, rows are kept.
--
-- payload / ticket_ids hold JSON as TEXT (same DDL works on the H2 dev profile).
-- ticket-service has NO JPA entity for this table (JdbcTemplate only), so
-- `ddl-auto: validate` never depends on it; DatabaseSchemaInitializer also runs
-- this exact DDL at boot. Idempotent.
-- =============================================================================

CREATE TABLE IF NOT EXISTS booking_drafts (
    id              UUID PRIMARY KEY,
    shop_id         UUID NOT NULL,
    created_by      UUID NOT NULL,
    updated_by      UUID,
    status          VARCHAR(20) NOT NULL,
    current_step    VARCHAR(60),
    step_label      VARCHAR(80),
    customer_name   VARCHAR(160),
    customer_mobile VARCHAR(30),
    device_summary  VARCHAR(255),
    device_count    INT NOT NULL DEFAULT 0,
    payload         TEXT NOT NULL,
    version         INT NOT NULL,
    ticket_ids      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_booking_drafts_status CHECK (status IN ('ACTIVE', 'SUBMITTED', 'DISCARDED'))
);

CREATE INDEX IF NOT EXISTS idx_booking_drafts_shop_status_updated
    ON booking_drafts (shop_id, status, updated_at DESC);
