-- =============================================================================
-- 62_repair_booking_events_audio_images.sql
--
-- Adds nullable audio_url + images_json columns to the customer/owner-facing
-- repair_booking_events timeline rows. Lets the technician's compliance-note
-- attachments (voice-note + image thumbnails saved to repair_notes) ride on
-- the same TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED_UPDATED event the customer
-- and owner timelines already render, so the "Issue Verified & Updated" row
-- can show the media inline without a new endpoint.
-- =============================================================================

ALTER TABLE repair_booking_events
    ADD COLUMN IF NOT EXISTS audio_url   TEXT,
    ADD COLUMN IF NOT EXISTS images_json TEXT;
