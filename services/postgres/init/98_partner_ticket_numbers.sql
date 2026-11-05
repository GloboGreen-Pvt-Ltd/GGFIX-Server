-- =============================================================================
-- 98_partner_ticket_numbers.sql
--
-- Partner-scoped ticket numbers: {first letter of the business name}TS-{seq},
-- e.g. GTS-001, GTS-002 … GTS-999, GTS-1000.
--
-- A partner is the shop owner (shops.owner_user_id; the shop's own id when the
-- shop has no owner row). Every shop of one owner shares that owner's counter;
-- a new partner starts at 001. ticket-service bumps the counter with one
-- INSERT … ON CONFLICT DO UPDATE … RETURNING inside the ticket's transaction,
-- so two simultaneous bookings can never get the same number and nothing ever
-- resets it (logout / reinstall / restart don't touch the database).
--
-- tickets.partner_id is set only on tickets numbered this way. Existing tickets
-- keep their tracking_id (CSPEN…) and stay NULL here — their ids, numbers and
-- relationships are untouched, and NULLs never collide in the unique index.
--
-- Idempotent. Apply BEFORE deploying ticket-service (ddl-auto: validate).
-- =============================================================================

CREATE TABLE IF NOT EXISTS partner_ticket_sequences (
    partner_id  UUID        PRIMARY KEY,
    last_value  BIGINT      NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS partner_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tickets_partner_tracking_id
    ON tickets (partner_id, tracking_id);
