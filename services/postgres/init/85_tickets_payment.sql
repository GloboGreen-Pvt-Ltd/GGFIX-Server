-- =============================================================================
-- 85_tickets_payment.sql
--
-- Records what the customer paid at the counter when the booking was taken.
--
-- WHY
-- The shop-app "Service Booking Devices List" screen (the last step before
-- Submit) asks for a payment mode and an amount, and Device Details → Price
-- Summary reads it back as Estimated Total / Advance-or-Full Payment / Balance.
-- Until now the ticket knew only what the repair was ESTIMATED at — nothing
-- about money that had already changed hands — so "has this customer paid
-- anything on this job?" could only be answered from the Cash Book
-- (migration 82), and only if the owner remembered to write the entry.
--
-- WHY NOT REUSE AN EXISTING COLUMN
-- final_price means "what the repair ended up costing" and customer_approval
-- means "the customer said go ahead". Neither is a payment. Overloading either
-- one makes a deposit indistinguishable from a re-quote or a consent flag the
-- first time someone reads the row back.
--
-- THE FIVE COLUMNS
--   payment_type     ADVANCE | FULL — what the amount means.
--   payment_amount   what was actually collected.
--   balance_amount   still owed. Denormalized on purpose, see below.
--   payment_status   PAID once money is recorded; PENDING while it is not.
--   payment_paid_at  when. Stamped by TicketService, never by the client — a
--                    device clock is not evidence of when cash was taken.
--
-- balance_amount is derived (applicable total − payment_amount) yet stored,
-- unlike the earlier draft of this migration which left it to the client. The
-- reason is that "what is still owed" is a number the shop reads off a list —
-- outstanding balances across bookings — and recomputing it per row means every
-- caller has to agree on which total applies (final_price when set, else
-- estimated_price). One writer, in TicketService, keeps that rule in a single
-- place. Every path that can change either price recomputes it in the same
-- transaction, so it cannot drift behind a re-estimate.
--
-- All five stay NULLable: every ticket booked before this migration has no
-- payment, and a walk-in that pays only on delivery is a normal case, not a
-- missing value.
--
-- Idempotent, including over the earlier draft of this file that shipped a
-- single `paid_amount` column — that column is renamed rather than re-added, so
-- a shop that already ran the draft keeps the amounts it recorded.
-- =============================================================================

-- Draft → final: carry any amounts already captured under the old name.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tickets' AND column_name = 'paid_amount'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'tickets' AND column_name = 'payment_amount'
    ) THEN
        ALTER TABLE tickets RENAME COLUMN paid_amount TO payment_amount;
    END IF;
END $$;

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS payment_type    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS payment_amount  DECIMAL(12, 2),
    ADD COLUMN IF NOT EXISTS balance_amount  DECIMAL(12, 2),
    ADD COLUMN IF NOT EXISTS payment_status  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS payment_paid_at TIMESTAMPTZ;

-- Values are normalized in TicketService before the write, so a row that
-- violates either constraint is a bug on the way in rather than data to
-- tolerate.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_tickets_payment_type'
    ) THEN
        ALTER TABLE tickets
            ADD CONSTRAINT chk_tickets_payment_type
            CHECK (payment_type IS NULL OR payment_type IN ('ADVANCE', 'FULL'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_tickets_payment_status'
    ) THEN
        ALTER TABLE tickets
            ADD CONSTRAINT chk_tickets_payment_status
            CHECK (payment_status IS NULL OR payment_status IN ('PAID', 'PENDING'));
    END IF;
END $$;

-- Backfill for rows written by the draft: they carry an amount and a type but
-- none of the three columns added here. Status and balance are recoverable;
-- the timestamp is not, so it is left NULL rather than invented from
-- updated_at, which any later edit would have moved.
UPDATE tickets
   SET payment_status = 'PAID',
       balance_amount = GREATEST(COALESCE(final_price, estimated_price, 0) - payment_amount, 0)
 WHERE payment_amount IS NOT NULL
   AND payment_amount > 0
   AND payment_status IS NULL;

-- Nothing collected → the whole applicable total is outstanding. Stated rather
-- than left NULL so "what is owed on this job" answers without a COALESCE at
-- every call site.
UPDATE tickets
   SET payment_status = 'PENDING',
       balance_amount = COALESCE(final_price, estimated_price)
 WHERE payment_amount IS NULL
   AND payment_status IS NULL
   AND COALESCE(final_price, estimated_price) IS NOT NULL;

COMMENT ON COLUMN tickets.payment_type IS
    'How the customer paid at booking time: ADVANCE (deposit, balance due on delivery) or FULL. NULL = nothing collected up front.';
COMMENT ON COLUMN tickets.payment_amount IS
    'Amount collected, in rupees. For a multi-device booking the payment is split across the devices'' tickets in proportion to each device''s estimate, so the parts sum to what the customer handed over.';
COMMENT ON COLUMN tickets.balance_amount IS
    'Still owed: COALESCE(final_price, estimated_price) - payment_amount, never negative. Recomputed by TicketService whenever either price or the payment changes.';
COMMENT ON COLUMN tickets.payment_status IS
    'PAID once payment_amount is recorded, PENDING while nothing has been collected.';
COMMENT ON COLUMN tickets.payment_paid_at IS
    'When the payment was recorded, stamped server-side. Refreshed only when the type or amount actually changes, so an unrelated edit does not re-date the receipt.';
