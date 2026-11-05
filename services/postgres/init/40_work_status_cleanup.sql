-- 40_work_status_cleanup.sql
--
-- Trim the Technician Work Status dropdown (admin Master Data page):
--   * Remove TECHNICIAN_DIAGNOSIS_UPDATED  (label "Technician Diagnosis Updated")
--   * Remove CANCELLED                      (label "Work Cancelled")
--   * Insert RETURN_FOR_DELIVERY between READY ("Ready for Delivery")
--     and DELIVERED ("Delivered to Customer") with sort_order 165.
--
-- Codes here match the rows actually present in master_technician_work_statuses
-- (which were seeded from the SHOP_BOOKING_STATUS_OPTIONS keys, not the
-- earlier v2 seed file). Re-running is safe: DELETE is idempotent, INSERT
-- guards on the unique code constraint.

DELETE FROM master_technician_work_statuses
WHERE code IN ('TECHNICIAN_DIAGNOSIS_UPDATED', 'CANCELLED', 'WORK_CANCELLED');

INSERT INTO master_technician_work_statuses
  (code, label, ticket_status, sort_order, is_active)
VALUES
  ('RETURN_FOR_DELIVERY', 'Return for Delivery', 'READY', 165, true)
ON CONFLICT (code) DO NOTHING;
