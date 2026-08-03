-- Backfill the orphan WALK_IN service booking for tracking #CSPEN9053281.
-- Restores the missing repair_bookings row (referenced by customer_orders.reference_id
-- 06fb4b4c-...) and the three timeline events that CustomerOrderMirrorService would
-- have written had the original mirror call not been wiped out-of-band.
--
-- All values are sourced from:
--   - tickets row id=4f29a246-15f2-4979-9f56-12316ef5ac2b
--   - customer_orders row id=06fb4b4c-593f-443d-b641-84083d939810
--   - customers row id=e96b5ea6-4584-40d2-9f54-3518e48bc84d (platform_user_id link)
--
-- Run with:
--   psql -h localhost -U postgres -d repairshop -v ON_ERROR_STOP=1 -f backfill_walkin_orphan_cspen9053281.sql
-- (set PGPASSWORD=root in the env first)
--
-- Safe to re-run: it's wrapped in BEGIN/COMMIT and uses fixed ids; if the row already
-- exists you'll get a unique-key error and the transaction aborts cleanly.

BEGIN;

INSERT INTO repair_bookings (
  id, booking_number, customer_user_id, shop_id, ticket_id,
  brand_id, model_id, ram_option_id, storage_option_id, color,
  service_mode, front_image_url, back_image_url, issue_summary,
  estimate_amount, status, customer_approval, device_pin,
  estimated_ready_at, estimated_delivery_at, created_at, updated_at
) VALUES (
  'afa4bf1a-9017-49fa-92d4-c43006d1ea9c',
  '#CSPEN9053281',
  '9f52e0e8-61cc-41a3-922e-f5485615a890',
  '66879749-87df-41d0-8599-73958d2426fe',
  '4f29a246-15f2-4979-9f56-12316ef5ac2b',
  '30918658-d7a0-4303-8c63-7d78b122d519',
  'dc563c70-f180-4bd8-880b-02c886190ada',
  '6c3180b4-5fd6-48b5-9203-1850b313eaee',
  'f6e117fb-83be-4430-b0b2-02ba439ad5a2',
  'Beige',
  'WALK_IN',
  'https://res.cloudinary.com/dg6c0g4gi/image/upload/v1780479023/ggifx/repair/g7hyc4tlxwxbyggomips.png',
  'https://res.cloudinary.com/dg6c0g4gi/image/upload/v1780479027/ggifx/repair/ymcdb1hinagulftewg1p.png',
  'Display Combo',
  2000.00,
  'ORDER_PLACED',
  'DONE',
  '4,2,5,3,6,9',
  '2026-06-03 14:59:54.865+05:30',
  '2026-06-03 18:59:54.865+05:30',
  '2026-06-03 15:19:43.130763+05:30',
  '2026-06-03 15:19:43.130763+05:30'
);

-- Three events covering Service Accepted phase up to where the ticket actually is
-- (status=CREATED, technician assigned but not yet accepted). Matches what
-- emitTimelineEvents would have written across the original create + assign calls.
INSERT INTO repair_booking_events (booking_id, status, note, actor, created_at) VALUES
  ('afa4bf1a-9017-49fa-92d4-c43006d1ea9c', 'ORDER_PLACED',        'Booking created by shop',        'SHOP', '2026-06-03 15:19:43+05:30'),
  ('afa4bf1a-9017-49fa-92d4-c43006d1ea9c', 'ASSIGN_TECHNICIAN',   'Assigned to Nandhakumar S',      'SHOP', '2026-06-03 18:25:29+05:30'),
  ('afa4bf1a-9017-49fa-92d4-c43006d1ea9c', 'ASSIGN_NOT_ACCEPTED', 'Awaiting technician acceptance', 'SHOP', '2026-06-03 18:25:29+05:30');

COMMIT;

SELECT id, booking_number, status, service_mode FROM repair_bookings WHERE id = 'afa4bf1a-9017-49fa-92d4-c43006d1ea9c';
SELECT status, note, actor, created_at FROM repair_booking_events WHERE booking_id = 'afa4bf1a-9017-49fa-92d4-c43006d1ea9c' ORDER BY created_at;
