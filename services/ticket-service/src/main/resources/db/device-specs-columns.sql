-- Boot-time safety net for postgres/init/98_device_category_specs.sql: the
-- category device-spec columns this service's entity maps. Run by
-- spring.sql.init BEFORE Hibernate validates the schema (ddl-auto=validate),
-- so a deploy that lands before migration 98 was applied by hand still starts
-- instead of failing validation. Additive and idempotent — safe on every boot.
-- The CHECK constraints, index and column comments stay in migration 98.
-- Plain statements only: Spring's script runner splits on ';', so no DO blocks.
ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS device_category  VARCHAR(30),
    ADD COLUMN IF NOT EXISTS ram              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS storage_capacity VARCHAR(20),
    ADD COLUMN IF NOT EXISTS storage_type     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS case_size        VARCHAR(20),
    ADD COLUMN IF NOT EXISTS connectivity     VARCHAR(40),
    ADD COLUMN IF NOT EXISTS device_type      VARCHAR(40);
