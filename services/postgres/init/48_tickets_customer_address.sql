-- =============================================================================
-- 48_tickets_customer_address.sql
--
-- Adds tickets.customer_address so the shop-owner Booking Details / Device
-- Details screens can render the pickup address that the customer entered
-- when they created the pickup booking. Without this column the screen
-- shows a blank Address row even though customer_addresses already has the
-- data via repair_bookings.pickup_address_id.
--
-- Idempotent — uses ADD COLUMN IF NOT EXISTS.
-- =============================================================================

BEGIN;

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS customer_address TEXT;

-- Backfill already-minted tickets that have a linked repair_booking with a
-- pickup_address_id. Constructs the same single-line address text the
-- ticket-mint code path now writes for new tickets. customer_addresses uses
-- a single address_line column (see schema.sql:38), plus locality/city/state/
-- pincode — concat them in that order with comma+space separators, then
-- strip leading/trailing punctuation that empty fields would leave behind.
UPDATE tickets t
SET    customer_address = trimmed.addr,
       updated_at       = now()
FROM (
    SELECT rb.ticket_id AS tid,
           NULLIF(TRIM(BOTH ', ' FROM CONCAT_WS(', ',
               NULLIF(TRIM(ca.address_line), ''),
               NULLIF(TRIM(ca.locality), ''),
               NULLIF(TRIM(ca.city), ''),
               NULLIF(TRIM(ca.state), ''),
               NULLIF(TRIM(ca.pincode), '')
           )), '') AS addr
    FROM repair_bookings rb
    JOIN customer_addresses ca ON ca.id = rb.pickup_address_id
    WHERE rb.ticket_id IS NOT NULL
) AS trimmed
WHERE t.id = trimmed.tid
  AND trimmed.addr IS NOT NULL
  AND (t.customer_address IS NULL OR t.customer_address = '');

COMMIT;
