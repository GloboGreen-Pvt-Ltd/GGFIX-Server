-- =============================================================================
-- 16_technician_attendance.sql
--
-- Creates the technician_attendance table used by ticket-service for
-- shift / leave / late tracking. Entity is TechnicianAttendance.java.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS technician_attendance (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    technician_id    UUID NOT NULL,
    attendance_date  DATE NOT NULL,
    check_in_time    TIME,
    check_out_time   TIME,
    status           VARCHAR(50),
    working_minutes  INTEGER,
    notes            VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tech_att_technician_date
    ON technician_attendance(technician_id, attendance_date);

COMMIT;
