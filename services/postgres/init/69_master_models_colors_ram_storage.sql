-- =============================================================================
-- 69_master_models_colors_ram_storage.sql
--
-- Collapses the per-model colour / RAM+storage options out of the separate
-- master_model_variants table and inlines them on master_models as two JSON
-- array TEXT columns (matching MasterModel.colors / MasterModel.ramStorage):
--
--   colors      -> ["Diamond Black","Skyline Blue","Cosmic Green"]
--   ram_storage -> ["4 GB + 128 GB","6 GB + 128 GB"]
--
-- Plain TEXT (not jsonb) keeps it portable across Postgres (default profile)
-- and H2 (dev profile), and lets Hibernate validate-only mode accept the
-- String-mapped columns.
--
-- The old master_model_variants table is intentionally LEFT IN PLACE (no drop):
-- the switch is non-destructive; the rows are simply no longer read.
--
-- Without these columns Hibernate rejects the MasterModel entity at startup and
-- every request touching master_models 500s.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS is safe to re-run, and the backfill only
-- touches models still at the default '[]' so re-running never clobbers values
-- an admin has since edited through the new inline flow.
-- =============================================================================

BEGIN;

ALTER TABLE master_models
    ADD COLUMN IF NOT EXISTS colors      TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN IF NOT EXISTS ram_storage TEXT NOT NULL DEFAULT '[]';

-- Backfill colours from existing colour-only variant rows.
UPDATE master_models m
SET colors = sub.arr
FROM (
    SELECT v.model_id,
           jsonb_agg(DISTINCT c.name)::text AS arr
    FROM master_model_variants v
    JOIN master_colors c ON c.id = v.color_id
    WHERE v.color_id IS NOT NULL
    GROUP BY v.model_id
) sub
WHERE m.id = sub.model_id
  AND (m.colors IS NULL OR m.colors = '[]');

-- Backfill RAM+storage from existing spec-only variant rows ("<ram> + <storage>").
UPDATE master_models m
SET ram_storage = sub.arr
FROM (
    SELECT v.model_id,
           jsonb_agg(DISTINCT (r.label || ' + ' || s.label))::text AS arr
    FROM master_model_variants v
    JOIN master_ram_options     r ON r.id = v.ram_option_id
    JOIN master_storage_options s ON s.id = v.storage_option_id
    WHERE v.ram_option_id IS NOT NULL
      AND v.storage_option_id IS NOT NULL
    GROUP BY v.model_id
) sub
WHERE m.id = sub.model_id
  AND (m.ram_storage IS NULL OR m.ram_storage = '[]');

COMMIT;
