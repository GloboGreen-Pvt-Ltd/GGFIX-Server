-- =============================================================================
-- 17_technician_hr_tables.sql
--
-- Creates the technician HR tables used by ticket-service:
-- technician_experience, technician_leave, technician_salary_advance.
-- Entities live under epair-shop-saas/services/ticket-service/.../entity/.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS technician_experience (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    technician_id   UUID NOT NULL,
    shop_name       VARCHAR(255),
    location        VARCHAR(500),
    join_date       DATE NOT NULL,
    relieving_date  DATE,
    working_type    VARCHAR(50),
    last_salary     VARCHAR(50),
    total_service   INTEGER,
    completed_count INTEGER,
    return_count    INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tech_exp_technician
    ON technician_experience(technician_id);

CREATE TABLE IF NOT EXISTS technician_leave (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    technician_id UUID NOT NULL,
    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL,
    reason        VARCHAR(500),
    status        VARCHAR(50) NOT NULL,
    requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tech_leave_technician
    ON technician_leave(technician_id);

CREATE TABLE IF NOT EXISTS technician_salary_advance (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    technician_id UUID NOT NULL,
    amount        NUMERIC(19,2) NOT NULL,
    advance_date  DATE NOT NULL,
    status        VARCHAR(50) NOT NULL,
    requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    notes         VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tech_advance_technician
    ON technician_salary_advance(technician_id);

COMMIT;
