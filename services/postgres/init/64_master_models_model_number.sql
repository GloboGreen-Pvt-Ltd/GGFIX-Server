-- =============================================================================
-- 64_master_models_model_number.sql
--
-- Adds master_models.model_number to match the new MasterModel.modelNumber
-- field. The admin "New model" form now collects a manufacturer model number
-- (e.g. "V2027" for a Vivo Y20) instead of exposing the SEO slug, which is now
-- auto-generated from the name behind the scenes.
--
-- Without this column Hibernate's validate-only ddl mode rejects the entity at
-- startup and every request that touches master_models 500s.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS is safe to re-run.
-- =============================================================================

BEGIN;

ALTER TABLE master_models
    ADD COLUMN IF NOT EXISTS model_number VARCHAR(100);

COMMIT;
