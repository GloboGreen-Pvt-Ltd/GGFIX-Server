-- =============================================================================
-- 75_master_models_drop_other_number.sql
--
-- Reverts migration 74: removes master_models.other_number. The "Other number"
-- field (secondary identifier codes, jsonb array) was added in 74 but is no
-- longer wanted, so the column is dropped from the schema. The MasterModel entity
-- no longer maps other_number, so Hibernate's validate-only ddl mode is unaffected
-- by this column being gone.
--
-- IMPORTANT DEPLOY ORDER: the master-data-service jar that still MAPS other_number
-- must NOT be running when this drop is applied — a mapped-but-missing column makes
-- every model INSERT/UPDATE fail. Deploy the new jar (which drops the mapping)
-- FIRST, then apply this migration.
--
-- Idempotent: guarded on the column existing, so re-running after it's already
-- gone is a no-op. (Migration 74 is kept as-is for history; on a fresh init 74
-- adds the column and 75 immediately drops it — net no column.)
-- =============================================================================

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'master_models'
          AND column_name = 'other_number'
    ) THEN
        ALTER TABLE master_models DROP COLUMN other_number;
    END IF;
END $$;

COMMIT;
