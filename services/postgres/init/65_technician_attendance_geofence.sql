-- =============================================================================
-- 65_technician_attendance_geofence.sql
--
-- Stores the GPS proof captured at each attendance punch for the employee-app
-- 100m shop geofence. The gate itself is enforced in TechnicianService
-- (haversine vs shops.latitude/longitude); these columns keep the evidence next
-- to the check-in / check-out that it justified, mirroring the pickup-person
-- flow's repair_booking_events GPS columns (migration 44).
--
--   * check_in_latitude / check_in_longitude    — where the employee punched in
--   * check_in_distance_meters                  — metres from the shop at check-in
--   * check_out_latitude / check_out_longitude  — where the employee punched out
--
-- All ADD COLUMN statements are guarded with IF NOT EXISTS so the file is
-- idempotent against partially-applied dev databases.
-- =============================================================================

ALTER TABLE technician_attendance
    ADD COLUMN IF NOT EXISTS check_in_latitude       NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS check_in_longitude      NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS check_in_distance_meters INTEGER,
    ADD COLUMN IF NOT EXISTS check_out_latitude      NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS check_out_longitude     NUMERIC(10, 7);
