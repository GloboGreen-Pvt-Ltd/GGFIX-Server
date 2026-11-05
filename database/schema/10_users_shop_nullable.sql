-- =============================================================================
-- 10_users_shop_nullable.sql
--
-- Makes users.shop_id nullable so platform users (SUPER_ADMIN) and shop OWNERS
-- don't need a placeholder "Platform Admin" parent shop row. The Platform Admin
-- row was a workaround for the original NOT NULL FK; with this change we can
-- delete it without breaking referential integrity.
--
-- Email uniqueness now enforced globally (was UNIQUE(shop_id, email), which is
-- broken once shop_id may be NULL since SQL treats NULL as distinct).
-- =============================================================================

BEGIN;

ALTER TABLE users ALTER COLUMN shop_id DROP NOT NULL;

-- Detach super-admins from the placeholder Platform Admin shop, then drop it.
UPDATE users SET shop_id = NULL
  WHERE shop_id IN (SELECT id FROM shops WHERE slug = 'admin');

DELETE FROM shops WHERE slug = 'admin';

-- Global email uniqueness. The legacy UNIQUE(shop_id, email) constraint is kept
-- (harmless, just redundant for non-null shop_id rows).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_users_email'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
    END IF;
END $$;

COMMIT;
