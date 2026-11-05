-- =============================================================================
-- 57_tickets_issue_audio_url.sql
--
-- Adds a Cloudinary URL column for the customer's voice-note recording of the
-- issue (recorded on the shop-app's "Review & Confirm" screen). The existing
-- `issue_description` TEXT column still holds the typed complaint; the new
-- column stores the audio recording so the customer can later play it back
-- in their My Orders detail screen.
--
-- The booking flow sends EITHER text OR audio OR both — the shop-app's
-- Continue gating already enforces "at least one of the two is present".
-- =============================================================================

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS issue_audio_url TEXT;
