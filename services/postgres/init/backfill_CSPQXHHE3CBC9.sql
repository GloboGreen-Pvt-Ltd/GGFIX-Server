-- =============================================================================
-- backfill_CSPQXHHE3CBC9.sql
--
-- One-shot backfill for the legacy pickup booking #CSPQXHHE3CBC9. The mint
-- logic in PickupBookingController.mintTicketFromBooking didn't exist when
-- this row first transitioned to REACHED_SHOP, so it never got a tickets row
-- and therefore never showed up in the shop owner's Bookings History (which
-- reads from `tickets`, not `repair_bookings`).
--
-- This script is idempotent: each step is guarded so re-running on a fixed
-- row is a no-op. Run with `psql -d repairshop -f backfill_CSPQXHHE3CBC9.sql`.
-- =============================================================================

\set ON_ERROR_STOP on

BEGIN;

-- 1. Mint the missing tickets row (if not already linked). Mirrors the
--    INSERT shape used by PickupBookingController.mintTicketFromBooking so
--    the schema-level invariants match a freshly minted ticket.
INSERT INTO tickets (
    id, shop_id, customer_id, customer_name, customer_phone,
    tracking_id, brand_id, model_id, ram_option_id, storage_option_id,
    color, status, estimated_price, issue_description,
    device_display_name, device_image_url, repair_services_summary,
    device_security_value, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    rb.shop_id,
    rb.customer_user_id,
    COALESCE(rb.customer_name, cu.full_name, 'Customer'),
    COALESCE(rb.customer_mobile, cu.mobile),
    'CSPEN' || LPAD((EXTRACT(EPOCH FROM now())::BIGINT % 10000000)::TEXT, 7, '0'),
    rb.brand_id, rb.model_id, rb.ram_option_id, rb.storage_option_id,
    rb.color, 'IN_DIAGNOSIS', rb.estimate_amount,
    NULLIF(REGEXP_REPLACE(COALESCE(rb.issue_summary, ''), '---PICKUP_ESTIMATE_META---.*$', '', 'g'), ''),
    COALESCE(mb.name, '') ||
        CASE WHEN mb.name IS NOT NULL AND mm.name IS NOT NULL THEN ' ' ELSE '' END ||
        COALESCE(mm.name, ''),
    mm.image_url,
    (SELECT STRING_AGG(service_name, ', ' ORDER BY created_at)
       FROM repair_booking_services WHERE booking_id = rb.id),
    rb.device_pin,
    now(), now()
FROM repair_bookings rb
LEFT JOIN customer_users cu ON cu.id = rb.customer_user_id
LEFT JOIN master_brands mb ON mb.id = rb.brand_id
LEFT JOIN master_models mm ON mm.id = rb.model_id
WHERE rb.booking_number = '#CSPQXHHE3CBC9'
  AND rb.ticket_id IS NULL;

-- 2. Backlink the booking to the newly minted ticket. The WHERE clause keeps
--    this idempotent — if a prior run already minted (or a future call adds
--    its own link), we don't clobber the existing reference.
UPDATE repair_bookings rb
   SET ticket_id = (
        SELECT t.id FROM tickets t
         WHERE t.shop_id = rb.shop_id
           AND t.customer_id = rb.customer_user_id
         ORDER BY t.created_at DESC
         LIMIT 1
       ),
       updated_at = now()
 WHERE rb.booking_number = '#CSPQXHHE3CBC9'
   AND rb.ticket_id IS NULL;

-- 3. Advance the booking to RECEIVED_AT_SHOP and stamp the milestone. Pickup
--    person had already pinged REACHED_SHOP twice; the shop side just never
--    completed the hand-off (the endpoint didn't exist). Treat the most
--    recent REACHED_SHOP time as the de-facto received time.
UPDATE repair_bookings
   SET status = 'RECEIVED_AT_SHOP',
       received_at_shop_at = COALESCE(received_at_shop_at, reached_shop_at, now()),
       received_by_user_name = COALESCE(received_by_user_name, 'Shop Staff (backfill)'),
       updated_at = now()
 WHERE booking_number = '#CSPQXHHE3CBC9'
   AND status <> 'RECEIVED_AT_SHOP';

-- 4. Drop a single RECEIVED_AT_SHOP event so the timeline ends at the real
--    final state. Skipped if an equivalent event already exists.
INSERT INTO repair_booking_events (id, booking_id, status, note, actor, created_at)
SELECT gen_random_uuid(), rb.id, 'RECEIVED_AT_SHOP',
       'Received at shop (backfill reconciliation)', 'SHOP_STAFF', now()
  FROM repair_bookings rb
 WHERE rb.booking_number = '#CSPQXHHE3CBC9'
   AND NOT EXISTS (
        SELECT 1 FROM repair_booking_events e
         WHERE e.booking_id = rb.id AND e.status = 'RECEIVED_AT_SHOP'
   );

-- 5. Mirror the customer-facing macro status. The customer's My Orders →
--    Pickup card reads customer_orders.status; it's been stuck on PENDING
--    since the original /repair-bookings insert, so the customer still sees
--    "PENDING" + "Re-Schedule" on a device that's already on the bench.
UPDATE customer_orders
   SET status = 'IN_PROGRESS', updated_at = now()
 WHERE order_number = '#CSPQXHHE3CBC9'
   AND status = 'PENDING';

COMMIT;

-- Sanity check — re-read the row so the operator can confirm the new shape.
SELECT booking_number, status, ticket_id, received_at_shop_at, received_by_user_name
  FROM repair_bookings WHERE booking_number = '#CSPQXHHE3CBC9';

SELECT order_number, status FROM customer_orders WHERE order_number = '#CSPQXHHE3CBC9';
