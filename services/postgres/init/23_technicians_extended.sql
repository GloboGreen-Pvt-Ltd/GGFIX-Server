-- =============================================================================
-- 23_technicians_extended.sql
--
-- Brings the technicians table in sync with Technician.java (ticket-service)
-- by adding the personal/payroll columns the entity references. Without
-- these, owner-side endpoints like
--   GET    /technicians
--   POST   /technicians
--   PATCH  /technicians/{id}
-- fail with HTTP 500 because Hibernate's validate-only schema rejects the
-- entity at startup or generated SQL references missing columns.
-- =============================================================================

BEGIN;

ALTER TABLE technicians
    ADD COLUMN IF NOT EXISTS salary_amount        VARCHAR(50),
    ADD COLUMN IF NOT EXISTS salary_period        VARCHAR(50),
    ADD COLUMN IF NOT EXISTS id_verification_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS id_number            VARCHAR(100),
    ADD COLUMN IF NOT EXISTS date_of_birth        DATE,
    ADD COLUMN IF NOT EXISTS date_of_join         DATE,
    ADD COLUMN IF NOT EXISTS default_check_in     TIME,
    ADD COLUMN IF NOT EXISTS default_check_out    TIME,
    ADD COLUMN IF NOT EXISTS photo_url            VARCHAR(500);

COMMIT;
