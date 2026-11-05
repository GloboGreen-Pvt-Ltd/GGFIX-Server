-- =============================================================================
-- 70_master_models_colors_ram_storage_jsonb.sql
--
-- Migration 69 created master_models.colors / ram_storage as TEXT columns
-- holding a JSON-array string. This converts them to real jsonb (the type
-- MasterModel now maps via @JdbcTypeCode(SqlTypes.JSON)) and re-runs the
-- variant backfill for any model whose colour/spec rows were added AFTER 69 ran
-- (e.g. models edited through the still-old admin — Apple iPhone 15 was one).
--
-- Idempotent: ALTER ... TYPE jsonb USING x::jsonb is a no-op when the column is
-- already jsonb, and the backfill only fills models still at the empty array.
-- =============================================================================

BEGIN;

-- Drop the TEXT '[]' defaults first — Postgres can't auto-cast a text default
-- to jsonb during the column type change.
ALTER TABLE master_models
    ALTER COLUMN colors      DROP DEFAULT,
    ALTER COLUMN ram_storage DROP DEFAULT;

-- TEXT (JSON string) -> jsonb. Existing '[]' / '["Black",...]' cast cleanly.
ALTER TABLE master_models
    ALTER COLUMN colors      TYPE jsonb USING colors::jsonb,
    ALTER COLUMN ram_storage TYPE jsonb USING ram_storage::jsonb;

-- Restore the default as a jsonb literal.
ALTER TABLE master_models
    ALTER COLUMN colors      SET DEFAULT '[]'::jsonb,
    ALTER COLUMN ram_storage SET DEFAULT '[]'::jsonb;

-- Re-backfill colours for models whose colour rows post-date migration 69.
UPDATE master_models m
SET colors = sub.arr
FROM (
    SELECT v.model_id,
           jsonb_agg(DISTINCT c.name) AS arr
    FROM master_model_variants v
    JOIN master_colors c ON c.id = v.color_id
    WHERE v.color_id IS NOT NULL
    GROUP BY v.model_id
) sub
WHERE m.id = sub.model_id
  AND (m.colors IS NULL OR m.colors = '[]'::jsonb);

-- Re-backfill RAM+storage the same way.
UPDATE master_models m
SET ram_storage = sub.arr
FROM (
    SELECT v.model_id,
           jsonb_agg(DISTINCT (r.label || ' + ' || s.label)) AS arr
    FROM master_model_variants v
    JOIN master_ram_options     r ON r.id = v.ram_option_id
    JOIN master_storage_options s ON s.id = v.storage_option_id
    WHERE v.ram_option_id IS NOT NULL
      AND v.storage_option_id IS NOT NULL
    GROUP BY v.model_id
) sub
WHERE m.id = sub.model_id
  AND (m.ram_storage IS NULL OR m.ram_storage = '[]'::jsonb);

COMMIT;
