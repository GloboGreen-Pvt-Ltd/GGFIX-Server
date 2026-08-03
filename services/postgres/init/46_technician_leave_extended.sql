-- =============================================================================
-- 46_technician_leave_extended.sql
--
-- Extends `technician_leave` so the employee Apply-for-Leave flow can capture
-- leave type, optional attachment proof, computed total days, and full
-- approval / rejection audit metadata (who, when, why). Schema-wise the table
-- is the single source of truth for employee leave requests; the entity is
-- TechnicianLeave (ticket-service) and the public DTO is LeaveRequestResponse.
--
-- Status values used by the service layer:
--   PROCESSING (legacy alias for PENDING) | PENDING | APPROVED | REJECTED | CANCELLED
--
-- Leave types (validated in the service):
--   CASUAL_LEAVE | SICK_LEAVE | EMERGENCY_LEAVE | PERMISSION | HALF_DAY | OTHER
-- =============================================================================

BEGIN;

ALTER TABLE technician_leave
    ADD COLUMN IF NOT EXISTS leave_type        VARCHAR(50),
    ADD COLUMN IF NOT EXISTS total_days        NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS attachment_url    TEXT,
    ADD COLUMN IF NOT EXISTS approved_by       UUID,
    ADD COLUMN IF NOT EXISTS approved_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejected_by       UUID,
    ADD COLUMN IF NOT EXISTS rejected_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejection_reason  TEXT,
    ADD COLUMN IF NOT EXISTS remarks           TEXT,
    ADD COLUMN IF NOT EXISTS updated_at        TIMESTAMPTZ NOT NULL DEFAULT now();

-- Widen reason from VARCHAR(500) to TEXT so longer write-ups (e.g. medical
-- notes from a doctor) aren't silently truncated. Safe / no rewrite on
-- Postgres when going to TEXT.
ALTER TABLE technician_leave
    ALTER COLUMN reason TYPE TEXT;

-- Backfill total_days for rows created before this column existed so the
-- mobile UI doesn't render "—" on historical entries. (end - start + 1).
UPDATE technician_leave
   SET total_days = (end_date - start_date) + 1
 WHERE total_days IS NULL;

-- Status filter is used by the shop owner's pending list and by the
-- employee's history filter chips; the date-range index keeps overlap
-- checks fast as employees apply for new leave.
CREATE INDEX IF NOT EXISTS idx_tech_leave_status
    ON technician_leave (status);

CREATE INDEX IF NOT EXISTS idx_tech_leave_start_end
    ON technician_leave (technician_id, start_date, end_date);

COMMIT;
