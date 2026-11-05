-- =============================================================================
-- 100_ticket_serial_number.sql
--
-- Serial number for non-mobile repair bookings (Laptop, Tablet, Smartwatch,
-- Audio Device). Phones keep using tickets.imei; this is the same kind of
-- identifier for devices that have no IMEI. Optional — NULL when not captured.
--
-- Also accessories: what was handed over with the device at booking
-- (charger, case, SIM tray …). Free text, optional.
--
-- Idempotent. Apply BEFORE deploying ticket-service (ddl-auto: validate).
-- Numbered 100 because 99 is taken (99_ticket_booking_devices.sql); it only touches
-- tickets (01_schema.sql), so its place in the init order doesn't matter.
-- =============================================================================

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS serial_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS accessories   VARCHAR(500);
