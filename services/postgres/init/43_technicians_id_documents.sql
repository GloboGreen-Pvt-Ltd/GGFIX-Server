-- =============================================================================
-- 43_technicians_id_documents.sql
--
-- Keeps services/postgres/init aligned with database/schema/24_*. The
-- ticket-service Technician entity maps these Aadhar/PAN fields directly, so a
-- validate-only startup fails when an older local database is missing them.
-- =============================================================================

ALTER TABLE technicians
    ADD COLUMN IF NOT EXISTS aadhar_number     VARCHAR(50),
    ADD COLUMN IF NOT EXISTS aadhar_front_url  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS aadhar_back_url   VARCHAR(500),
    ADD COLUMN IF NOT EXISTS pan_number        VARCHAR(50),
    ADD COLUMN IF NOT EXISTS pan_front_url     VARCHAR(500),
    ADD COLUMN IF NOT EXISTS pan_back_url      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS daily_wage        VARCHAR(50);
