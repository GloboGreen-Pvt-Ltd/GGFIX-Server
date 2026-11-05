-- Migration 68: Remove the marketplace "shopping" feature end-to-end.
--
-- Scope: product catalog, shopping cart, buy-orders, and the Sell/buy listings
-- board are being retired. The customer<->shop CHAT feature that also lives in
-- marketplace-service is INTENTIONALLY RETAINED — its tables
-- (customer_chat_threads / customer_chat_messages) are NOT touched here.
--
-- Backend counterpart: the corresponding JPA entities/repositories/controllers
-- were deleted from marketplace-service. Because Hibernate runs with
-- ddl-auto=validate, this migration MUST be applied only AFTER the updated
-- marketplace-service (without those entities) is deployed — otherwise validate
-- would fail on the missing tables at startup. There are no entities left that
-- map these tables, so dropping them is safe once the new jar is live.
--
-- Drop order is child/referencing tables first, then parents. CASCADE + IF EXISTS
-- make the script idempotent and robust to FK constraints
-- (marketplace_order_items.product_id -> marketplace_products is ON DELETE
-- RESTRICT, which would otherwise refuse a plain DROP).

BEGIN;

-- 1. buy-order line items (FK -> marketplace_orders, marketplace_products)
DROP TABLE IF EXISTS marketplace_order_items CASCADE;

-- 2. shopping cart items (FK -> marketplace_products ON DELETE CASCADE)
DROP TABLE IF EXISTS customer_cart_items CASCADE;

-- 3. buy-order headers
DROP TABLE IF EXISTS marketplace_orders CASCADE;

-- 4. product catalog (referenced by the two tables dropped above)
DROP TABLE IF EXISTS marketplace_products CASCADE;

-- 5. Sell/buy listings board (independent — no FK to products)
DROP TABLE IF EXISTS marketplace_listings CASCADE;

COMMIT;
