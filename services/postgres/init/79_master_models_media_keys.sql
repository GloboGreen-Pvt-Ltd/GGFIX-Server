-- ============================================================================
-- 79_master_models_media_keys.sql
--
-- Device images move from Cloudinary (and from inline base64 data URIs) to S3
-- behind the media.ggfix.in CloudFront distribution.
--
-- The object key is derived from the model's own taxonomy, so the bucket browses
-- like the catalogue:
--
--   mobile/vivo/y-series/vivo-y20/main-a82f5c1.jpg
--   |      |    |        |        |
--   |      |    |        |        image_key leaf, unique per upload
--   |      |    |        model slug
--   |      |    series slug
--   |    brand slug
--   category slug
--
-- media_folder_key stores the folder (everything up to the model slug) and
-- image_key the full object key. Both are kept because the folder is stable
-- across image replacements while the leaf deliberately changes every time —
-- a fresh filename is what stops CloudFront and browsers serving the old image
-- after an update.
--
-- The public URL is NOT stored: it is composed at read time as
-- https://media.ggfix.in/{image_key}. Storing it would bake the CDN hostname
-- into every row and make moving domains a data migration.
--
-- image_url is deliberately left in place. Existing rows still point at
-- Cloudinary, and the API keeps serving whichever of the two is populated, so
-- this migration is additive and needs no backfill or downtime.
-- ============================================================================

ALTER TABLE master_models ADD COLUMN IF NOT EXISTS media_folder_key    VARCHAR(512);
ALTER TABLE master_models ADD COLUMN IF NOT EXISTS image_key           VARCHAR(768);
ALTER TABLE master_models ADD COLUMN IF NOT EXISTS image_original_name VARCHAR(255);
ALTER TABLE master_models ADD COLUMN IF NOT EXISTS image_content_type  VARCHAR(100);
ALTER TABLE master_models ADD COLUMN IF NOT EXISTS image_size_bytes    BIGINT;

-- One row per object. Guards against a retried upload leaving two models
-- pointing at the same key, which would make the delete-on-replace path remove
-- an image still in use by another row.
CREATE UNIQUE INDEX IF NOT EXISTS uq_master_models_image_key
    ON master_models (image_key)
    WHERE image_key IS NOT NULL;

-- Listing a series' folder contents, and the "does this folder already exist"
-- lookup used when a second model is added under the same category/brand/series.
CREATE INDEX IF NOT EXISTS idx_master_models_media_folder_key
    ON master_models (media_folder_key)
    WHERE media_folder_key IS NOT NULL;

COMMENT ON COLUMN master_models.media_folder_key IS
    'S3 folder for this model, e.g. mobile/vivo/y-series/vivo-y20. Stable across image replacements.';
COMMENT ON COLUMN master_models.image_key IS
    'Full S3 object key, e.g. mobile/vivo/y-series/vivo-y20/main-a82f5c1.jpg. Public URL = https://media.ggfix.in/{image_key}.';
COMMENT ON COLUMN master_models.image_original_name IS
    'Filename as uploaded. Retained for admin display and audit only; never used to build the key.';
COMMENT ON COLUMN master_models.image_content_type IS
    'MIME type as validated server-side against the allow-list, not as claimed by the client.';
COMMENT ON COLUMN master_models.image_size_bytes IS
    'Object size in bytes, recorded at upload time.';
