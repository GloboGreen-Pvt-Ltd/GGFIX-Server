-- =============================================================================
-- 24_technician_management.sql
--
-- Creates the four tables backing the technician management feature in
-- ticket-service:
--   * technician_attendance       (TechnicianAttendance.java)
--   * technician_leave            (TechnicianLeave.java)
--   * technician_salary_advance   (TechnicianSalaryAdvance.java)
--   * technician_experience       (TechnicianExperience.java)
--
-- Without these every owner-side endpoint under /technicians/{id}/attendance,
-- /technicians/{id}/leave, /technicians/{id}/advances and
-- /technicians/{id}/experience fails with HTTP 500. These tables only ever
-- existed in the legacy H2 dev DB via Hibernate ddl-auto.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS technician_attendance (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    technician_id       UUID NOT NULL REFERENCES technicians(id) ON DELETE CASCADE,
    attendance_date     DATE NOT NULL,
    check_in_time       TIME,
    check_out_time      TIME,
    status              VARCHAR(50),   -- GENERAL, LEAVE, WEEK_OFF, LATE, PERMISSION
    working_minutes     INTEGER,
    notes               VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tech_att_technician_date
    ON technician_attendance (technician_id, attendance_date);

CREATE TABLE IF NOT EXISTS technician_leave (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    technician_id       UUID NOT NULL REFERENCES technicians(id) ON DELETE CASCADE,
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    reason              VARCHAR(500),
    status              VARCHAR(50) NOT NULL,   -- PROCESSING, APPROVED, REJECTED
    requested_at        TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tech_leave_technician
    ON technician_leave (technician_id);

CREATE TABLE IF NOT EXISTS technician_salary_advance (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    technician_id       UUID NOT NULL REFERENCES technicians(id) ON DELETE CASCADE,
    amount              NUMERIC(19, 2) NOT NULL,
    advance_date        DATE NOT NULL,
    status              VARCHAR(50) NOT NULL,   -- PAID, UNPAID
    requested_at        TIMESTAMPTZ NOT NULL,
    notes               VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tech_advance_technician
    ON technician_salary_advance (technician_id);

CREATE TABLE IF NOT EXISTS technician_experience (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    technician_id       UUID NOT NULL REFERENCES technicians(id) ON DELETE CASCADE,
    shop_name           VARCHAR(255),
    location            VARCHAR(500),
    join_date           DATE NOT NULL,
    relieving_date      DATE,                    -- NULL = current/present
    working_type        VARCHAR(50),             -- FULL_TIME, PART_TIME
    last_salary         VARCHAR(50),
    total_service       INTEGER,
    completed_count     INTEGER,
    return_count        INTEGER,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tech_exp_technician
    ON technician_experience (technician_id);

COMMIT;
