-- =============================================================================
-- 44_pickup_reached_received_shop.sql
--
-- Adds the columns needed by the pickup-person "Reached Shop" / "Received at
-- Shop" flow:
--   * repair_booking_events gains latitude / longitude / distance_meters so
--     the GPS proof that the pickup person was within the shop radius is
--     stored next to the status transition that it justified.
--   * repair_bookings gains reached_shop_at / received_at_shop_at as cheap
--     denormalised pointers for owner-side filtering (so the shop-side list
--     can sort/filter without scanning the event log).
--
-- All ADD COLUMN statements are guarded with IF NOT EXISTS so the file is
-- idempotent against partially-applied dev databases.
-- =============================================================================

ALTER TABLE repair_booking_events
    ADD COLUMN IF NOT EXISTS latitude         DECIMAL(10, 7),
    ADD COLUMN IF NOT EXISTS longitude        DECIMAL(10, 7),
    ADD COLUMN IF NOT EXISTS distance_meters  INTEGER;

ALTER TABLE repair_bookings
    ADD COLUMN IF NOT EXISTS reached_shop_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS received_at_shop_at  TIMESTAMPTZ;

-- Backfill the new timestamp columns from any prior REACHED_SHOP /
-- RECEIVED_AT_SHOP events that already exist (e.g. test bookings created
-- before this migration). Keeps the shop-side list consistent on day one.
UPDATE repair_bookings rb
   SET reached_shop_at = ev.created_at
  FROM (
      SELECT booking_id, MIN(created_at) AS created_at
        FROM repair_booking_events
       WHERE status = 'REACHED_SHOP'
       GROUP BY booking_id
  ) ev
 WHERE rb.id = ev.booking_id
   AND rb.reached_shop_at IS NULL;

UPDATE repair_bookings rb
   SET received_at_shop_at = ev.created_at
  FROM (
      SELECT booking_id, MIN(created_at) AS created_at
        FROM repair_booking_events
       WHERE status = 'RECEIVED_AT_SHOP'
       GROUP BY booking_id
  ) ev
 WHERE rb.id = ev.booking_id
   AND rb.received_at_shop_at IS NULL;
