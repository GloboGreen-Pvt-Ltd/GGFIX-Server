-- =============================================================================
-- Delete ticket GTS058 and its linked repair booking.
-- Run in pgAdmin Query Tool. Step 1 first, check the rows, then run step 2.
-- tracking_id is unique per shop only — if step 1 shows rows from other
-- shops, uncomment the shop_id filter in the CTE below.
-- =============================================================================

-- ---- Step 1: preview -------------------------------------------------------
SELECT t.id AS ticket_id, t.shop_id, t.tracking_id,
       b.id AS booking_id, b.booking_number, b.status
FROM tickets t
LEFT JOIN repair_bookings b ON b.ticket_id = t.id
WHERE t.tracking_id IN ('GTS058', 'GTS-058');
-- AND t.shop_id = '<shop-uuid>'

-- ---- Step 2: delete --------------------------------------------------------
BEGIN;

CREATE TEMP TABLE _del_tickets ON COMMIT DROP AS
SELECT id FROM tickets
WHERE tracking_id IN ('GTS058', 'GTS-058');
-- AND shop_id = '<shop-uuid>';

CREATE TEMP TABLE _del_bookings ON COMMIT DROP AS
SELECT id, booking_number FROM repair_bookings
WHERE ticket_id IN (SELECT id FROM _del_tickets);

-- Notification tables have no FK to bookings, so clean them explicitly.
DELETE FROM customer_notifications
WHERE booking_id IN (SELECT id FROM _del_bookings);

DELETE FROM shop_notifications
WHERE booking_id IN (SELECT id FROM _del_bookings);

-- repair_booking_services / repair_booking_events cascade.
DELETE FROM repair_bookings WHERE id IN (SELECT id FROM _del_bookings);

-- Ticket child tables cascade or SET NULL.
DELETE FROM tickets WHERE id IN (SELECT id FROM _del_tickets);

COMMIT;   -- replace with ROLLBACK; to dry-run
