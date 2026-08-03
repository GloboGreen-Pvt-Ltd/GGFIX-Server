-- =============================================================================
-- 45_pickup_received_by_staff.sql
--
-- Adds columns recording which shop staff member confirmed "Received at Shop"
-- on a pickup booking. Migration 44 added received_at_shop_at; this one adds
-- the actor so the booking-history timeline can render "Received by <name>".
--
--   received_by_user_id    — users.id of the shop staff who tapped Received
--   received_by_user_name  — denormalized display name (avoids a users join
--                            on every read of the booking history)
--
-- Idempotent (IF NOT EXISTS) so re-running on an already-migrated DB is safe.
-- =============================================================================

ALTER TABLE repair_bookings
    ADD COLUMN IF NOT EXISTS received_by_user_id    UUID,
    ADD COLUMN IF NOT EXISTS received_by_user_name  VARCHAR(255);
