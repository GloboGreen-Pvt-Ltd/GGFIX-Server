-- =============================================================================
-- 80_model_compatibility_part_types.sql
--
-- Splits Model Compatibility by PART TYPE, so the admin sidebar can carry one
-- child entry per type (Mobile Model Number, Tempered Glass, Mobile Case, …)
-- instead of one flat list of every box in the shop.
--
-- WHY A TABLE AND NOT AN ENUM / A VARCHAR
-- The types are admin-managed: adding a fourth has to be a row, not a release.
-- A CHECK constraint or a Java enum would put the shop's stock vocabulary in the
-- deploy path, and the sidebar is built from whatever rows exist here.
--
-- part_type_id is NULLABLE. Boxes already exist (the "Tempered Glass - 01" row
-- was created before this migration), and forcing a type on them would mean
-- guessing. A box with no type still lists under "All", so nothing disappears
-- from the screen the moment this lands — the admin assigns types by editing.
--
-- Idempotent: IF NOT EXISTS everywhere, and the seed uses ON CONFLICT DO NOTHING.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS model_compatibility_types (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,
    -- URL-safe key. The admin sidebar links to ?type=<slug> rather than ?type=<uuid>
    -- so the address bar stays readable and a bookmarked link survives a reseed.
    slug        VARCHAR(140) NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Same case-insensitive uniqueness rule as box numbers: "Tempered Glass" and
-- "tempered glass" must not become two menu entries.
CREATE UNIQUE INDEX IF NOT EXISTS uq_model_compatibility_types_name
    ON model_compatibility_types (lower(name));
CREATE UNIQUE INDEX IF NOT EXISTS uq_model_compatibility_types_slug
    ON model_compatibility_types (lower(slug));

INSERT INTO model_compatibility_types (name, slug, sort_order)
VALUES
    ('Mobile Model Number', 'mobile-model-number', 10),
    ('Tempered Glass',      'tempered-glass',      20),
    ('Mobile Case',         'mobile-case',         30)
ON CONFLICT DO NOTHING;

ALTER TABLE model_compatibility
    ADD COLUMN IF NOT EXISTS part_type_id UUID;

-- No FK: master-data tables here are managed by the application rather than by
-- referential actions, and a hard constraint would make deleting a type fail
-- with a constraint error instead of the readable 409 the controller returns.
CREATE INDEX IF NOT EXISTS ix_model_compatibility_part_type
    ON model_compatibility (part_type_id);

-- The one box that predates this migration is Tempered Glass by name, so file it
-- under that type rather than leaving it untyped. Matches on the box name and
-- only fills rows that have no type yet, so a re-run is a no-op.
UPDATE model_compatibility mc
SET part_type_id = t.id
FROM model_compatibility_types t
WHERE mc.part_type_id IS NULL
  AND lower(mc.box_name) = lower(t.name);

COMMIT;
