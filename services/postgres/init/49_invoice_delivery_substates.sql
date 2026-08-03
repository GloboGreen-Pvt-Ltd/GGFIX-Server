-- =============================================================================
-- 49_invoice_delivery_substates.sql
--
-- Splits the single "Ready for Delivery -> Delivered to Customer" hop into
-- four steps so the customer + owner Service History rails reflect the
-- invoice generation and physical hand-off as their own audited events:
--
--   READY                  -> Ready for Delivery       (repair done, billing TBD)
--   INVOICE_GENERATED      -> Invoice Generated        (billing started)
--   INVOICE_READY          -> Invoice Ready            (invoice finalized)
--   DELIVERED_PROCESSING   -> Delivered Processing     (hand-off in progress)
--   DELIVERED              -> Delivered to Customer    (terminal)
--
-- The three new substates need master_technician_work_statuses rows so the
-- ticket-service work-status -> ticket-status advancer maps them correctly,
-- and so the owner Service Status dropdowns surface them. ticket_status
-- mirrors the substate itself (it's a lifecycle ladder value, not a generic
-- "in repair" / "ready" bucket).
--
-- Idempotent — INSERT ... ON CONFLICT (code) DO UPDATE so re-running the
-- migration refreshes the label/order without breaking anything.
--
-- The old DELIVERED_TO_CUSTOMER row stays mapped to DELIVERED (set by
-- migration 41) so historical events keep resolving correctly. We do NOT
-- rewrite past repair_booking_events rows — they continue to render under
-- their original status keys; only new emits flow through the new substages.
-- =============================================================================

BEGIN;

INSERT INTO master_technician_work_statuses
  (code, label, ticket_status, sort_order, is_active)
VALUES
  ('INVOICE_GENERATED',     'Invoice Generated',                  'INVOICE_GENERATED',    172, true),
  ('INVOICE_READY',         'Invoice Ready',                      'INVOICE_READY',        174, true),
  ('DELIVERED_PROCESSING',  'Delivered to Customer Processing',   'DELIVERED_PROCESSING', 176, true)
ON CONFLICT (code) DO UPDATE
  SET label         = EXCLUDED.label,
      ticket_status = EXCLUDED.ticket_status,
      sort_order    = EXCLUDED.sort_order,
      is_active     = EXCLUDED.is_active;

-- READY's master row currently maps to ticket_status = 'READY' (see
-- seed_technician_work_statuses_v2.sql). With the new substates in play
-- READY must NOT silently advance a ticket past the billing steps, so we
-- leave its mapping at 'READY' — but we re-order it just before the new
-- billing rows so the admin dropdown reads in lifecycle order.
UPDATE master_technician_work_statuses
   SET sort_order = 170
 WHERE code = 'READY_FOR_DELIVERY';

-- Ditto: keep DELIVERED_TO_CUSTOMER last (after the billing + processing rows).
UPDATE master_technician_work_statuses
   SET sort_order = 180
 WHERE code = 'DELIVERED_TO_CUSTOMER';

COMMIT;
