-- =============================================================================
-- 23_repair_bookings_customer_denormalize.sql
--
-- Keep repair_bookings aligned with the current order/ticket entities and
-- denormalize customer name/mobile so shop/owner pickup screens can show the
-- customer immediately, while still keeping a runtime customer_users fallback
-- for older rows.
-- =============================================================================

BEGIN;

ALTER TABLE repair_bookings
    ADD COLUMN IF NOT EXISTS assigned_technician_id UUID,
    ADD COLUMN IF NOT EXISTS customer_name   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS customer_mobile VARCHAR(50);

UPDATE repair_bookings rb
SET    customer_name   = COALESCE(NULLIF(rb.customer_name, ''), cu.full_name),
       customer_mobile = COALESCE(NULLIF(rb.customer_mobile, ''), cu.mobile)
FROM   customer_users cu
WHERE  rb.customer_user_id = cu.id
  AND  (
       rb.customer_name IS NULL OR rb.customer_name = ''
       OR rb.customer_mobile IS NULL OR rb.customer_mobile = ''
  );

COMMIT;
