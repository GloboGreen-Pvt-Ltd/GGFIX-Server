-- Nearby GGFIX shops (Google Maps location feature).
--
-- Supports GET /shops/nearby (shop-service), which now always filters
-- is_active = true and only considers shops with coordinates. No new columns
-- are needed here — pincode, latitude/longitude, opening/closing hours etc.
-- already exist on shops from earlier migrations. This partial index just
-- narrows the row set that query scans, the same style as idx_shops_pickup_enabled.

CREATE INDEX IF NOT EXISTS idx_shops_active_geo
    ON shops (is_active)
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
