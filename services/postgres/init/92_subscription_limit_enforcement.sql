-- 92_subscription_limit_enforcement.sql
--
-- Prepares the subscription data for ENFORCEMENT. Until now the plan limits
-- were recorded but never checked, so rows were allowed to carry whatever the
-- creating code or a backfill script happened to write. Now that
-- POST /technicians, PATCH /technicians/{id} and the auth-service login
-- provisioning all refuse past the allowance, the rows have to be coherent.
--
-- Everything here is idempotent and safe to re-run.
--
-- NOTE ON EXISTING OVER-LIMIT SHOPS. This migration deliberately does NOT
-- deactivate anyone. A trial shop that already has 4 active employees (the case
-- that prompted this work) keeps all 4 — the engine compares usage >= limit, so
-- that shop can add no more and can re-activate no one, but nobody loses access
-- overnight because a limit started being enforced. The owner resolves it by
-- deactivating someone or upgrading. List the affected shops with:
--
--   SELECT s.id, s.name, COUNT(*) FILTER (WHERE t.is_available) AS active
--     FROM shops s JOIN technicians t ON t.shop_id = s.id
--    GROUP BY s.id, s.name
--   HAVING COUNT(*) FILTER (WHERE t.is_available) > 3;

-- ── 0. Let a subscription exist without a shop ──────────────────────────────
-- subscriptions.shop_id was created NOT NULL UNIQUE back when a subscription
-- belonged to a shop. It is now keyed by owner_user_id (migration 66) and a
-- multi-shop owner has one subscription covering all their shops, so the column
-- is only a loose pointer at whichever shop it was opened from.
--
-- This has to be relaxed before enforcement, not after: SubscriptionService
-- .activateBasic() builds a new row with shopId left null, so an owner
-- upgrading to BASIC without an existing row hits the NOT NULL and gets a 500 —
-- i.e. exactly the person trying to pay to lift their limit is the one who
-- cannot. UNIQUE stays; Postgres permits many NULLs under a unique index.
ALTER TABLE subscriptions ALTER COLUMN shop_id DROP NOT NULL;

-- ── 1. Backfill FREE_TRIAL for owners with no subscription row ──────────────
-- Owners created before the subscription feature shipped have no row at all.
-- The limit engine fails OPEN for them (deliberately — a lookup miss must never
-- lock a working shop out), so until this runs those accounts are covered by no
-- plan and no limit.
--
-- 15 days from now, not back-dated from registration: back-dating would land
-- every legacy owner in EXPIRED the instant enforcement went live, which is the
-- outage this migration exists to avoid.
INSERT INTO subscriptions (
    id, owner_user_id, shop_id, plan_code, status, subscription_type,
    trial_start_date, trial_end_date, active_date, inactive_date,
    shop_limit, employee_limit, sell_limit,
    pickup_service_enabled, buy_product_unlimited, sell_product_unlimited,
    shop_count, price_amount, started_at, current_period_end, created_at, updated_at
)
SELECT
    gen_random_uuid(), u.id, s.id, 'FREE_TRIAL', 'FREE_TRIAL', 'FREE_TRIAL',
    now(), now() + INTERVAL '15 days', now(), now() + INTERVAL '15 days',
    2, 3, 5,
    false, true, false,
    1, 0, now(), now() + INTERVAL '15 days', now(), now()
FROM users u
-- One row per owner, pointing at their oldest shop. LATERAL + LIMIT 1 rather
-- than a plain join so a multi-shop owner produces one row, not one per shop —
-- shop_id is UNIQUE and the second row would abort the whole migration.
JOIN LATERAL (
    SELECT sh.id
      FROM shops sh
     WHERE sh.owner_user_id = u.id
       AND NOT EXISTS (SELECT 1 FROM subscriptions sx WHERE sx.shop_id = sh.id)
     ORDER BY sh.created_at
     LIMIT 1
) s ON true
WHERE u.role = 'SHOP_OWNER'
  AND NOT EXISTS (SELECT 1 FROM subscriptions x WHERE x.owner_user_id = u.id);

-- ── 2. Re-sync stored limits with the plan catalogue ────────────────────────
-- These columns are display/history only — SubscriptionPlan.java is what gets
-- enforced — but an admin screen showing "employee limit 5" next to an API that
-- stops at 3 is its own bug report. Keep this block in step with the enum.
-- FREE_TRIAL = 2 shops / 3 employees per shop / 5 sell orders.
-- Pickup service IS included in the trial.
UPDATE subscriptions
   SET shop_limit             = 2,
       employee_limit         = 3,
       sell_limit             = 5,
       pickup_service_enabled = true,
       buy_product_unlimited  = true,
       sell_product_unlimited = false,
       updated_at             = now()
 WHERE UPPER(TRIM(COALESCE(subscription_type, plan_code))) = 'FREE_TRIAL'
   AND (shop_limit IS DISTINCT FROM 2
     OR employee_limit IS DISTINCT FROM 3
     OR sell_limit IS DISTINCT FROM 5
     OR pickup_service_enabled IS DISTINCT FROM true);

-- BASIC = unlimited employees and sell orders. NULL, not a large number: the
-- engine skips a null limit without comparing it, so unlimited cannot be capped
-- by accident, and no sentinel can surface in the UI as a literal "999999".
--
-- shop_limit is the exception. Basic is sold per shop (₹3,000 for one, ₹2,500
-- each from two), so the ceiling is what the owner actually bought — shop_count
-- — not infinity. A row with no recorded count falls back to NULL/unlimited
-- rather than to 1: the count is a billing detail, and a missing billing detail
-- must not retroactively lock a paying owner out of shops already running.
UPDATE subscriptions
   SET shop_limit             = CASE WHEN COALESCE(shop_count, 0) > 0 THEN shop_count END,
       employee_limit         = NULL,
       sell_limit             = NULL,
       pickup_service_enabled = true,
       buy_product_unlimited  = true,
       sell_product_unlimited = true,
       updated_at             = now()
 WHERE UPPER(TRIM(COALESCE(subscription_type, plan_code))) = 'BASIC'
   AND (shop_limit IS DISTINCT FROM CASE WHEN COALESCE(shop_count, 0) > 0 THEN shop_count END
     OR employee_limit IS NOT NULL
     OR sell_limit IS NOT NULL);

-- Normalise the plan spelling so the two columns agree. The engine reads
-- subscription_type first, falls back to plan_code and tolerates case drift,
-- but leaving them disagreeing invites a future reader to trust the wrong one.
UPDATE subscriptions
   SET subscription_type = UPPER(TRIM(COALESCE(subscription_type, plan_code))),
       plan_code         = UPPER(TRIM(COALESCE(subscription_type, plan_code))),
       updated_at        = now()
 WHERE COALESCE(subscription_type, plan_code) IS NOT NULL
   AND (subscription_type IS DISTINCT FROM plan_code
     OR subscription_type IS DISTINCT FROM UPPER(TRIM(subscription_type)));

-- ── 3. Index the count enforcement runs ─────────────────────────────────────
-- Every add-employee call runs
--   SELECT COUNT(*) FROM technicians WHERE shop_id = ? AND is_available = true
-- and the Employees screen runs it again on every focus.
CREATE INDEX IF NOT EXISTS idx_technicians_shop_active
    ON technicians(shop_id) WHERE is_available = true;
