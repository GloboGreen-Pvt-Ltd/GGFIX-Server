-- =============================================================================
-- 90_shop_ledger_parties_due_date.sql
--
-- When the shop expects an account to settle.
--
-- WHY
-- The Cash Book records what is owed but never when it was promised. The owner
-- has that date — "he said Friday" — and today it lives in their head, which is
-- why chasing a debt means scrolling the whole customer list trying to remember
-- who is overdue. One nullable date per account turns that into a sort.
--
-- WHY ON THE PARTY AND NOT THE ENTRY
-- A shop chases an ACCOUNT, not a line. A customer with four unpaid entries is
-- one conversation and one promise, and per-entry dates would ask the owner to
-- answer the same question four times and then leave the app to decide which of
-- the four answers the account is "due" on. The balance is already an
-- account-level number; its due date belongs at the same level.
--
-- DATE, not timestamp, matching shop_ledger_entries.entry_date: this is the day
-- money is expected, not an instant. Nullable with no default, because most
-- accounts never carry a promise and 'no date' is the honest representation of
-- that — a defaulted date would make every account look overdue eventually.
--
-- The partial index carries the sort behind the Cash Book's "Due Today" and
-- "Defaulters" filters. Partial because the majority of rows are NULL, and an
-- index that skips them is a fraction of the size for exactly the same reads.
--
-- Idempotent — IF NOT EXISTS on both the column and the index.
-- =============================================================================

ALTER TABLE shop_ledger_parties
    ADD COLUMN IF NOT EXISTS due_date DATE;

CREATE INDEX IF NOT EXISTS idx_shop_ledger_parties_due
    ON shop_ledger_parties (shop_id, due_date)
    WHERE due_date IS NOT NULL;

COMMENT ON COLUMN shop_ledger_parties.due_date IS
    'The day the shop expects this account to settle. NULL when no promise has been made — most accounts. Set from the party detail screen; drives the overdue sort and the payment reminder.';
