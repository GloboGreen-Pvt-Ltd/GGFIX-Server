-- =============================================================================
-- 83_shop_ledger_entry_attachments.sql
--
-- Evidence on a Cash Book entry (migration 82): the voice note the owner spoke
-- instead of typing, and the photographed bills behind the amount.
--
-- WHY A VOICE NOTE AT ALL
-- The note field is typed at a counter, one-handed, often mid-conversation with
-- the customer whose money is being booked. Owners skip it, and an entry with no
-- reason on it is the one nobody can settle an argument with a month later.
-- Holding the mic is the version that actually gets used.
--
-- WHY TEXT AND NOT VARCHAR(500)
-- Matches repair_notes.audio_url / images_json in this same service. S3 URLs are
-- already long and a signed or CDN-rewritten one is longer; a length cap here
-- buys nothing and truncates a URL into a broken link rather than failing loudly.
--
-- WHY bill_images_json AND NOT A CHILD TABLE
-- These are attachments to one entry, never queried across entries, never joined
-- and deleted with it. A child table would add a join to every statement read to
-- serve a list that is almost always empty. Stored as a JSON array of URLs —
-- ["https://media.ggfix.in/cashbook/bill-3f9c11ab.jpg", ...] — exactly like
-- repair_notes.images_json, so both surfaces parse the same shape. Empty
-- collapses to NULL so reads can short-circuit on isBlank.
--
-- Nullable throughout: every entry written before this migration has neither,
-- and an entry with only an amount stays perfectly valid.
--
-- Idempotent — IF NOT EXISTS, so re-running is a no-op.
-- =============================================================================

ALTER TABLE shop_ledger_entries
    ADD COLUMN IF NOT EXISTS note_audio_url   TEXT,
    ADD COLUMN IF NOT EXISTS bill_images_json TEXT;
