-- =============================================================================
-- 35_shops_working_hours.sql
--
-- Adds shop working-hours fields to the `shops` table for the admin panel's
-- Edit Business Location form. Captures three things:
--
--   working_days  : preset day range. One of MON_FRI | MON_SAT | MON_SUN.
--                   Kept as VARCHAR rather than ENUM so it's easy to add
--                   presets later without a type migration.
--   opening_time  : human-readable display string (e.g. "08:00 AM"),
--                   matching the existing pickup_from_time / pickup_to_time
--                   shape on this same table.
--   closing_time  : ditto for closing.
--
-- These are distinct from the per-day pickup capacity slots in
-- shop_pickup_slots — they represent the shop's overall opening hours, not
-- pickup-only windows.
-- =============================================================================

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS working_days  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS opening_time  VARCHAR(16),
    ADD COLUMN IF NOT EXISTS closing_time  VARCHAR(16);
