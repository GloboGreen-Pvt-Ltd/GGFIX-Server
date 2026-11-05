-- =============================================================================
-- 42_technician_kyc_documents.sql
--
-- Per-technician KYC store backing the employee mobile KYC flow (Aadhar
-- front/back + PAN). Mirrors shop_kyc_documents (migration 33/34) — same
-- columns and PENDING_REVIEW/APPROVED/REJECTED lifecycle — but keyed by
-- technician_id instead of shop_id. One row per (technician_id, doc_type);
-- re-uploading the same doc updates the row in place via the controller's
-- upsert path.
--
-- Hosted URLs come from master-service's /media/upload (Cloudinary in prod,
-- base64 data URI fallback in dev), so the url column is TEXT to fit the
-- multi-MB base64 payload the dev fallback returns.
-- =============================================================================

CREATE TABLE IF NOT EXISTS technician_kyc_documents (
    id            UUID         PRIMARY KEY,
    technician_id UUID         NOT NULL,
    doc_type      VARCHAR(40)  NOT NULL,
    title         VARCHAR(120) NOT NULL,
    url           TEXT         NOT NULL,
    is_required   BOOLEAN      NOT NULL DEFAULT FALSE,
    status        VARCHAR(30)  NOT NULL DEFAULT 'PENDING_REVIEW',
    reject_reason TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (technician_id, doc_type)
);

CREATE INDEX IF NOT EXISTS idx_technician_kyc_documents_technician_id
    ON technician_kyc_documents(technician_id);
