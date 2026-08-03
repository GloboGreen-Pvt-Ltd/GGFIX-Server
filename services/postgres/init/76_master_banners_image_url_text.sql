-- 76: widen master_banners.image_url from VARCHAR(500) to TEXT.
--
-- The MasterBanner entity has always declared this column as
-- @Column(name = "image_url", columnDefinition = "TEXT"), but the original
-- CREATE TABLE in 02_customer_app.sql made it VARCHAR(500). Hibernate runs
-- validate-only, and validate does not compare varchar length against the
-- declared columnDefinition, so the drift never surfaced at boot -- it
-- surfaced at INSERT time as SQLSTATE 22001 "value too long for type
-- character varying(500)".
--
-- This matters now that the admin Banners screen uploads images instead of
-- pasting a URL: a signed Cloudinary secure_url with transformation segments
-- runs well past 500 characters, and the uploader's data-URI fallback path
-- produces a string orders of magnitude longer still.
--
-- Idempotent: re-running against an already-TEXT column is a no-op in
-- Postgres, so this is safe to apply to databases created after the CREATE
-- TABLE in 02_customer_app.sql was corrected to TEXT.

ALTER TABLE master_banners
    ALTER COLUMN image_url TYPE TEXT;
