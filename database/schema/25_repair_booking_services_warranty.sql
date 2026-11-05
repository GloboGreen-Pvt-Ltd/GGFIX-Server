-- =============================================================================
-- 25_repair_booking_services_warranty.sql
--
-- The pickup-person Repair Estimate flow lets the technician pick a per-service
-- warranty (3/6/12 months). Persist it next to the service row so the customer
-- and shop owner can see exactly what the technician committed to.
-- =============================================================================

BEGIN;

ALTER TABLE repair_booking_services
    ADD COLUMN IF NOT EXISTS warranty VARCHAR(20);

COMMIT;
