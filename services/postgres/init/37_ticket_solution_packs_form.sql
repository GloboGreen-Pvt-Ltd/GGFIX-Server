-- 37_ticket_solution_packs_form.sql
--
-- Extends ticket_solution_packs (migration 36) with the brand / model /
-- issue category / sub-category fields the technician fills in on the
-- "New Issue Solution Pack Upload" form, plus a files_json column that
-- holds the audio / video / image attachments as one JSON array.
--
-- files_json shape:
--   [
--     { "type": "audio", "url": "...", "name": "speaker.m4a" },
--     { "type": "video", "url": "...", "name": "fix.mp4" },
--     { "type": "image", "url": "...", "name": "before.jpg" }
--   ]
--
-- brand_id / model_id are unconstrained UUIDs (no FK) because the master
-- catalog lives in master-data-service and this column should not break
-- on cross-service ID drift.

ALTER TABLE ticket_solution_packs
    ADD COLUMN IF NOT EXISTS brand_id          UUID,
    ADD COLUMN IF NOT EXISTS model_id          UUID,
    ADD COLUMN IF NOT EXISTS brand_name        VARCHAR(150),
    ADD COLUMN IF NOT EXISTS model_name        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS issue_category    VARCHAR(80),
    ADD COLUMN IF NOT EXISTS issue_subcategory VARCHAR(120),
    ADD COLUMN IF NOT EXISTS files_json        TEXT;

CREATE INDEX IF NOT EXISTS idx_ticket_solution_packs_category
    ON ticket_solution_packs(shop_id, issue_category);
