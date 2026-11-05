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

ALTER TABLE shop_notifications
    ADD COLUMN IF NOT EXISTS ticket_id UUID;

COMMIT;
