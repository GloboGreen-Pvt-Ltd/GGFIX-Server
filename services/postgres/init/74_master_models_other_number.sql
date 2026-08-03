-- =============================================================================
-- 74_master_models_other_number.sql
--
-- Adds master_models.other_number: an ADDITIONAL secondary identifier field for a
-- model, alongside the existing model_number (migration 73). Stored as a jsonb
-- array of codes (e.g. ["V2143","V2168"]) so a model can carry several — the same
-- shape and mapping as model_number / colors / ram_storage, exposed on the entity
-- as MasterModel.otherNumber via @JdbcTypeCode(SqlTypes.JSON).
--
-- Brand-new column (nothing to convert): default to an empty array so existing
-- rows are valid immediately and Hibernate's validate-only ddl mode accepts the
-- jsonb-mapped List<String> at startup.
--
-- Idempotent: guarded on the column not already existing, so re-running is a no-op.
-- =============================================================================

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'master_models'
          AND column_name = 'other_number'
    ) THEN
        ALTER TABLE master_models
            ADD COLUMN other_number jsonb NOT NULL DEFAULT '[]'::jsonb;
    END IF;
END $$;

COMMIT;
