-- =============================================================================
-- 03_tickets_extended_columns.sql
--
-- Backfills the columns the ticket-service JPA entity (Ticket.java) writes/reads
-- for databases created before 01_schema.sql included the extended ticket fields.
--
-- Idempotent: uses ADD COLUMN IF NOT EXISTS so re-running this file is safe.
-- Apply with:
--   psql -h <host> -U <user> -d <db> -f 03_tickets_extended_columns.sql
-- =============================================================================

BEGIN;

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS customer_name           VARCHAR(200),
    ADD COLUMN IF NOT EXISTS customer_phone          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS device_display_name     VARCHAR(200),
    ADD COLUMN IF NOT EXISTS device_image_url        VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS repair_services_summary VARCHAR(500),
    ADD COLUMN IF NOT EXISTS price_items_json        TEXT,
    ADD COLUMN IF NOT EXISTS missing_parts_json      TEXT,
    ADD COLUMN IF NOT EXISTS device_photos_json      TEXT,
    ADD COLUMN IF NOT EXISTS device_security_type    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS device_security_value   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS customer_approval       BOOLEAN,
    ADD COLUMN IF NOT EXISTS estimated_ready_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS estimated_delivery_at   TIMESTAMPTZ;

-- Helpful indexes for the bookings list query patterns used by the mobile app.
CREATE INDEX IF NOT EXISTS idx_tickets_customer_phone     ON tickets(shop_id, customer_phone);
CREATE INDEX IF NOT EXISTS idx_tickets_device_display     ON tickets(shop_id, device_display_name);

COMMIT;
