-- =============================================================================
-- 71_drop_master_model_variants.sql
--
-- Retires the master_model_variants table. Colours + RAM/storage now live inline
-- on master_models as jsonb arrays (migrations 69 + 70); the variant rows were
-- backfilled into those columns and are no longer read by any code (the
-- MasterModelVariant entity/repo + /master/model-variants endpoints were removed,
-- and no mobile/admin client calls them).
--
-- Safe: verified before drop — 942 rows, 0 non-null reference_price, 0 combined
-- colour+RAM+storage rows, so nothing beyond what was backfilled is lost.
--
-- ORDER-OF-DEPLOY: run this only AFTER the new master-data-service jar (which no
-- longer maps the MasterModelVariant entity) is deployed. The OLD jar validates
-- that entity against this table at startup and will fail to boot once it's gone.
--
-- Idempotent: DROP TABLE IF EXISTS is safe to re-run.
-- =============================================================================

BEGIN;

DROP TABLE IF EXISTS master_model_variants;

COMMIT;
