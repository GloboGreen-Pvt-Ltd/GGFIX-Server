-- =============================================================================
-- 32_drop_orphan_customer_orders.sql
--
-- customer_orders.reference_id has no FK to repair_bookings/sell_orders/etc, so
-- when a referenced row is removed out-of-band (test reset, dev profile flip,
-- manual psql) the customer_orders row dangles. The customer mobile app fans
-- out one GET /repair-bookings/{ref} per row and a 403/404 on the orphan
-- bounces the user back to Login.
--
-- This migration removes existing REPAIR/PICKUP/ENQUIRY orphans whose
-- referenced repair_booking is gone. Idempotent: a clean DB matches zero rows.
-- Future orphans are filtered out by the order-service list endpoint.
-- =============================================================================

DELETE FROM customer_orders co
WHERE co.order_type IN ('REPAIR', 'PICKUP', 'ENQUIRY')
  AND co.reference_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM repair_bookings rb WHERE rb.id = co.reference_id
  );
