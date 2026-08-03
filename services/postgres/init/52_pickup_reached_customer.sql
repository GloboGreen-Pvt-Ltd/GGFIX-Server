-- =============================================================================
-- 52_pickup_reached_customer.sql
--
-- Adds the column needed by the pickup-person "Reached Customer Location"
-- transition, a new step between PICKUP_ON_THE_WAY and REPAIR_ESTIMATE_PROCESSING
-- that proves the pickup person arrived at the customer's saved pickup
-- address (50m GPS gate against customer_addresses.latitude/longitude).
--
--   * repair_bookings gains reached_customer_at as the denormalised pointer
--     for owner-side filtering / timeline display without scanning events.
--   * repair_booking_events already has latitude / longitude / distance_meters
--     (added in migration 44 for REACHED_SHOP), so the same GPS proof columns
--     are reused for the REACHED_CUSTOMER_LOCATION event row.
--
-- IF NOT EXISTS makes this idempotent.
-- =============================================================================

ALTER TABLE repair_bookings
    ADD COLUMN IF NOT EXISTS reached_customer_at  TIMESTAMPTZ;

-- Backfill from any prior REACHED_CUSTOMER_LOCATION events (defensive; the
-- status is new so we don't expect any rows yet).
UPDATE repair_bookings rb
   SET reached_customer_at = ev.created_at
  FROM (
      SELECT booking_id, MIN(created_at) AS created_at
        FROM repair_booking_events
       WHERE status = 'REACHED_CUSTOMER_LOCATION'
       GROUP BY booking_id
  ) ev
 WHERE rb.id = ev.booking_id
   AND rb.reached_customer_at IS NULL;
