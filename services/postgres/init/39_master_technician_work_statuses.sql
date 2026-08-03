-- 39_master_technician_work_statuses.sql
--
-- Backs the "Technician Work Status" dropdown on the employee Ticket Detail
-- screen. Each row is one option the technician can pick; selecting it
-- PATCHes the ticket's status to `ticket_status` which then flows into the
-- existing customer / owner history rail via the mirror service.
--
-- The admin manages this list via the Admin panel → Master Data → Technician
-- Work Statuses page so the shop can rename labels or add intermediate
-- statuses without a code release.

CREATE TABLE IF NOT EXISTS master_technician_work_statuses (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(50) NOT NULL UNIQUE,
    label         VARCHAR(100) NOT NULL,
    ticket_status VARCHAR(50) NOT NULL,
    sort_order    INTEGER NOT NULL DEFAULT 0,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed rows mirror the previously hard-coded STATUS_OPTIONS in the
-- technician detail screen so the dropdown stays familiar after the cutover.
-- ON CONFLICT (code) DO NOTHING keeps re-runs safe.
INSERT INTO master_technician_work_statuses (code, label, ticket_status, sort_order)
VALUES
    ('START',       'Start',       'IN_REPAIR', 10),
    ('IN_PROGRESS', 'In Progress', 'IN_REPAIR', 20),
    ('DONE',        'Done',        'READY',     30)
ON CONFLICT (code) DO NOTHING;
