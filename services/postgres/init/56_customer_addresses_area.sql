-- The customer-app form labels this field "Area", but the legacy column is
-- named `locality`. Add a dedicated `area` column to match the form, backfill
-- from `locality`, and let the API dual-write area -> locality so older
-- readers (RepairBooking pickup display, owner CRM, etc.) keep working.

ALTER TABLE customer_addresses ADD COLUMN IF NOT EXISTS area VARCHAR(255);

UPDATE customer_addresses
   SET area = locality
 WHERE area IS NULL
   AND locality IS NOT NULL;
