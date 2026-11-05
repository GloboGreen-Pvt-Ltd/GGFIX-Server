-- =============================================================================
-- 103_ticket_original_delivery.sql
--
-- The booking's FIRST delivery promise. A re-estimate overwrites
-- tickets.estimated_delivery_at with the revised one; this keeps the original
-- so the Partner app's Device Details can show it under Service Schedule and
-- the revised one under Re-Estimated Summary. Written once, on the first
-- re-estimate; NULL for bookings never re-estimated (or re-estimated before
-- this column existed — that earlier value is gone).
--
-- Idempotent. Apply BEFORE deploying ticket-service (ddl-auto: validate).
-- Numbered 103 because 99–102 are taken by pending branches; it only touches
-- tickets (01_schema.sql), so its place in the init order doesn't matter.
-- =============================================================================

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS original_estimated_delivery_at TIMESTAMPTZ;
