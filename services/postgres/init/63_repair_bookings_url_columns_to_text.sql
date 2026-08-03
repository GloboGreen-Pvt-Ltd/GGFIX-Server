-- =============================================================================
-- 63_repair_bookings_url_columns_to_text.sql
--
-- repair_bookings.front_image_url / back_image_url / video_url were varchar(500).
-- Normalized / Cloudinary fetch-wrapped device image URLs (e.g. AVIF -> f_jpg,
-- which URL-encodes the original CDN URL inside a Cloudinary fetch URL) can
-- exceed 500 chars, which raised:
--
--   ERROR: value too long for type character varying(500)
--
-- and 500'd POST /tickets (the repair_bookings mirror insert). Widen the three
-- URL columns to TEXT so they match the entity and the other long columns
-- (issue_summary, missing_damage_parts, technician_photos), which are already
-- TEXT.
--
-- Idempotent: ALTER ... TYPE TEXT is safe to re-run.
-- =============================================================================

BEGIN;

ALTER TABLE repair_bookings ALTER COLUMN front_image_url TYPE TEXT;
ALTER TABLE repair_bookings ALTER COLUMN back_image_url  TYPE TEXT;
ALTER TABLE repair_bookings ALTER COLUMN video_url       TYPE TEXT;

COMMIT;
