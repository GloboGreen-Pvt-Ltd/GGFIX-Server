-- =============================================================================
-- 24_technicians_id_documents.sql
--
-- Stores Aadhar and PAN documents separately on the technicians row so the
-- owner Employee Details form can capture both IDs (each with its own front/
-- back image + number) instead of the legacy single id_verification_type +
-- id_number pair. Also adds daily_wage which the mobile form has been
-- collecting but the entity never persisted.
-- =============================================================================

BEGIN;

ALTER TABLE technicians
    ADD COLUMN IF NOT EXISTS aadhar_number     VARCHAR(50),
    ADD COLUMN IF NOT EXISTS aadhar_front_url  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS aadhar_back_url   VARCHAR(500),
    ADD COLUMN IF NOT EXISTS pan_number        VARCHAR(50),
    ADD COLUMN IF NOT EXISTS pan_front_url     VARCHAR(500),
    ADD COLUMN IF NOT EXISTS pan_back_url      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS daily_wage        VARCHAR(50);

COMMIT;
