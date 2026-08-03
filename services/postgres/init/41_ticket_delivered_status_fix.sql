-- =============================================================================
-- 41_ticket_delivered_status_fix.sql
--
-- Two problems combined to leave fully-delivered tickets stuck on status
-- = READY (which the technician/owner list screens bucket as "In Process"):
--
--   1. The technician detail screen submits work-status key DELIVERED, but
--      master_technician_work_statuses seeded that row as DELIVERED_TO_CUSTOMER
--      — so advanceTicketStatusForWorkCode's lookup returned no row.
--   2. Even with a matching lookup, the master row mapped DELIVERED_TO_CUSTOMER
--      → ticket_status = 'READY', which is wrong: a Delivered-to-Customer
--      event should put the ticket into the terminal DELIVERED bucket.
--
-- The Java fix (TicketService.advanceTicketStatusForWorkCode) now falls back
-- to the code itself when it's a lifecycle status, so future DELIVERED events
-- promote the ticket correctly. This migration:
--
--   * Aligns the master row so the mapping is correct going forward
--     regardless of which code the frontend submits.
--   * Repairs already-delivered tickets whose status is still READY because
--     they were submitted before the Java fix shipped.
-- =============================================================================

BEGIN;

-- Master row mapping correction.
UPDATE master_technician_work_statuses
   SET ticket_status = 'DELIVERED'
 WHERE code = 'DELIVERED_TO_CUSTOMER'
   AND ticket_status <> 'DELIVERED';

-- Promote any ticket that already received a Delivered event but never had
-- its status column advanced. Joins through the platform booking layer the
-- same way emitOrUpdateBookingEvent writes events.
UPDATE tickets t
   SET status = 'DELIVERED'
  FROM repair_bookings b
  JOIN repair_booking_events e ON e.booking_id = b.id
 WHERE b.ticket_id = t.id
   AND UPPER(e.status) IN ('DELIVERED', 'DELIVERED_TO_CUSTOMER')
   AND UPPER(t.status) NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED');

COMMIT;
