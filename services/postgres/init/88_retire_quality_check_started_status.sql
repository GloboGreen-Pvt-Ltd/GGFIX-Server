-- =============================================================================
-- 88_retire_quality_check_started_status.sql
--
-- Collapses the two quality-check service statuses into one:
--
--     QUALITY_CHECK_STARTED    "Quality Check Pending"    ┐
--                                                         ├─► QUALITY_CHECK_COMPLETED
--     QUALITY_CHECK_COMPLETED  "Quality Check Completed"  ┘   "Quality Check Completed"
--
-- Same shape as migration 87 (spare parts): the surviving CODE is unchanged, so
-- no completed-check row has to be rewritten and any client build still posting
-- it keeps working. Only the retired half goes.
--
-- There is no "pending" step any more. The completion event IS the record: its
-- created_at is stamped server-side by TicketService#emitOrUpdateBookingEvent
-- at the instant the technician marks the check done, so the Quality Check
-- Completed timestamp is produced automatically by that action — nothing has to
-- be entered by hand, and nothing needs a second status to close.
--
-- QUALITY_CHECK_STARTED history rows are DELETED rather than converted: turning
-- a "check started" row into "check completed" would claim a check passed at the
-- moment it merely began. ticket-service has already dropped the code from
-- ALLOWED_PROGRESS_STEP_KEYS, so nothing can write it back after this runs.
--
-- Note on ticket.status: QUALITY_CHECK_STARTED mapped to IN_REPAIR and
-- QUALITY_CHECK_COMPLETED maps to READY in master_technician_work_statuses.
-- Dropping the started row does NOT strand anything — a ticket that only ever
-- reached "started" is still IN_REPAIR, which is exactly where a device whose
-- quality check has not been completed belongs.
--
-- Idempotent. Safe to re-run.
-- =============================================================================

BEGIN;

-- 1. Service History timeline rows. Destructive: drops the "Quality Check
--    Pending" step from every booking's rail. Bookings that had both events
--    keep the completed one, which is the row that carries the real timestamp.
DELETE FROM repair_booking_events
 WHERE upper(status) = 'QUALITY_CHECK_STARTED';

-- 2. Master work-status dropdown (admin-managed; drives
--    advanceTicketStatusForWorkCode and the owner's work-status picker).
DELETE FROM master_technician_work_statuses
 WHERE code = 'QUALITY_CHECK_STARTED';

-- 3. Canned note text on surviving rows. Free-form technician notes are left
--    alone — only the exact old label strings are rewritten, the same rule
--    migrations 61 and 87 used.
UPDATE repair_booking_events
   SET note = 'Quality Check Completed'
 WHERE upper(status) = 'QUALITY_CHECK_COMPLETED'
   AND note IN ('Quality Check Pending', 'Quality Check Started');

-- 4. Notification feeds. Only QUALITY_CHECK_COMPLETED ever had a template, so
--    this is a defensive sweep for rows raised by an older build.
DELETE FROM customer_notifications WHERE upper(status_key) = 'QUALITY_CHECK_STARTED';
DELETE FROM shop_notifications     WHERE upper(status_key) = 'QUALITY_CHECK_STARTED';

-- 5. Defensive: no lifecycle column should hold a work-status code
--    (LIFECYCLE_ORDER has no QUALITY_CHECK_* entry), but a hand-edited or
--    legacy row would strand a booking on a status the apps can no longer
--    render. Park a stranded "started" row on IN_REPAIR; a stranded
--    "completed" row on READY, which is what that code maps to in master.
UPDATE tickets          SET status = 'IN_REPAIR' WHERE upper(status) = 'QUALITY_CHECK_STARTED';
UPDATE repair_bookings  SET status = 'IN_REPAIR' WHERE upper(status) = 'QUALITY_CHECK_STARTED';
UPDATE customer_orders  SET status = 'IN_REPAIR' WHERE upper(status) = 'QUALITY_CHECK_STARTED';

UPDATE tickets          SET status = 'READY' WHERE upper(status) = 'QUALITY_CHECK_COMPLETED';
UPDATE repair_bookings  SET status = 'READY' WHERE upper(status) = 'QUALITY_CHECK_COMPLETED';
UPDATE customer_orders  SET status = 'READY' WHERE upper(status) = 'QUALITY_CHECK_COMPLETED';

COMMIT;
