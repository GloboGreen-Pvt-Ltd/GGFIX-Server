-- ═══════════════════════════════════════════════════════════════════════════
-- Backfill: bookings re-estimated BEFORE TicketService.markReEstimated() existed
--
-- TicketService.update() used to record a re-estimate as a repair_booking_events
-- row (status = 'RE_ESTIMATED_CONFIRMED') and nothing else — tickets.status was
-- never moved. The owner app's Re-Estimated tile filters on status = 'QUOTED'
-- (bookingScopes.js), so it counted 0 and the card badge still read
-- "Service Accepted", while the Service History screen — which reads events —
-- correctly showed "Service Re-estimated". #CSPEN5544270 is one such booking.
--
-- Run ONCE, after deploying the ticket-service fix. Idempotent: re-running it
-- is a no-op because every affected row is already at QUOTED.
--
-- Guard matches markReEstimated() exactly: only CREATED / IN_DIAGNOSIS /
-- APPROVED / IN_REPAIR are walked to QUOTED. READY and beyond are past the
-- re-quote point (a price change there is a billing adjustment, and demoting
-- would unwind the invoice/handover chain); CANCELLED / RETURNED are terminal.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── 1. Preview — run this first and eyeball the list ───────────────────────
SELECT t.tracking_id,
       t.status                AS current_status,
       max(e.created_at)       AS re_estimated_at,
       rb.booking_number
FROM tickets t
JOIN repair_bookings       rb ON rb.ticket_id = t.id
JOIN repair_booking_events e  ON e.booking_id = rb.id
WHERE upper(e.status) = 'RE_ESTIMATED_CONFIRMED'
  AND upper(coalesce(t.status, 'CREATED'))
      IN ('CREATED', 'IN_DIAGNOSIS', 'APPROVED', 'IN_REPAIR')
GROUP BY t.tracking_id, t.status, rb.booking_number
ORDER BY 3 DESC;

-- ── 2. Apply ──────────────────────────────────────────────────────────────
BEGIN;

UPDATE tickets t
SET status     = 'QUOTED',
    updated_at = now()
WHERE upper(coalesce(t.status, 'CREATED'))
      IN ('CREATED', 'IN_DIAGNOSIS', 'APPROVED', 'IN_REPAIR')
  AND EXISTS (
        SELECT 1
        FROM repair_bookings       rb
        JOIN repair_booking_events e ON e.booking_id = rb.id
        WHERE rb.ticket_id = t.id
          AND upper(e.status) = 'RE_ESTIMATED_CONFIRMED');

-- Keep the booking mirror in step. CustomerOrderMirrorService maps ticket
-- QUOTED -> repair_bookings.status 'QUOTED'; the runtime path re-mirrors, but a
-- direct SQL backfill has to do it by hand.
--
-- customer_orders deliberately needs no update: every source status above and
-- QUOTED itself all mirror to customer_orders.status = 'PENDING', so that table
-- is already correct.
UPDATE repair_bookings rb
SET status     = 'QUOTED',
    updated_at = now()
FROM tickets t
WHERE rb.ticket_id = t.id
  AND upper(t.status) = 'QUOTED'
  AND upper(coalesce(rb.status, '')) <> 'QUOTED'
  AND EXISTS (
        SELECT 1
        FROM repair_booking_events e
        WHERE e.booking_id = rb.id
          AND upper(e.status) = 'RE_ESTIMATED_CONFIRMED');

COMMIT;

-- ── 3. Verify ─────────────────────────────────────────────────────────────
-- Expect one row per re-estimated booking, all showing QUOTED / QUOTED.
SELECT t.tracking_id, t.status AS ticket_status, rb.status AS booking_status
FROM tickets t
JOIN repair_bookings rb ON rb.ticket_id = t.id
WHERE EXISTS (SELECT 1 FROM repair_booking_events e
              WHERE e.booking_id = rb.id
                AND upper(e.status) = 'RE_ESTIMATED_CONFIRMED')
ORDER BY t.tracking_id;
