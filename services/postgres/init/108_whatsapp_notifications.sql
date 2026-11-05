-- =============================================================================
-- 108_whatsapp_notifications.sql
--
-- WhatsApp booking confirmations (ticket-service whatsapp package, BotBee).
-- One row per booking event: a Partner App booking (GTS-001, or all devices of
-- GTS-001-A/B…) gets ONE "BOOKING_CONFIRMED" message — enforced by the unique
-- key, so a double tap, a retry or a second device never sends twice.
--
-- The row is the outbox AND the history: ticket-service inserts it after the
-- booking commits, a scheduled worker sends it (with backoff retries), and
-- delivery-status polling (BotBee message/status) moves it to DELIVERED / READ. history_json is the
-- append-only audit trail. recipient_phone is E.164; logs only ever mask it.
--
-- Apply BEFORE deploying the ticket-service build that writes it. Without the
-- table the service still starts (JDBC only, no JPA entity) and just logs that
-- the WhatsApp row could not be saved — bookings are never affected.
-- =============================================================================

CREATE TABLE IF NOT EXISTS whatsapp_notifications (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id              UUID         NOT NULL,
    booking_number       VARCHAR(40)  NOT NULL,
    ticket_id            UUID         NOT NULL,
    event_type           VARCHAR(40)  NOT NULL,
    template_name        VARCHAR(100) NOT NULL,
    recipient_phone      VARCHAR(20),
    opt_in               BOOLEAN      NOT NULL DEFAULT false,
    status               VARCHAR(20)  NOT NULL,
    status_reason        VARCHAR(500),
    attempts             INT          NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMPTZ,
    variables_json       TEXT,
    media_key            VARCHAR(300),
    media_deleted_at     TIMESTAMPTZ,
    provider_message_id  VARCHAR(160),
    provider_response    TEXT,
    resend_count         INT          NOT NULL DEFAULT 0,
    history_json         TEXT         NOT NULL DEFAULT '[]',
    sent_at              TIMESTAMPTZ,
    delivered_at         TIMESTAMPTZ,
    read_at              TIMESTAMPTZ,
    status_checked_at    TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_whatsapp_notifications_event UNIQUE (shop_id, booking_number, event_type),
    CONSTRAINT ck_whatsapp_notifications_status CHECK (status IN
        ('PENDING', 'SENDING', 'SENT', 'DELIVERED', 'READ', 'FAILED', 'SKIPPED'))
);

-- Worker scan: due PENDING rows.
CREATE INDEX IF NOT EXISTS idx_whatsapp_notifications_due
    ON whatsapp_notifications (next_attempt_at) WHERE status = 'PENDING';
-- Delivery-status polling: sent / delivered rows not yet read.
CREATE INDEX IF NOT EXISTS idx_whatsapp_notifications_poll
    ON whatsapp_notifications (sent_at) WHERE status IN ('SENT', 'DELIVERED');
-- Receipt-image cleanup.
CREATE INDEX IF NOT EXISTS idx_whatsapp_notifications_media
    ON whatsapp_notifications (created_at) WHERE media_key IS NOT NULL AND media_deleted_at IS NULL;
