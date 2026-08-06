-- =============================================================================
-- 79_model_compatibility.sql
--
-- Backs Admin panel -> Master Data -> Model Compatibility.
--
-- One row per physical spare-part BOX on the shop shelf: the box's number and
-- name, the device models whose part that box holds, an optional reference
-- photo of the part, and a free-text note. The shop looks a model up and gets
-- the box number to walk to.
--
-- WHY models IS jsonb AND NOT A JOIN TABLE
-- A box is edited as a whole — the admin ticks and unticks models in one form
-- and saves once — so a child table would only add a delete-then-reinsert dance
-- with no query that needs it. This follows the same call already made for
-- master_models.colors / ram_storage (migrations 69/70), where per-row child
-- tables were retired in favour of an inline jsonb array.
--
-- Each element carries the brand as well as the model, denormalised:
--   [{"brandId":"…","brandName":"ZEBRONICS","modelId":"…","modelName":"ZEB BEETLES"}]
-- The admin table and the shop lookup both render "brand — model" without a
-- second round trip, and the gin index below keeps a "which box holds this
-- model?" containment query fast:
--   SELECT * FROM model_compatibility WHERE models @> '[{"modelId":"…"}]';
--
-- reference_image_url holds the public media.ggfix.in URL, exactly as
-- master_models.image_url / master_banners.image_url do. No image_key or
-- content-type columns: TaxonomyMediaService documents why that metadata was
-- dropped after the same idea drifted the live schema twice.
--
-- Idempotent — IF NOT EXISTS throughout, so re-running is a no-op.
-- =============================================================================

CREATE TABLE IF NOT EXISTS model_compatibility (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    box_no              VARCHAR(60)  NOT NULL,
    box_name            VARCHAR(255) NOT NULL,
    models              JSONB NOT NULL DEFAULT '[]'::jsonb,
    reference_image_url TEXT,
    notes               TEXT,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Box numbers are how staff refer to a box out loud, so "A-12" and "a-12" must
-- not be two rows. Indexing lower(box_no) makes that a database guarantee
-- rather than a check the admin form alone performs.
CREATE UNIQUE INDEX IF NOT EXISTS uq_model_compatibility_box_no
    ON model_compatibility (lower(box_no));

-- jsonb_path_ops is the smaller, faster gin opclass for the only operator this
-- column is queried with (@> containment).
CREATE INDEX IF NOT EXISTS ix_model_compatibility_models
    ON model_compatibility USING gin (models jsonb_path_ops);
