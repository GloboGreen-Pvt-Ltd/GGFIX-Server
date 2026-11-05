-- =============================================================================
-- 82_shop_ledger_entries.sql
--
-- The running account behind every customer / supplier row (migration 81).
-- One row per movement of money between the shop and ONE named party:
--   RECEIVED — the party handed money to the shop
--   GIVEN    — the shop handed money to the party (goods on credit, a refund)
--
-- WHY direction AND NOT A SIGNED amount
-- Same reason as the party CHECK: a stray minus sign would silently turn a
-- receipt into a payment, and both gross figures have to be showable without
-- re-deriving them from the sign.
--
-- WHY THERE IS NO balance COLUMN, HERE OR ON shop_ledger_parties
-- A party's balance is SUM(GIVEN) − SUM(RECEIVED) over these rows. Storing it
-- would be a second source of truth that drifts the moment an entry is edited,
-- back-dated or deleted — and this is money a shop argues with a customer about,
-- so a stale figure is worse than a slow one. ShopLedgerEntryService derives it
-- with one GROUP BY, which the (shop_id, party_id) index below serves directly.
--
-- SIGN CONVENTION (matches how the app labels it)
--   balance > 0  -> "Due"      the party owes the shop
--   balance < 0  -> "Advance"  the party has paid ahead
--   balance = 0  -> settled
--
-- ON DELETE CASCADE: deleting an account is the owner saying "this person is not
-- in my book". Leaving their money behind, invisible but still summing into
-- reports, is the one outcome nobody wants.
--
-- TIMESTAMP (not TIMESTAMPTZ) and NUMERIC(14,2) match shop_ledger_parties (81)
-- and invoices (58) so the money tables in this service all read the same.
--
-- Idempotent — IF NOT EXISTS throughout, so re-running is a no-op.
-- =============================================================================

CREATE TABLE IF NOT EXISTS shop_ledger_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id     UUID NOT NULL,
    party_id    UUID NOT NULL,

    direction   VARCHAR(10)    NOT NULL,               -- RECEIVED | GIVEN
    amount      NUMERIC(14, 2) NOT NULL,
    entry_date  DATE           NOT NULL,
    note        VARCHAR(500),

    created_by  UUID,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_shop_ledger_entries_party
        FOREIGN KEY (party_id) REFERENCES shop_ledger_parties (id) ON DELETE CASCADE,
    CONSTRAINT ck_shop_ledger_entries_direction CHECK (direction IN ('RECEIVED', 'GIVEN')),
    CONSTRAINT ck_shop_ledger_entries_amount    CHECK (amount > 0)
);

-- Serves the party statement ("this account, newest first") and the balance
-- GROUP BY that the account list reads for every row.
CREATE INDEX IF NOT EXISTS idx_shop_ledger_entries_party
    ON shop_ledger_entries (shop_id, party_id, entry_date DESC);

-- Serves the Today / This Week / Month chips, which sweep every party in the
-- shop over a date window and would otherwise scan the whole table.
CREATE INDEX IF NOT EXISTS idx_shop_ledger_entries_shop_date
    ON shop_ledger_entries (shop_id, entry_date DESC);
