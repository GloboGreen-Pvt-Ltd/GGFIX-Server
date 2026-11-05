-- =============================================================================
-- 47_backfill_ticket_customer_from_users.sql
--
-- Backfills tickets.customer_name / customer_phone and the still-NULL
-- repair_bookings.customer_name / customer_mobile from customer_users.
--
-- Why this exists:
--   * Customer-side pickup bookings (order-service RepairBookingController.create)
--     historically stored only customer_user_id and left customer_name /
--     customer_mobile NULL on repair_bookings.
--   * When the shop received the pickup, mintTicketFromBooking
--     (ticket-service) copied those NULL columns straight into
--     tickets.customer_name / customer_phone, so the owner-side Bookings
--     History card rendered "Customer: -" with no mobile.
--   * The forward-fix is in the code path; this migration cleans up the
--     historical rows that already shipped to production.
--
-- Idempotent: only touches rows where the destination is NULL/empty.
-- =============================================================================

BEGIN;

-- 1) Backfill repair_bookings from customer_users for any row that's still
--    missing the denormalized columns (migration 30 backfilled once but new
--    rows kept landing NULL until the entity was extended).
UPDATE repair_bookings rb
SET    customer_name   = COALESCE(NULLIF(rb.customer_name, ''),   cu.full_name),
       customer_mobile = COALESCE(NULLIF(rb.customer_mobile, ''), cu.mobile),
       updated_at      = now()
FROM   customer_users cu
WHERE  rb.customer_user_id = cu.id
  AND  (
       rb.customer_name   IS NULL OR rb.customer_name   = ''
    OR rb.customer_mobile IS NULL OR rb.customer_mobile = ''
  );

-- 2) Backfill tickets from repair_bookings (preferred — already a per-booking
--    snapshot) and fall back to customer_users where the booking row itself
--    is still blank for some legacy reason.
UPDATE tickets t
SET    customer_name  = COALESCE(NULLIF(t.customer_name, ''),  rb.customer_name, cu.full_name),
       customer_phone = COALESCE(NULLIF(t.customer_phone, ''), rb.customer_mobile, cu.mobile),
       updated_at     = now()
FROM   repair_bookings rb
LEFT JOIN customer_users cu ON cu.id = rb.customer_user_id
WHERE  rb.ticket_id = t.id
  AND  (
       t.customer_name  IS NULL OR t.customer_name  = ''
    OR t.customer_phone IS NULL OR t.customer_phone = ''
  );

-- 3) Tickets that were minted standalone (no linked repair_booking, e.g.
--    walk-in tickets created directly via TicketService.create with a
--    customer_id) get patched from customer_users when t.customer_id
--    happens to point at a platform customer_users row.
UPDATE tickets t
SET    customer_name  = COALESCE(NULLIF(t.customer_name, ''),  cu.full_name),
       customer_phone = COALESCE(NULLIF(t.customer_phone, ''), cu.mobile),
       updated_at     = now()
FROM   customer_users cu
WHERE  cu.id = t.customer_id
  AND  (
       t.customer_name  IS NULL OR t.customer_name  = ''
    OR t.customer_phone IS NULL OR t.customer_phone = ''
  );

COMMIT;
