-- 93_subscription_entitlement_corrections.sql
--
-- Two entitlement corrections, applied when the plan spec was finalised:
--
--   1. Pickup Service IS included in the Free Trial. It was recorded as
--      disabled, which would have withheld a feature the trial advertises.
--   2. Basic's shop ceiling is the number of shops PURCHASED, not unlimited.
--      Basic is priced per shop (₹3,000 for one, ₹2,500 each from two), so an
--      owner who paid for two shops is entitled to two.
--
-- Both are also folded into migration 92, so a database built from scratch
-- lands on the right values in one pass. This file exists for databases where
-- 92 was already applied — running it after 92 is a no-op on a fresh build and
-- a fix on an existing one. Idempotent and safe to re-run.
--
-- These columns are display/history only; SubscriptionPlan.java is what gets
-- enforced. They are kept in step so an admin screen cannot contradict the API.

-- ── 1. Trial includes pickup service ────────────────────────────────────────
UPDATE subscriptions
   SET pickup_service_enabled = true,
       updated_at             = now()
 WHERE UPPER(TRIM(COALESCE(subscription_type, plan_code))) = 'FREE_TRIAL'
   AND pickup_service_enabled IS DISTINCT FROM true;

-- ── 2. Basic shop ceiling = purchased shop count ────────────────────────────
-- NULL when the count is missing or nonsensical (0/negative) — unlimited is the
-- safe direction for a paying customer whose billing detail did not survive.
UPDATE subscriptions
   SET shop_limit = CASE WHEN COALESCE(shop_count, 0) > 0 THEN shop_count END,
       updated_at = now()
 WHERE UPPER(TRIM(COALESCE(subscription_type, plan_code))) = 'BASIC'
   AND shop_limit IS DISTINCT FROM CASE WHEN COALESCE(shop_count, 0) > 0 THEN shop_count END;

-- ── 3. Index the two usage counts enforcement now runs ──────────────────────
-- Shop allowance is counted per owner on every add-shop; the sell allowance is
-- counted per shop on every quotation a shop submits.
CREATE INDEX IF NOT EXISTS idx_shops_owner_active
    ON shops(owner_user_id) WHERE COALESCE(is_active, true) = true;

CREATE INDEX IF NOT EXISTS idx_sell_orders_shop
    ON sell_orders(shop_id);
