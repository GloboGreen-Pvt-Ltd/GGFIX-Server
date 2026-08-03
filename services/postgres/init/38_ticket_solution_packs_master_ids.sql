-- 38_ticket_solution_packs_master_ids.sql
--
-- Backs the "New Issue Solution Pack Upload" screen now that the issue main
-- category and sub-category come from the admin's master tables instead of
-- hard-coded client lists:
--   * issue_category_id    → master_repair_categories.id (e.g. Display & Touch)
--   * issue_subcategory_id → master_repair_services.id   (e.g. Screen Broken)
--
-- The existing issue_category / issue_subcategory VARCHAR columns (migration 37)
-- continue to hold the denormalized name for display so the row stays readable
-- even if the master row is later renamed or deleted. Filters can use either
-- side: id when present (precise), name as fallback.
--
-- Columns are nullable + un-FKed because the master catalog lives in a sibling
-- service and we don't want a cross-service FK constraint here.

ALTER TABLE ticket_solution_packs
    ADD COLUMN IF NOT EXISTS issue_category_id    UUID,
    ADD COLUMN IF NOT EXISTS issue_subcategory_id UUID;

CREATE INDEX IF NOT EXISTS idx_ticket_solution_packs_issue_category_id
    ON ticket_solution_packs(shop_id, issue_category_id);
CREATE INDEX IF NOT EXISTS idx_ticket_solution_packs_issue_subcategory_id
    ON ticket_solution_packs(shop_id, issue_subcategory_id);
