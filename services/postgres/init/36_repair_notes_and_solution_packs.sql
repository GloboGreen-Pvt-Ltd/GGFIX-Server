-- 36_repair_notes_and_solution_packs.sql
--
-- Backs the technician "Ticket Detail" screen in repair-shop-employee:
--   * `tickets.technician_photos_json` — post-acceptance photos the
--     technician uploads (separate from `device_photos_json` which the
--     customer / shop fills in at booking time).
--   * `ticket_solution_packs` — knowledge-base attachments tied to a
--     ticket. `pack_type='REFERENCE'` is the existing solution the
--     technician views; `pack_type='NEW'` is the one they upload when
--     they figure out a new fix.
--
-- The `repair_notes` table already exists in 01_schema.sql; no schema
-- change here. Only the controller binding is added in code.

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS technician_photos_json TEXT;

CREATE TABLE IF NOT EXISTS ticket_solution_packs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id     UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    shop_id       UUID NOT NULL,
    pack_type     VARCHAR(20) NOT NULL,   -- 'REFERENCE' | 'NEW'
    title         VARCHAR(255),
    description   TEXT,
    file_url      TEXT,
    file_name     VARCHAR(255),
    uploaded_by   UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ticket_solution_packs_ticket
    ON ticket_solution_packs(ticket_id);
CREATE INDEX IF NOT EXISTS idx_ticket_solution_packs_shop_type
    ON ticket_solution_packs(shop_id, pack_type);
