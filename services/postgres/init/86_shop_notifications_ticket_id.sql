-- =============================================================================
-- 86_shop_notifications_ticket_id.sql
--
-- The shop feed only ever carried booking_id / booking_number, so a
-- notification could not open the job it referred to — the owner app fell back
-- to dumping the user on the Bookings tab. Service History (BookingTimeline)
-- is keyed by tickets.id, so carry the ticket id on the notification row and
-- the tap can land on the exact timeline.
--
-- Nullable on purpose: pickup notifications raised before a ticket is minted
-- (and every pre-existing row) have no ticket to point at.
--
-- Idempotent. Safe to re-run.
-- =============================================================================

BEGIN;

-- Fresh installs: the table was first created outside migrations (no earlier
-- CREATE). Same shape as the ShopNotification entity; no-op where it exists.
CREATE TABLE IF NOT EXISTS shop_notifications (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id        UUID NOT NULL,
    booking_id     UUID,
    booking_number VARCHAR(60),
    status_key     VARCHAR(100),
    title          VARCHAR(200) NOT NULL,
    body           TEXT,
    type           VARCHAR(30),
    is_read        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_shop_notifications_shop ON shop_notifications(shop_id, created_at DESC);

ALTER TABLE shop_notifications
    ADD COLUMN IF NOT EXISTS ticket_id UUID;

COMMIT;
