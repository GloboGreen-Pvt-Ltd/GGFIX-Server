-- =============================================================================
-- 33_shop_kyc_documents.sql
--
-- Adds shop_kyc_documents, the per-shop KYC document store backing the owner
-- mobile KYC flow (Aadhar front/back, PAN, GST, Udyam). One row per (shop_id,
-- doc_type) — re-uploading a doc updates the row in place via the controller's
-- upsert path. Hosted URLs come from master-service's /media/upload (Cloudinary
-- in prod, base64 data URI fallback in dev).
--
-- Status lifecycle:
--   PENDING_REVIEW (default on upload)
--   APPROVED       (admin-side approval)
--   REJECTED       (admin-side rejection, with reason)
-- =============================================================================

CREATE TABLE IF NOT EXISTS shop_kyc_documents (
    id          UUID PRIMARY KEY,
    shop_id     UUID         NOT NULL,
    doc_type    VARCHAR(40)  NOT NULL,
    title       VARCHAR(120) NOT NULL,
    url         VARCHAR(1000) NOT NULL,
    is_required BOOLEAN      NOT NULL DEFAULT FALSE,
    status      VARCHAR(30)  NOT NULL DEFAULT 'PENDING_REVIEW',
    reject_reason TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (shop_id, doc_type)
);

CREATE INDEX IF NOT EXISTS idx_shop_kyc_documents_shop_id
    ON shop_kyc_documents(shop_id);
