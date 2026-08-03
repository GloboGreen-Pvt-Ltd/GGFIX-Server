-- =============================================================================
-- 67_master_models_sell_active.sql
--
-- Adds master_models.sell_active to match the new MasterModel.sellActive field.
-- The admin Models table now shows a "Sell Active" switch per model; the mobile
-- Sell / trade-in product picker (flow === 'SELL') hides any model whose
-- sell_active is false. Existing models default to TRUE so the Sell flow keeps
-- showing everything until an admin turns a model off.
--
-- Without this column Hibernate's validate-only ddl mode rejects the entity at
-- startup and every request that touches master_models 500s.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS is safe to re-run.
-- =============================================================================

BEGIN;

ALTER TABLE master_models
    ADD COLUMN IF NOT EXISTS sell_active BOOLEAN NOT NULL DEFAULT TRUE;

COMMIT;
