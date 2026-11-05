-- =============================================================================
-- 99_ticket_booking_devices.sql
--
-- One customer booking, several devices: every device stays its own ticket
-- (own details, issues, status, technician, history) but shares one parent
-- booking number from the partner sequence (98_partner_ticket_numbers.sql):
--
--   1 device   GTS-001
--   2 devices  GTS-001-A, GTS-001-B
--   added later to an existing booking -> next free suffix (GTS-001-C …);
--   existing tracking ids are never renamed.
--
-- tickets.booking_number  parent number shared by every device of a booking
-- tickets.device_suffix   A, B, C … when the booking has more than one device
--
-- Existing tickets stay NULL in both columns; ticket-service treats a NULL
-- booking_number as "its own tracking_id". Uniqueness of the per-device ids is
-- still enforced by uq_tickets_partner_tracking_id.
--
-- Idempotent. Apply BEFORE deploying ticket-service (ddl-auto: validate).
-- =============================================================================

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS booking_number VARCHAR(50);

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS device_suffix VARCHAR(4);

CREATE INDEX IF NOT EXISTS idx_tickets_partner_booking_number
    ON tickets (partner_id, booking_number);

-- A suffix can't repeat inside one booking.
CREATE UNIQUE INDEX IF NOT EXISTS uq_tickets_partner_booking_suffix
    ON tickets (partner_id, booking_number, device_suffix);
