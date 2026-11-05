-- =============================================================================
-- backfill_CSPQXGSB9SPFB.sql
--
-- One-shot backfill for pickup booking #CSPQXGSB9SPFB → ticket CSPEN4436693.
--
-- Symptom: shop-owner Bookings History card shows Customer "-" and the
-- Booking Details / Device Details screens render an empty Name row, even
-- though the linked customer_users row (id 9f52e0e8-...-5615a890) has
-- full_name='Nandha' and mobile='8939615914'.
--
-- Root cause: the booking was inserted with NULL customer_name / customer_mobile
-- and the mint ran against a service build that didn't yet COALESCE to
-- customer_users.full_name / .mobile. Result: tickets.customer_name and
-- tickets.customer_phone landed as NULL even though the data was reachable
-- one JOIN away.
--
-- Two other Device Details sections on the same screen render empty
-- (Service Schedule / Device Photos / Device Security). Those are *not* a
-- data bug — the customer-side pickup flow doesn't capture estimated_ready_at,
-- estimated_delivery_at, front/back/video URLs, or device_pin. They get
-- filled when the owner uses the "Edit this booking" shortcut to walk the
-- service-booking wizard. This script does not touch them.
--
-- Idempotent: every UPDATE is guarded with "WHERE the field is still blank".
-- Run with: psql -d repairshop -f backfill_CSPQXGSB9SPFB.sql
-- =============================================================================

\set ON_ERROR_STOP on

BEGIN;

-- 1. Repair the source booking row. Re-mints (or any future code that joins
--    repair_bookings directly) will see the denormalized columns the
--    order-service create flow was supposed to write.
UPDATE repair_bookings rb
   SET customer_name   = COALESCE(NULLIF(TRIM(rb.customer_name), ''),   cu.full_name),
       customer_mobile = COALESCE(NULLIF(TRIM(rb.customer_mobile), ''), cu.mobile),
       updated_at      = now()
  FROM customer_users cu
 WHERE rb.booking_number = '#CSPQXGSB9SPFB'
   AND cu.id = rb.customer_user_id
   AND (NULLIF(TRIM(rb.customer_name), '')   IS NULL
        OR NULLIF(TRIM(rb.customer_mobile), '') IS NULL);

-- 2. Repair the already-minted ticket so the Bookings History card and the
--    owner-side Booking Details screen pick up the customer immediately —
--    without waiting for a re-mint that won't happen on an existing row.
UPDATE tickets t
   SET customer_name  = COALESCE(NULLIF(TRIM(t.customer_name), ''),  cu.full_name),
       customer_phone = COALESCE(NULLIF(TRIM(t.customer_phone), ''), cu.mobile),
       updated_at     = now()
  FROM customer_users cu
 WHERE t.tracking_id = 'CSPEN4436693'
   AND cu.id = t.customer_id
   AND (NULLIF(TRIM(t.customer_name), '')  IS NULL
        OR NULLIF(TRIM(t.customer_phone), '') IS NULL);

COMMIT;

-- Sanity check — re-read both rows so the operator can confirm the new shape.
SELECT booking_number, status, customer_name, customer_mobile, ticket_id
  FROM repair_bookings WHERE booking_number = '#CSPQXGSB9SPFB';

SELECT tracking_id, status, customer_name, customer_phone
  FROM tickets WHERE tracking_id = 'CSPEN4436693';
