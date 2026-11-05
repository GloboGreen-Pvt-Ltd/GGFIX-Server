-- =============================================================================
-- 87_retire_parts_replaced_status.sql
--
-- Collapses the two spare-parts service statuses into one:
--
--     PARTS_REQUIRED  "Spare Parts Pending"   ┐
--                                             ├─►  PARTS_REQUIRED  "Spare Parts Waiting"
--     PARTS_REPLACED  "Spare Parts Replaced"  ┘        (the only spare-parts status)
--
-- The surviving CODE is PARTS_REQUIRED — unchanged, so no existing timeline row
-- has to be rewritten and any client build still posting it keeps working. Only
-- the display label is standardised, everywhere, to "Spare Parts Waiting".
--
-- PARTS_REPLACED is removed outright: the technician now records the outcome in
-- the note on the single waiting row instead of emitting a second status. Its
-- history rows are DELETED rather than converted — a row that meant "parts were
-- replaced" must not start reading "waiting" at the moment work actually
-- finished. ticket-service has already dropped the code from
-- ALLOWED_PROGRESS_STEP_KEYS, so nothing can write it back after this runs.
--
-- Ordering note: this file sorts before seed_technician_work_statuses_v2.sql in
-- a fresh init, which is fine — that seed no longer inserts PARTS_REPLACED and
-- already carries the new label, so the end state is the same either way.
--
-- Idempotent. Safe to re-run.
-- =============================================================================

BEGIN;

-- 1. Service History timeline rows. This is the destructive step: it drops the
--    "Spare Parts Replaced" step from every booking's rail. Bookings that had
--    both events keep the PARTS_REQUIRED one, which is the row the timeline
--    renders as "Spare Parts Waiting".
DELETE FROM repair_booking_events
 WHERE upper(status) = 'PARTS_REPLACED';

-- 2. Master work-status dropdown (admin-managed; drives
--    advanceTicketStatusForWorkCode and the owner's work-status picker).
DELETE FROM master_technician_work_statuses
 WHERE code = 'PARTS_REPLACED';

UPDATE master_technician_work_statuses
   SET label = 'Spare Parts Waiting'
 WHERE code  = 'PARTS_REQUIRED'
   AND label <> 'Spare Parts Waiting';

-- 3. Canned note text on surviving rows. Free-form technician notes ("Display
--    on order, ETA Tuesday") are left alone — only the exact old label strings
--    are rewritten, the same rule migration 61 used.
UPDATE repair_booking_events
   SET note = 'Spare Parts Waiting'
 WHERE upper(status) = 'PARTS_REQUIRED'
   AND note IN ('Spare Parts Pending', 'Parts Required');

-- 4. Notification feeds. Rows for the retired status point at a step that no
--    longer exists anywhere in the apps, and the surviving titles must not keep
--    the old wording.
DELETE FROM customer_notifications WHERE upper(status_key) = 'PARTS_REPLACED';
DELETE FROM shop_notifications     WHERE upper(status_key) = 'PARTS_REPLACED';

UPDATE shop_notifications
   SET title = 'Spare parts waiting'
 WHERE upper(status_key) = 'PARTS_REQUIRED'
   AND title = 'Spare parts pending';

-- 5. Defensive: no lifecycle column should ever hold a work-status code
--    (LIFECYCLE_ORDER has no PARTS_* entry), but a hand-edited or legacy row
--    would strand a booking on a status the apps can no longer render. Park any
--    such row on IN_REPAIR, which is where the spare-parts wait belongs.
UPDATE tickets          SET status = 'IN_REPAIR' WHERE upper(status) IN ('PARTS_REPLACED', 'PARTS_REQUIRED');
UPDATE repair_bookings  SET status = 'IN_REPAIR' WHERE upper(status) IN ('PARTS_REPLACED', 'PARTS_REQUIRED');
UPDATE customer_orders  SET status = 'IN_REPAIR' WHERE upper(status) IN ('PARTS_REPLACED', 'PARTS_REQUIRED');

COMMIT;
