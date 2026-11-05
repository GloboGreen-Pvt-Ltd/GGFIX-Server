-- =============================================================================
-- 29_repair_bookings_assigned_tech.sql
--
-- Adds assigned_technician_id to repair_bookings so the ticket-service mirror
-- can detect technician-assignment transitions (first assign vs re-assign) and
-- emit timeline events the customer's Service History reads.
-- =============================================================================

ALTER TABLE repair_bookings
    ADD COLUMN IF NOT EXISTS assigned_technician_id UUID;
