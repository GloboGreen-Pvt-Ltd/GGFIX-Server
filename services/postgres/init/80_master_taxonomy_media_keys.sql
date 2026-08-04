-- ============================================================================
-- 80_master_taxonomy_media_keys.sql
--
-- Category and brand artwork moves to S3 behind media.ggfix.in, following
-- 79_master_models_media_keys.sql for device images.
--
--   master/categories/audio-device-1f0ab993.jpg
--   master/brands/vivo-4c7d1e02.png
--
-- These are flat, unlike the model keys: a category has no parent to nest under,
-- so the taxonomy prefix is the whole folder. The random suffix serves the same
-- purpose as on models — a replacement writes a NEW key, so CloudFront and the
-- browser cannot serve the superseded image and no invalidation is needed.
--
-- WHY THIS MATTERS MORE THAN IT LOOKS
-- Both tables already have image_base64, and with Cloudinary unconfigured the
-- admin's uploader silently fell back to inlining the whole file as a base64 data
-- URI into image_url. That is what "stored as: inline (data URI)" meant on the
-- Edit category screen. Those rows carry the image inside the table, so every
-- catalogue read drags it through Postgres and the JSON response — the same
-- mechanism that previously took the admin Models page down against a 384 MB heap.
--
-- Additive and safe to re-run: no backfill, and image_url / image_base64 are left
-- alone so existing rows keep rendering until they are re-uploaded.
-- ============================================================================

ALTER TABLE master_device_categories ADD COLUMN IF NOT EXISTS image_key           VARCHAR(768);
ALTER TABLE master_device_categories ADD COLUMN IF NOT EXISTS image_original_name VARCHAR(255);
ALTER TABLE master_device_categories ADD COLUMN IF NOT EXISTS image_content_type  VARCHAR(100);
ALTER TABLE master_device_categories ADD COLUMN IF NOT EXISTS image_size_bytes    BIGINT;

ALTER TABLE master_brands ADD COLUMN IF NOT EXISTS image_key           VARCHAR(768);
ALTER TABLE master_brands ADD COLUMN IF NOT EXISTS image_original_name VARCHAR(255);
ALTER TABLE master_brands ADD COLUMN IF NOT EXISTS image_content_type  VARCHAR(100);
ALTER TABLE master_brands ADD COLUMN IF NOT EXISTS image_size_bytes    BIGINT;

-- One row per object, so replacing an image can delete the superseded key without
-- risking an object another row still points at.
CREATE UNIQUE INDEX IF NOT EXISTS uq_master_device_categories_image_key
    ON master_device_categories (image_key)
    WHERE image_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_master_brands_image_key
    ON master_brands (image_key)
    WHERE image_key IS NOT NULL;

COMMENT ON COLUMN master_device_categories.image_key IS
    'S3 object key, e.g. master/categories/audio-device-1f0ab993.jpg. Public URL = https://media.ggfix.in/{image_key}.';
COMMENT ON COLUMN master_brands.image_key IS
    'S3 object key, e.g. master/brands/vivo-4c7d1e02.png. Public URL = https://media.ggfix.in/{image_key}.';
