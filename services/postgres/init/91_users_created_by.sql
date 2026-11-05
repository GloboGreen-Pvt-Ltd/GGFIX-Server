-- 91_users_created_by.sql
--
-- Account provenance for staff-created accounts. `users` already carries role,
-- is_active and created_at (01_schema.sql); the only missing piece for the Shop
-- Owner account-management screen is WHO created the row.
--
-- Nullable on purpose: every pre-existing owner was created before this column
-- existed and has no creator to backfill. The admin list renders those as "—".
-- No FK to users(id) — a deleted admin must not cascade-delete or block the
-- shop owners they created; the id is kept purely as an audit pointer and is
-- resolved to a name at read time (AuthService.toOwnerView).
--
-- ADD COLUMN IF NOT EXISTS so this is safe to re-run, matching the H2->Postgres
-- drift pattern used across this codebase: entities gain @Column fields, so the
-- next-numbered init file must add the matching DB column (ddl-auto=validate
-- requires every mapped column to exist).

ALTER TABLE users ADD COLUMN IF NOT EXISTS created_by UUID;

CREATE INDEX IF NOT EXISTS idx_users_created_by ON users(created_by);
