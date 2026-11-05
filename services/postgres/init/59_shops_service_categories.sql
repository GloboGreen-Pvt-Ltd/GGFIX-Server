-- =============================================================================
-- 59_shops_service_categories.sql
--
-- Adds per-shop "what we repair" snapshot. The owner Shop Information screen
-- has a two-column grid (Android / Apple) where each repair category can be
-- toggled on or off; previously the toggles were UI-only and reset to "all
-- selected" every time the screen was opened because nothing was persisted.
--
-- Format: JSON with two string arrays, e.g.
--   {"android":["Screen Repair","Battery Replacement"],"apple":["Screen Repair"]}
--
-- Stored as TEXT (not JSONB) for simplicity — the value is opaque to the
-- backend, which just round-trips it between the PATCH body, the column,
-- and the ShopLocationView response so the mobile client can parse it.
-- NULL means "service list never set" — the client should fall back to its
-- default "all categories selected" until the owner saves once.
-- =============================================================================

ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS service_categories_json TEXT;
