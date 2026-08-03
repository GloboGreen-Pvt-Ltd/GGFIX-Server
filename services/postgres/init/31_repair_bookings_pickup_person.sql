-- =============================================================================
-- 31_repair_bookings_pickup_person.sql
--
-- Adds pickup-person assignment columns to repair_bookings. Mirrors the pattern
-- used for assigned_technician_id (migration 29): the order-service writes the
-- agent id + denormalized name when the shop assigns a pickup person, and the
-- customer's Pickup Status timeline reads the resulting repair_booking_events
-- row (status = 'PICKUP_ASSIGNED').
-- =============================================================================

ALTER TABLE repair_bookings
    ADD COLUMN IF NOT EXISTS assigned_pickup_person_id UUID,
    ADD COLUMN IF NOT EXISTS pickup_person_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS pickup_person_phone VARCHAR(30);
