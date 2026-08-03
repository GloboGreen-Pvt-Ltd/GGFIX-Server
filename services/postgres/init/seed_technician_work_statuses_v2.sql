-- Seed the canonical Technician Work Status dropdown list. Removes the
-- placeholder Start / In Progress / Done seeded by migration 39 and inserts
-- the statuses the shop actually uses. Codes are upper-snake'd labels;
-- ticket_status mirrors the backend inference rule:
--   "complete" / "ready" / "delivered" / "return" → READY
--   everything else → IN_REPAIR

DELETE FROM master_technician_work_statuses
WHERE code IN ('START', 'IN_PROGRESS', 'DONE');

INSERT INTO master_technician_work_statuses
  (code, label, ticket_status, sort_order, is_active)
VALUES
  ('TECHNICIAN_ASSIGNED',                       'Technician Assigned',                       'IN_REPAIR',     10, true),
  ('TECHNICIAN_ACCEPTED',                       'Technician Accepted',                       'IN_REPAIR',     20, true),
  ('TECHNICIAN_WORK_STARTED',                   'Technician Work Started',                   'IN_REPAIR',     30, true),
  ('TECHNICIAN_UPLOADED_DEVICE_IMAGES',         'Technician Uploaded Device Images',         'IN_REPAIR',     40, true),
  ('TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED',      'Technician Issue Verified & Updated', 'IN_REPAIR', 60, true),
  ('WAITING_FOR_CUSTOMER_APPROVAL',             'Waiting for Customer Approval',             'IN_REPAIR',     70, true),
  ('CUSTOMER_APPROVED',                         'Customer Approved',                         'IN_REPAIR',     80, true),
  ('CUSTOMER_REJECTED',                         'Customer Rejected',                         'IN_REPAIR',     90, true),
  ('REPAIR_WORK_IN_PROGRESS',                   'Repair Work In Progress',                   'IN_REPAIR',    100, true),
  ('PARTS_REQUIRED',                            'Parts Required',                            'IN_REPAIR',    110, true),
  ('PARTS_REPLACED',                            'Parts Replaced',                            'IN_REPAIR',    120, true),
  ('QUALITY_CHECK_STARTED',                     'Quality Check Started',                     'IN_REPAIR',    130, true),
  ('QUALITY_CHECK_COMPLETED',                   'Quality Check Completed',                   'READY',        140, true),
  ('REPAIR_COMPLETED',                          'Repair Completed',                          'READY',        150, true),
  ('READY_FOR_DELIVERY',                        'Ready for Delivery',                        'READY',        160, true),
  ('RETURN_FOR_DELIVERY',                       'Return for Delivery',                       'READY',        165, true),
  ('DELIVERED_TO_CUSTOMER',                     'Delivered to Customer',                     'READY',        170, true)
ON CONFLICT (code) DO NOTHING;
