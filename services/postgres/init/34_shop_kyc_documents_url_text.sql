-- =============================================================================
-- 34_shop_kyc_documents_url_text.sql
--
-- Widen shop_kyc_documents.url from VARCHAR(1000) to TEXT.
--
-- Why: in dev master-service's /media/upload falls back to base64 data URIs
-- (Cloudinary creds not configured), and a typical phone-camera image is
-- 1–3 MB once base64-encoded. That blows past VARCHAR(1000) and the INSERT
-- throws "value too long for type character varying(1000)" — which Spring
-- wraps in a 500 Internal Server Error on POST /shops/{id}/kyc-documents.
--
-- TEXT is unbounded in Postgres, so this also future-proofs the column for
-- whatever URL prefix Cloudinary returns (signed URLs are long too).
-- =============================================================================

ALTER TABLE shop_kyc_documents
    ALTER COLUMN url TYPE TEXT;
