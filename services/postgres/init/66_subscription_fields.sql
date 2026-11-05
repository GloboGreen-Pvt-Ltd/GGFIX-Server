-- 66_subscription_fields.sql
--
-- Extend the existing `subscriptions` table (created in 01_schema.sql) with the
-- fields the new subscription system needs. The base table only carried
-- shop_id / plan_code / status / period timestamps; the record-only subscription
-- feature adds an owner reference, a richer plan/type + trial vs paid window,
-- and the STORED (not yet enforced) plan limits.
--
-- All columns are added NULLABLE / with defaults via ADD COLUMN IF NOT EXISTS so
-- this is safe to re-run and safe against pre-existing rows. This mirrors the
-- H2->Postgres drift pattern used across this codebase: entities gain @Column
-- fields, so the next-numbered init file must add the matching DB columns
-- (Hibernate ddl-auto=validate requires every mapped column to exist).
--
-- The base `status` column is reused as the subscription status
-- (FREE_TRIAL | ACTIVE | EXPIRED | CANCELLED) and `plan_code` stays populated
-- (FREE_TRIAL | BASIC) alongside the new subscription_type column.

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS owner_user_id           UUID;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS subscription_type       VARCHAR(30);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS trial_start_date        TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS trial_end_date          TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS subscription_start_date TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS subscription_end_date   TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS active_date             TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS inactive_date           TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS shop_limit              INT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS employee_limit          INT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS sell_limit              INT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS pickup_service_enabled  BOOLEAN DEFAULT false;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS buy_product_unlimited   BOOLEAN DEFAULT true;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS sell_product_unlimited  BOOLEAN DEFAULT false;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS shop_count              INT DEFAULT 1;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS price_amount            NUMERIC(10,2);

CREATE INDEX IF NOT EXISTS idx_subscriptions_owner_user_id ON subscriptions(owner_user_id);
