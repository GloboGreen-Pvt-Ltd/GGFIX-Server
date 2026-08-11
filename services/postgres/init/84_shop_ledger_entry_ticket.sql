-- =============================================================================
-- 84_shop_ledger_entry_ticket.sql
--
-- Links a Cash Book entry (migration 82) to the repair it was paid against.
--
-- WHY
-- The commonest entry a shop writes is an ADVANCE on a job in progress: the
-- customer leaves a deposit, the phone goes on the bench, and the balance is
-- collected on delivery. Until now that connection lived only in whatever the
-- owner typed in the note, so "did this customer already pay something on this
-- repair?" could only be answered by reading free text — the exact question that
-- turns into an argument at the counter.
--
-- WHY THE LABEL IS SNAPSHOT ALONGSIDE THE ID
-- ticket_tracking_id and ticket_label repeat what the tickets row already says.
-- Deliberate, and the same reasoning as LedgerEntryResponse.partyName: a
-- statement is a list of many entries, and resolving each one's ticket would be
-- an N+1 into another table on every render. The snapshot also survives the
-- ticket being deleted — the money still moved, and a row that reads
-- "Advance · (deleted)" is worse than one that still names the device.
--
-- ON DELETE SET NULL, never CASCADE: deleting a repair must not delete the
-- record of money that changed hands over it. The id goes, the snapshot stays,
-- and the entry keeps its amount.
--
-- Nullable throughout — most entries are not against any single job.
--
-- Idempotent — IF NOT EXISTS, and the FK is added only when absent, so
-- re-running is a no-op.
-- =============================================================================

ALTER TABLE shop_ledger_entries
    ADD COLUMN IF NOT EXISTS ticket_id          UUID,
    ADD COLUMN IF NOT EXISTS ticket_tracking_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS ticket_label       TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_shop_ledger_entries_ticket'
    ) THEN
        ALTER TABLE shop_ledger_entries
            ADD CONSTRAINT fk_shop_ledger_entries_ticket
            FOREIGN KEY (ticket_id) REFERENCES tickets (id) ON DELETE SET NULL;
    END IF;
END $$;

-- Answers "what has been paid on this job?" without scanning the shop's whole
-- book. Partial, because the vast majority of entries carry no ticket at all.
CREATE INDEX IF NOT EXISTS idx_shop_ledger_entries_ticket
    ON shop_ledger_entries (ticket_id)
    WHERE ticket_id IS NOT NULL;
