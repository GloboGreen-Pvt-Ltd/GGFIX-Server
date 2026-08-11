-- Service History rail: clean up the two auto-derived rows that the timeline
-- used to write, on bookings created before the fix.
--
-- MANUAL, REVIEW-FIRST script. Nothing here runs automatically — read each
-- section, run the SELECT, then run the DELETE if the rows look right.
--
-- Background. Two rows used to appear on the rail without anybody performing
-- the action they describe:
--
--   * "Customer Approved"       — back-filled by TicketService
--     .syncStepEventsFromTicketState on EVERY ticket read, from
--     tickets.customer_approval (or from a status that had advanced past the
--     gate). A shop that ticked the approval box while creating the booking got
--     a green Customer Approved stamped at the first page load, minutes before
--     the technician was even assigned and with "Service Re-estimated" still
--     grey above it.
--
--   * "Repair Work In Progress" — pinned to the exact created_at of
--     "Technician Issue Verified & Updated", so the rail claimed repair work had
--     begun at the minute the technician described the fault, before the
--     customer had seen the re-estimate.
--
-- Both are fixed going forward: approval is written only by a real approval
-- action, and IN_REPAIR only by the technician's own checklist tap. Neither is
-- derived from anything any more. This script deals with rows already in the
-- table.


-- ORDER OF OPERATIONS. Deploy the fixed ticket-service and order-service FIRST.
-- The old code re-creates both rows on the next ticket read — IN_REPAIR at the
-- Issue-Verified instant, CUSTOMER_APPROVED at a fresh now() — so running this
-- against a server still on the old build is undone by one page refresh.


-- =============================================================================
-- 0. Single booking, both rows — the screenshot case
-- =============================================================================
-- Edit the booking number, review, then run. Clears the derived approval and
-- the auto-marked repair row, and resets the approval flags so the customer is
-- re-prompted. After this the technician's Service Progress checklist shows
-- "Repair Work In Progress" as Mark (not DONE) until they tap it themselves.

\set booking_number '''CSPEN7657020'''

SELECT e.status, e.note, e.actor, e.created_at
  FROM repair_booking_events e
  JOIN repair_bookings b ON b.id = e.booking_id
 WHERE b.booking_number = :booking_number
 ORDER BY e.created_at;

-- BEGIN;
-- DELETE FROM repair_booking_events
--  WHERE upper(status) IN ('CUSTOMER_APPROVED', 'IN_REPAIR')
--    AND booking_id = (SELECT id FROM repair_bookings
--                       WHERE booking_number = :booking_number);
-- UPDATE tickets SET customer_approval = NULL
--  WHERE id = (SELECT ticket_id FROM repair_bookings
--               WHERE booking_number = :booking_number);
-- UPDATE repair_bookings SET customer_approval = NULL
--  WHERE booking_number = :booking_number;
-- COMMIT;


-- =============================================================================
-- 1. "Repair Work In Progress" rows produced by the old pairing — SAFE
-- =============================================================================
-- Signature is exact: the old code copied the Issue Verified instant verbatim,
-- so created_at is EQUAL to the microsecond. Nothing else produces that — a
-- technician's own checklist tap stamps its own now().
--
-- Review:

SELECT b.booking_number,
       e.created_at AS repair_row_at,
       v.created_at AS issue_verified_at
  FROM repair_booking_events e
  JOIN repair_bookings b ON b.id = e.booking_id
  JOIN repair_booking_events v
    ON v.booking_id = e.booking_id
   AND upper(v.status) = 'TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED_UPDATED'
 WHERE upper(e.status) = 'IN_REPAIR'
   AND e.created_at = v.created_at
 ORDER BY e.created_at DESC;

-- Delete. The step goes grey until the technician taps "Repair Work In
-- Progress" on their checklist, which is the point — the row then carries the
-- minute work actually started instead of a copied diagnosis timestamp:

-- DELETE FROM repair_booking_events e
--  USING repair_booking_events v
--  WHERE upper(e.status) = 'IN_REPAIR'
--    AND v.booking_id = e.booking_id
--    AND upper(v.status) = 'TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED_UPDATED'
--    AND e.created_at = v.created_at;


-- =============================================================================
-- 2. Derived "Customer Approved" rows — REVIEW EACH ONE, NO BLANKET DELETE
-- =============================================================================
-- These CANNOT be told apart from a genuine customer approval by column values
-- alone. The back-fill wrote actor='USER', note='Customer Approved'; so did the
-- customer app's own Approve tap. And repair_bookings.customer_approval is set
-- to 'DONE' by the mirror whenever tickets.customer_approval is true, whoever
-- set it — so it does not prove the customer tapped anything either.
--
-- What DOES flag a derived row is the ordering: a real approval follows the
-- technician being assigned and the estimate being sent. An approval dated
-- before the assignment is one nobody gave.
--
-- Review the suspects:

SELECT b.booking_number,
       a.created_at  AS approved_at,
       asg.created_at AS assigned_at,
       a.actor,
       a.note
  FROM repair_booking_events a
  JOIN repair_bookings b ON b.id = a.booking_id
  LEFT JOIN repair_booking_events asg
    ON asg.booking_id = a.booking_id
   AND upper(asg.status) = 'ASSIGNED_TO_TECHNICIAN'
 WHERE upper(a.status) = 'CUSTOMER_APPROVED'
   AND asg.created_at IS NOT NULL
   AND a.created_at < asg.created_at
 ORDER BY a.created_at DESC;

-- Then delete only the ones you have confirmed, by booking number:

-- DELETE FROM repair_booking_events
--  WHERE upper(status) = 'CUSTOMER_APPROVED'
--    AND booking_id IN (SELECT id FROM repair_bookings
--                        WHERE booking_number IN ('CSPEN7657020'));

-- Clearing the row alone leaves tickets.customer_approval still true, which the
-- shop app renders as an approved booking. Clear the flag too, so the customer
-- is re-prompted and the rail fills in from the real approval:

-- UPDATE tickets SET customer_approval = NULL
--  WHERE id IN (SELECT ticket_id FROM repair_bookings
--                WHERE booking_number IN ('CSPEN7657020'));
-- UPDATE repair_bookings SET customer_approval = NULL
--  WHERE booking_number IN ('CSPEN7657020');
