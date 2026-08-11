-- =============================================================================
-- 89_invoices_payment_credit.sql
--
-- What the customer actually paid against an invoice, and what they still owe.
--
-- WHY
-- The Invoice Generator computed a Final Payable Amount and stopped there. Money
-- already collected at booking time lived on the ticket (migration 85), money
-- collected at the counter on delivery lived nowhere at all, and "this customer
-- is taking the phone and paying me next week" lived only in the owner's head.
-- The screen now closes that loop:
--
--   final_payable_amount                       what the repair came to
--   − advance_paid                             collected when the job was booked
--   = net_payable_amount                       what is owed today
--   − amount_paid                              handed over at the counter now
--   = credit_amount                            what the customer owes from here
--
-- WHY THE ARITHMETIC IS STORED AND NOT DERIVED
-- Same rule the rest of this table already follows: an invoice is a document, and
-- a document that recomputes itself is not a document. The owner must be able to
-- reprint the same figures a year later even if the rounding, the tax mode or the
-- advance on the ticket has moved since.
--
-- WHY THE CREDIT POINTS AT THE CASH BOOK
-- credit_amount is not a note on an invoice — it is a debt, and the shop already
-- has one place where debts live and get chased: the customer account behind the
-- Cash Book (migrations 81/82). InvoiceService posts the credit there as a GIVEN
-- entry, so the balance the owner reads on the customer's account and the balance
-- on the invoice are the same number by construction, and a later repayment is
-- just an ordinary RECEIVED entry on that account. Nothing new to reconcile.
--
-- WHY THE ENTRY ID IS STORED HERE
-- Re-generating an invoice UPDATES the same invoices row (uq_invoices_ticket_id).
-- Without a handle on the row it already wrote, every re-generation would post a
-- SECOND credit entry and silently double the customer's debt. Holding the id
-- turns the second write into an update — and lets a credit that is later cleared
-- delete its entry instead of stranding it.
--
-- ON DELETE SET NULL on both links, never CASCADE: deleting a cash-book row must
-- not delete the invoice, and deleting the customer's account must not either.
-- The invoice keeps its own copy of every amount, so it still prints.
--
-- NUMERIC(14,2) and NOT NULL DEFAULT 0 match the money columns already on this
-- table (migration 58). payment_date is a DATE, not a timestamp, for the same
-- reason shop_ledger_entries.entry_date is: it is the counter day the money
-- belongs to, not an instant.
--
-- Idempotent — IF NOT EXISTS throughout, constraints added only when absent.
-- =============================================================================

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS advance_paid           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS net_payable_amount     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS amount_paid            NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS credit_amount          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payment_note           VARCHAR(500),
    ADD COLUMN IF NOT EXISTS payment_date           DATE,
    ADD COLUMN IF NOT EXISTS credit_party_id        UUID,
    ADD COLUMN IF NOT EXISTS credit_ledger_entry_id UUID;

-- Every invoice generated before this migration was, by definition, fully
-- payable with nothing collected against it — so the net is the total. Left at
-- the column default of 0 it would read as "this invoice is settled", which is
-- the one wrong answer.
UPDATE invoices
   SET net_payable_amount = final_payable_amount
 WHERE net_payable_amount = 0
   AND final_payable_amount > 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_invoices_credit_party'
    ) THEN
        ALTER TABLE invoices
            ADD CONSTRAINT fk_invoices_credit_party
            FOREIGN KEY (credit_party_id) REFERENCES shop_ledger_parties (id) ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_invoices_credit_ledger_entry'
    ) THEN
        ALTER TABLE invoices
            ADD CONSTRAINT fk_invoices_credit_ledger_entry
            FOREIGN KEY (credit_ledger_entry_id) REFERENCES shop_ledger_entries (id) ON DELETE SET NULL;
    END IF;

    -- Amounts are normalized in InvoiceService before the write (clamped at zero,
    -- never negative), so a row that violates this is a bug on the way in rather
    -- than data to tolerate.
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_invoices_payment_amounts'
    ) THEN
        ALTER TABLE invoices
            ADD CONSTRAINT chk_invoices_payment_amounts
            CHECK (advance_paid >= 0 AND net_payable_amount >= 0
               AND amount_paid  >= 0 AND credit_amount      >= 0);
    END IF;
END $$;

COMMENT ON COLUMN invoices.advance_paid IS
    'Already collected before this bill was raised — normally the booking deposit from tickets.payment_amount, overridable by the owner.';
COMMENT ON COLUMN invoices.net_payable_amount IS
    'final_payable_amount - advance_paid, never negative. What the customer owes at the counter today.';
COMMENT ON COLUMN invoices.amount_paid IS
    'Handed over at delivery. Equal to net_payable_amount on a full payment, less on a partial one.';
COMMENT ON COLUMN invoices.credit_amount IS
    'net_payable_amount - amount_paid, never negative. The customer''s outstanding balance, mirrored into the Cash Book as a GIVEN entry.';
COMMENT ON COLUMN invoices.payment_date IS
    'Counter day the payment belongs to; also the entry_date of the mirrored Cash Book row.';
COMMENT ON COLUMN invoices.credit_party_id IS
    'The Cash Book customer account (shop_ledger_parties) the credit was posted to. NULL when there is no credit, or when the ticket carries no usable phone number to open an account against.';
COMMENT ON COLUMN invoices.credit_ledger_entry_id IS
    'The shop_ledger_entries row mirroring credit_amount. Held so re-generating this invoice updates that row instead of posting a second one and doubling the debt.';
