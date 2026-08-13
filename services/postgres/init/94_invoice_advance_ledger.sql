-- ════════════════════════════════════════════════════════════════════════════
-- 94 · Mirror the invoice ADVANCE onto the Cash Book
--
-- Migration 81 gave the invoice a credit_ledger_entry_id so the outstanding
-- balance could be posted to the customer's account as a GIVEN entry, and so a
-- re-generated invoice would UPDATE that row instead of doubling the debt.
--
-- The advance never got the same treatment. Money handed over at booking was
-- recorded on the invoice alone, so the Cash Book — and anything reading it,
-- including the Revenue report — could not see it. A ₹10,000 job taken with a
-- ₹5,000 advance showed only the ₹5,000 balance as revenue when it was cleared;
-- the first ₹5,000 was invisible.
--
-- These two columns are the advance's half of the same mechanism, and carry the
-- same guarantee: one ledger row per invoice advance, updated in place, deleted
-- when the advance goes to zero.
--
-- Deliberately NOT backfilled. Writing rows into live customer ledgers for every
-- historical invoice would double-count every advance an owner had already
-- entered by hand, and there is no reliable key to tell those apart. Existing
-- invoices keep their present behaviour; new and re-generated ones post.
-- ════════════════════════════════════════════════════════════════════════════

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS advance_party_id        UUID,
    ADD COLUMN IF NOT EXISTS advance_ledger_entry_id UUID;

COMMENT ON COLUMN invoices.advance_party_id IS
    'Cash Book customer account the advance was posted to (migration 94).';
COMMENT ON COLUMN invoices.advance_ledger_entry_id IS
    'shop_ledger_entries row mirroring invoices.advance_paid as a RECEIVED entry. Held so re-generating the invoice updates that row instead of posting a second receipt.';
