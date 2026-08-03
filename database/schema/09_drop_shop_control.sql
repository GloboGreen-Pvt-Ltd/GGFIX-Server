-- =============================================================================
-- 09_drop_shop_control.sql
--
-- Removes users.shop_control. The product no longer differentiates owner
-- workspaces by control type; role alone is sufficient.
-- Idempotent.
-- =============================================================================

BEGIN;

ALTER TABLE users DROP COLUMN IF EXISTS shop_control;

COMMIT;
