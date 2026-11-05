-- Add Indian-address fields to customer_addresses. The form now collects
-- State > District > Taluk > Area > Door no./Street > Pincode. We keep the
-- legacy `city` column populated (dual-writing district -> city in the API)
-- so existing readers (tickets, invoices, shop owner views) keep working
-- without coordinated rollout.

ALTER TABLE customer_addresses ADD COLUMN IF NOT EXISTS district VARCHAR(255);
ALTER TABLE customer_addresses ADD COLUMN IF NOT EXISTS taluk    VARCHAR(255);

-- Backfill district from city for pre-existing rows so reads via the new
-- column return something. New writes go through the API, which dual-writes.
UPDATE customer_addresses
   SET district = city
 WHERE district IS NULL
   AND city IS NOT NULL;
