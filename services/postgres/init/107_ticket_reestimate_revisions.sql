-- =============================================================================
-- 107_ticket_reestimate_revisions.sql
--
-- Technician re-estimate workflow (ticket-service ReEstimateWorkflowService).
-- One row per re-estimate: a technician's request (REQUESTED), the partner's
-- revised estimate (AWAITING_APPROVAL), and the customer's decision (APPROVED /
-- REJECTED) — or DISMISSED when the shop declines a request without
-- re-estimating. Rows are never deleted by the app; they are the full history
-- behind GET /tickets/{id}/re-estimates.
--
-- original_* columns snapshot the ticket BEFORE the partner's revision was
-- applied, so a REJECTED decision restores price_items_json + estimated_price
-- (and the approval / delivery fields in original_state_json) exactly.
--
-- At most ONE open (REQUESTED / AWAITING_APPROVAL) row per ticket — enforced by
-- the partial unique index, which is what turns a double-tap into a 409.
--
-- Idempotent (IF NOT EXISTS throughout). Touches no existing rows.
-- Apply BEFORE deploying ticket-service (ddl-auto: validate).
-- 104 / 105 / 106 are reserved by other branches.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS ticket_reestimate_revisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id                   UUID NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    shop_id                     UUID NOT NULL,
    revision_no                 INTEGER NOT NULL,
    source                      VARCHAR(20) NOT NULL
                                CHECK (source IN ('TECHNICIAN','PARTNER')),
    status                      VARCHAR(30) NOT NULL
                                CHECK (status IN ('REQUESTED','AWAITING_APPROVAL','APPROVED','REJECTED','DISMISSED')),

    -- Technician request
    requested_by_user_id        UUID,
    technician_id               UUID,
    technician_name             VARCHAR(255),
    requested_at                TIMESTAMPTZ,
    additional_issue            TEXT,
    reason                      TEXT,
    proposed_amount             NUMERIC(12,2),
    technician_notes            TEXT,
    request_images_json         TEXT,

    -- Partner revision (POST /tickets/{id}/re-estimate)
    submitted_by_user_id        UUID,
    submitted_by_name           VARCHAR(255),
    submitted_at                TIMESTAMPTZ,
    items_json                  TEXT,
    additional_amount           NUMERIC(12,2),
    original_estimated_price    NUMERIC(12,2),
    original_price_items_json   TEXT,
    original_state_json         TEXT,
    revised_total               NUMERIC(12,2),
    revision_reason             TEXT,
    revision_images_json        TEXT,

    -- Decision
    decision                    VARCHAR(20),
    decided_by_user_id          UUID,
    decided_by_name             VARCHAR(255),
    decided_at                  TIMESTAMPTZ,
    decision_reason             TEXT,
    decided_via                 VARCHAR(20)
                                CHECK (decided_via IS NULL OR decided_via IN ('SHOP','CUSTOMER_APP')),
    technician_acknowledged_at  TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_ticket_reestimate_revision_no UNIQUE (ticket_id, revision_no)
);

-- At most one open re-estimate per ticket.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ticket_reestimate_open
    ON ticket_reestimate_revisions (ticket_id)
    WHERE status IN ('REQUESTED','AWAITING_APPROVAL');

CREATE INDEX IF NOT EXISTS idx_ticket_reestimate_shop
    ON ticket_reestimate_revisions (shop_id, status);

COMMIT;
