-- =============================================================================
-- 18_technicians_extended.sql
--
-- Brings the technicians table up to date with Technician.java in
-- ticket-service. JPA schema validation was failing on `date_of_birth`
-- and 8 other columns that were added to the entity but never to the table.
-- =============================================================================

BEGIN;

ALTER TABLE technicians
    ADD COLUMN IF NOT EXISTS salary_amount         VARCHAR(50),
    ADD COLUMN IF NOT EXISTS salary_period         VARCHAR(50),
    ADD COLUMN IF NOT EXISTS id_verification_type  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS id_number             VARCHAR(100),
    ADD COLUMN IF NOT EXISTS date_of_birth         DATE,
    ADD COLUMN IF NOT EXISTS date_of_join          DATE,
    ADD COLUMN IF NOT EXISTS default_check_in      TIME,
    ADD COLUMN IF NOT EXISTS default_check_out     TIME,
    ADD COLUMN IF NOT EXISTS photo_url             VARCHAR(500);

COMMIT;
