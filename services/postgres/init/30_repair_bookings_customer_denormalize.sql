-- =============================================================================
-- 30_repair_bookings_customer_denormalize.sql
--
-- Denormalize customer name & mobile onto repair_bookings so the owner-side
-- Pickup Service list does not depend on a runtime JDBC lookup against
-- customer_users (which silently swallowed failures and left those fields
-- blank on the owner screen).
--
-- Also backfills existing rows from customer_users where available.
-- =============================================================================

ALTER TABLE repair_bookings
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
