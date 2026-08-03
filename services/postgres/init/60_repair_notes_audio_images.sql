-- =============================================================================
-- 60_repair_notes_audio_images.sql
--
-- Extends the per-ticket repair_notes table (used by the technician
-- "Technician Compliance Issue Verified & Updated" form on the Ticket
-- Detail screen) with a voice-note URL and a small JSON list of attached
-- image URLs. The existing `note` TEXT column stays NOT NULL — audio and
-- images are optional add-ons to the typed note, not replacements.
--
-- Both columns are nullable + idempotent so existing rows are unaffected.
-- =============================================================================

ALTER TABLE repair_notes
    ADD COLUMN IF NOT EXISTS audio_url   TEXT,
    ADD COLUMN IF NOT EXISTS images_json TEXT;
