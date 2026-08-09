-- =============================================================================
-- 81_shop_ledger_parties.sql
--
-- Backs the shop app -> Home -> Shortcuts -> Cash Book -> Customer / Supplier
-- tabs: the shop's address book of the people it keeps a running account with.
--
-- WHY A SEPARATE TABLE FROM `customers`
-- `customers` is the person on a repair ticket — it is created by a booking and
-- carries device/booking context. A ledger party is an ACCOUNT the counter keeps
-- (the shop next door it buys screens from, the walk-in who pays weekly), and a
-- supplier has no ticket at all. Folding suppliers into a table whose every
-- other row means "someone who brought a phone in" would make both books lie.
--
-- WHY party_type IS A COLUMN AND NOT TWO TABLES
-- Customer and supplier rows are identical in shape and are read by the same
-- screen with the same query; splitting them would duplicate the entity, the
-- repository and every route for no gain. The CHECK keeps the domain closed.
--
-- WHY name IS NOT NULL BUT MAY EQUAL THE PHONE
-- The add screen lets the owner save with a phone and no name (this is how a
-- number gets jotted down mid-rush). The service fills `name` from `phone` in
-- that case rather than storing NULL, so every list row has something to render
-- and sorting by name never has to special-case a hole.
--
-- TIMESTAMP (not TIMESTAMPTZ) matches shop_cash_book (migration 80) and
-- invoices (58) so the money-adjacent tables in this service all read the same.
--
-- Idempotent — IF NOT EXISTS throughout, so re-running is a no-op.
-- =============================================================================

CREATE TABLE IF NOT EXISTS shop_ledger_parties (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id     UUID NOT NULL,

    party_type  VARCHAR(10)  NOT NULL,                 -- CUSTOMER | SUPPLIER
    name        VARCHAR(120) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,

    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT ck_shop_ledger_parties_type CHECK (party_type IN ('CUSTOMER', 'SUPPLIER'))
);

-- The list query is always "this shop, this party type, newest first".
CREATE INDEX IF NOT EXISTS idx_shop_ledger_parties_shop_type
    ON shop_ledger_parties (shop_id, party_type, created_at DESC);

-- Importing from the phone's contact list is the main way rows get added, and a
-- contact book is full of near-duplicates. One account per number per book, so
-- importing the same contact twice updates the name instead of splitting the
-- account in two. Scoped to party_type because the same person can legitimately
-- be both a customer and a supplier of the shop.
CREATE UNIQUE INDEX IF NOT EXISTS ux_shop_ledger_parties_phone
    ON shop_ledger_parties (shop_id, party_type, phone);
