-- 92_users_account_management.sql
--
-- Full account-management model on `users`, extending migration 91.
--
-- 91 shipped created_by as the creator's user id (UUID). The account-management
-- spec splits that into three columns with different meanings:
--
--   created_by          -> the creator's ROLE   ('ADMIN' | 'MARKET_PERSON')
--   created_person_id   -> the creator's user id
--   created_person_name -> the creator's name, captured at creation time
--
-- so created_by changes type uuid -> varchar and its old value moves to
-- created_person_id. The retype is wrapped in a DO block that fires only while
-- the column is still uuid, because `USING NULL` would blank a varchar column
-- that had already been converted — this file must stay safe to re-run.
--
-- The active-person trio is separate from the creator trio on purpose: the
-- creator is immutable history, while the active person is reassignable by an
-- admin. A shop owner created by Market Person A but later handed to Market
-- Person B keeps A as creator and shows B as active.
--
-- Names are stored rather than joined at read time because that is what the
-- spec asks for; they are always sourced from the real user row server-side,
-- never from the client. Note the trade-off: a person renaming themselves does
-- not retroactively update rows that captured the old name.

-- Reassign the index before the column underneath it changes type.
DROP INDEX IF EXISTS idx_users_created_by;

ALTER TABLE users ADD COLUMN IF NOT EXISTS created_person_id   UUID;
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_person_name VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS active_role         VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS active_person_id    UUID;
ALTER TABLE users ADD COLUMN IF NOT EXISTS active_person_name  VARCHAR(255);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'users'
           AND column_name = 'created_by'
           AND data_type = 'uuid'
    ) THEN
        -- Preserve the id that 91 put in created_by before retyping the column.
        UPDATE users
           SET created_person_id = created_by
         WHERE created_by IS NOT NULL
           AND created_person_id IS NULL;

        ALTER TABLE users
            ALTER COLUMN created_by TYPE VARCHAR(50) USING NULL::VARCHAR(50);
    END IF;
END $$;

-- Backfill the creator's role and name from whoever created_person_id points at.
-- No-op on a database whose owners all predate 91 (created_person_id IS NULL).
UPDATE users u
   SET created_by          = COALESCE(u.created_by, c.role),
       created_person_name = COALESCE(u.created_person_name, c.name)
  FROM users c
 WHERE u.created_person_id = c.id;

CREATE INDEX IF NOT EXISTS idx_users_created_person_id ON users(created_person_id);
CREATE INDEX IF NOT EXISTS idx_users_active_person_id  ON users(active_person_id);
