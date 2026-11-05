-- =============================================================================
-- 72_customer_users_addresses_jsonb.sql   (PHASE A — additive, non-breaking)
--
-- Begins collapsing customer_addresses into a jsonb array on customer_users, and
-- snapshots the chosen address onto each order so orders no longer need to join
-- customer_addresses (that read-cutover + the table drop happen in Phase B).
--
-- This migration is STRICTLY ADDITIVE:
--   * customer_users.addresses  jsonb  — the address book, one array per user
--   * repair_bookings.pickup_address / sell_orders.address /
--     customer_pickup_orders.address  jsonb  — per-order address snapshot
--   * all backfilled from customer_addresses
--   * customer_addresses, its FKs, every read-site and both apps are UNTOUCHED,
--     so nothing changes behaviour until Phase B.
--
-- Each jsonb address element PRESERVES the original customer_addresses.id as its
-- "id" so the existing id contract (bookings store address_id, the app resolves
-- addresses by id) keeps resolving after the source flips in Phase B.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS + guarded backfills.
-- =============================================================================

BEGIN;

ALTER TABLE customer_users
    ADD COLUMN IF NOT EXISTS addresses jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE repair_bookings        ADD COLUMN IF NOT EXISTS pickup_address jsonb;
ALTER TABLE sell_orders            ADD COLUMN IF NOT EXISTS address        jsonb;
ALTER TABLE customer_pickup_orders ADD COLUMN IF NOT EXISTS address        jsonb;

-- Shared shape for one address element (camelCase keys, matching AddressResponse).
-- Backfill the per-user address book.
UPDATE customer_users u
SET addresses = COALESCE((
    SELECT jsonb_agg(jsonb_build_object(
               'id',          ca.id,
               'label',       ca.label,
               'fullName',    ca.full_name,
               'mobile',      ca.mobile,
               'pincode',     ca.pincode,
               'locality',    ca.locality,
               'area',        ca.area,
               'addressLine', ca.address_line,
               'city',        ca.city,
               'district',    ca.district,
               'taluk',       ca.taluk,
               'state',       ca.state,
               'latitude',    ca.latitude,
               'longitude',   ca.longitude,
               'isDefault',   COALESCE(ca.is_default, false),
               'createdAt',   ca.created_at,
               'updatedAt',   ca.updated_at
           ) ORDER BY ca.created_at)
    FROM customer_addresses ca
    WHERE ca.customer_user_id = u.id
), '[]'::jsonb)
WHERE EXISTS (SELECT 1 FROM customer_addresses ca WHERE ca.customer_user_id = u.id)
  AND (u.addresses IS NULL OR u.addresses = '[]'::jsonb);

-- Per-order snapshot builder (same shape, minus the book-keeping timestamps).
-- repair_bookings.pickup_address
UPDATE repair_bookings rb
SET pickup_address = jsonb_build_object(
        'id', ca.id, 'label', ca.label, 'fullName', ca.full_name, 'mobile', ca.mobile,
        'pincode', ca.pincode, 'locality', ca.locality, 'area', ca.area,
        'addressLine', ca.address_line, 'city', ca.city, 'district', ca.district,
        'taluk', ca.taluk, 'state', ca.state, 'latitude', ca.latitude, 'longitude', ca.longitude)
FROM customer_addresses ca
WHERE ca.id = rb.pickup_address_id
  AND rb.pickup_address_id IS NOT NULL
  AND rb.pickup_address IS NULL;

-- sell_orders.address
UPDATE sell_orders so
SET address = jsonb_build_object(
        'id', ca.id, 'label', ca.label, 'fullName', ca.full_name, 'mobile', ca.mobile,
        'pincode', ca.pincode, 'locality', ca.locality, 'area', ca.area,
        'addressLine', ca.address_line, 'city', ca.city, 'district', ca.district,
        'taluk', ca.taluk, 'state', ca.state, 'latitude', ca.latitude, 'longitude', ca.longitude)
FROM customer_addresses ca
WHERE ca.id = so.address_id
  AND so.address_id IS NOT NULL
  AND so.address IS NULL;

-- customer_pickup_orders.address
UPDATE customer_pickup_orders po
SET address = jsonb_build_object(
        'id', ca.id, 'label', ca.label, 'fullName', ca.full_name, 'mobile', ca.mobile,
        'pincode', ca.pincode, 'locality', ca.locality, 'area', ca.area,
        'addressLine', ca.address_line, 'city', ca.city, 'district', ca.district,
        'taluk', ca.taluk, 'state', ca.state, 'latitude', ca.latitude, 'longitude', ca.longitude)
FROM customer_addresses ca
WHERE ca.id = po.address_id
  AND po.address_id IS NOT NULL
  AND po.address IS NULL;

COMMIT;
