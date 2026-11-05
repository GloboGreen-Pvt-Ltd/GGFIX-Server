-- =============================================================================
-- 28_customers_platform_link.sql
--
-- Adds platform_user_id to per-shop `customers` so a shop's customer row can be
-- linked to a platform-wide `customer_users` row. Enables the owner New-Booking
-- search to surface platform customers (registered through the customer app)
-- and to materialize a shop-scoped customer row the first time the owner picks
-- one for booking.
--
-- ticket-service runs Hibernate with validate-only schema check, so the column
-- must exist before the new Customer.platformUserId field can be deployed.
-- =============================================================================

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS platform_user_id UUID;

CREATE INDEX IF NOT EXISTS idx_customers_shop_platform_user
    ON customers(shop_id, platform_user_id);
