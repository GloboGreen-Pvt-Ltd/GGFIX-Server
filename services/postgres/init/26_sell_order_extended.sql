-- =============================================================================
-- 26_sell_order_extended.sql
--
-- Brings the sell_order_* child tables in sync with the order-service entities
-- by adding the display columns the JPA mappings reference:
--   * sell_order_conditions.group_name        (SellOrderCondition.java)
--   * sell_order_accessories.label            (SellOrderAccessory.java)
--   * sell_order_screening_answers.question   (SellOrderScreeningAnswer.java)
--
-- Without these, the customer Sell submit flow (POST /sell-orders) and the
-- owner-side detail view (GET /sell-orders/{id}) fail with HTTP 500 because
-- Hibernate's validate-only schema rejects the entity at startup. The
-- existing master_* FK columns are kept; these are denormalized labels
-- captured at submission time so historical orders display correctly even
-- if the master record is later renamed or removed.
-- =============================================================================

BEGIN;

ALTER TABLE sell_order_conditions
    ADD COLUMN IF NOT EXISTS group_name VARCHAR(200);

ALTER TABLE sell_order_accessories
    ADD COLUMN IF NOT EXISTS label VARCHAR(200);

ALTER TABLE sell_order_screening_answers
    ADD COLUMN IF NOT EXISTS question TEXT;

COMMIT;
