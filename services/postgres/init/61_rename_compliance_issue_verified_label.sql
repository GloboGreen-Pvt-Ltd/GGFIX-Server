-- =============================================================================
-- 61_rename_compliance_issue_verified_label.sql
--
-- Renames the customer-visible display label for the technician work-status
--   "Technician Compliance Issue Verified & Updated"  →  "Technician Issue Verified & Updated"
--
-- The status CODE (TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED on master, and
-- TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED_UPDATED on timeline events) is left
-- untouched — it's a stable identifier used by both frontends and existing
-- rows; the change is cosmetic only.
--
-- Backfills:
--   * master_technician_work_statuses.label for the master row.
--   * repair_booking_events.note for rows that still carry the old canned
--     label text. Free-form technician notes (anything that doesn't exactly
--     match the old label) are left alone.
-- =============================================================================

UPDATE master_technician_work_statuses
   SET label = 'Technician Issue Verified & Updated'
 WHERE code  = 'TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED'
   AND label = 'Technician Compliance Issue Verified & Updated';

UPDATE repair_booking_events
   SET note = 'Technician Issue Verified & Updated'
 WHERE status = 'TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED_UPDATED'
   AND note   = 'Technician Compliance Issue Verified & Updated';
