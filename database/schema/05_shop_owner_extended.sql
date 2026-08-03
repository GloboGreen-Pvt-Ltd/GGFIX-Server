-- =============================================================================
-- 05_shop_owner_extended.sql
--
-- Extends users + shops so the admin can create a shop OWNER plus one or more
-- shop locations in a single transaction.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS.
-- Apply with:
--   psql -h <host> -U <user> -d <db> -f 05_shop_owner_extended.sql
-- =============================================================================

BEGIN;

-- ---- users: owner personal profile ------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone            VARCHAR(50),
    ADD COLUMN IF NOT EXISTS avatar_url       VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS id_proof_url     VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS personal_address TEXT;

-- ---- shops: full location profile + owner link ------------------------------
ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS owner_user_id          UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS mobile                 VARCHAR(50),
    ADD COLUMN IF NOT EXISTS district               VARCHAR(120),
    ADD COLUMN IF NOT EXISTS city                   VARCHAR(120),
    ADD COLUMN IF NOT EXISTS state                  VARCHAR(120),
    ADD COLUMN IF NOT EXISTS pincode                VARCHAR(20),
    ADD COLUMN IF NOT EXISTS latitude               NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude              NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS front_image_url        VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS gst_certificate_url    VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS udyam_certificate_url  VARCHAR(1000);

CREATE INDEX IF NOT EXISTS idx_shops_owner_user_id ON shops(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_shops_pincode       ON shops(pincode);

COMMIT;
