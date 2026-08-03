-- =============================================================================
-- 27_master_category_scoped_columns.sql
--
-- Adds the device_category_id FK column to three master tables whose JPA
-- entities reference it but whose original CREATE TABLE in 02_customer_app.sql
-- never declared it:
--   * master_screening_questions  (MasterScreeningQuestion.java)
--   * master_condition_groups     (MasterConditionGroup.java)
--   * master_functional_issues    (MasterFunctionalIssue.java)
--
-- Without these columns master-data-service fails Hibernate's validate-only
-- schema check at startup (SchemaValidationException), which takes the entire
-- service down. Every customer Sell-flow endpoint therefore returns HTTP 500
-- through the gateway, including:
--   GET /master/screening-questions
--   GET /master/condition-groups   (and /master/condition-options)
--   GET /master/functional-issues
--   GET /master/repair-categories  (collateral — service won't start)
--   …and every other /master/* endpoint
--
-- The columns only ever existed in the legacy H2 dev DB where Hibernate's
-- ddl-auto created them on the fly. NULL means "applies to every category".
-- =============================================================================

BEGIN;

ALTER TABLE master_screening_questions
    ADD COLUMN IF NOT EXISTS device_category_id UUID
    REFERENCES master_device_categories(id) ON DELETE CASCADE;

ALTER TABLE master_condition_groups
    ADD COLUMN IF NOT EXISTS device_category_id UUID
    REFERENCES master_device_categories(id) ON DELETE CASCADE;

ALTER TABLE master_functional_issues
    ADD COLUMN IF NOT EXISTS device_category_id UUID
    REFERENCES master_device_categories(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_master_screening_questions_device_category
    ON master_screening_questions (device_category_id);

CREATE INDEX IF NOT EXISTS idx_master_condition_groups_device_category
    ON master_condition_groups (device_category_id);

CREATE INDEX IF NOT EXISTS idx_master_functional_issues_device_category
    ON master_functional_issues (device_category_id);

COMMIT;
