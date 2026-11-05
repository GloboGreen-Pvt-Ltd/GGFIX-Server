-- =============================================================================
-- 100_staff_tasks.sql
--
-- Backs Partner App -> Employees -> Staff Tasks: shop work the owner hands to
-- employees (deliveries, parcels, supplier runs, bank work...). Read by the
-- Employee App for "my tasks".
--
-- WHY NOT REPAIR TICKETS
-- A staff task is shop work, not a repair job: it has no customer, device or
-- price, and must never appear in booking lists, statuses or reports.
--
-- WHY A SEPARATE ASSIGNEE TABLE
-- A task can go to several employees, and the Employee App asks "which tasks
-- are mine?" — that needs an indexed (technician_id) lookup, not a scan of a
-- JSON column. Name / role / phone are snapshotted so the task still reads
-- correctly after an employee is renamed or removed.
--
-- WHY checklist_json / history_json ARE TEXT
-- Both are small, always read and written with their task, and never queried
-- on their own — the same choice as tickets.price_items_json.
--
-- Status OVERDUE is derived by the apps (end time passed, not completed) and
-- is never stored.
--
-- Idempotent — IF NOT EXISTS throughout, so re-running is a no-op.
-- =============================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS staff_tasks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id             UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    task_type           VARCHAR(20)  NOT NULL DEFAULT 'OTHER'
                        CHECK (task_type IN ('SERVICE','DELIVERY','PARCEL','SUPPLIER','BANK_WORK','SHOP_WORK','OTHER')),
    title               VARCHAR(160) NOT NULL,
    description         TEXT,
    task_date           DATE         NOT NULL,
    start_time          TIME         NOT NULL,
    end_time            TIME         NOT NULL,
    priority            VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM'
                        CHECK (priority IN ('LOW','MEDIUM','HIGH')),
    reminder_minutes    INTEGER      NOT NULL DEFAULT 0,
    repeat_rule         VARCHAR(10)  NOT NULL DEFAULT 'NEVER'
                        CHECK (repeat_rule IN ('NEVER','DAILY','WEEKLY','MONTHLY')),
    location            VARCHAR(500),
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    checklist_json      TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','INCOMPLETE','CANCELLED')),
    incomplete_reason   VARCHAR(500),
    history_json        TEXT,
    -- Set by the app per create; a retried / double-tapped create returns the
    -- first task instead of making a copy.
    client_ref          VARCHAR(64),
    created_by_user_id  UUID,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_staff_tasks_time CHECK (end_time > start_time)
);

CREATE INDEX IF NOT EXISTS idx_staff_tasks_shop_date
    ON staff_tasks (shop_id, task_date, start_time);

CREATE UNIQUE INDEX IF NOT EXISTS uq_staff_tasks_shop_client_ref
    ON staff_tasks (shop_id, client_ref) WHERE client_ref IS NOT NULL;

CREATE TABLE IF NOT EXISTS staff_task_assignees (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id             UUID NOT NULL REFERENCES staff_tasks(id) ON DELETE CASCADE,
    technician_id       UUID NOT NULL,
    name                VARCHAR(120),
    role_label          VARCHAR(60),
    phone               VARCHAR(20),
    photo_url           VARCHAR(500),
    CONSTRAINT uq_staff_task_assignee UNIQUE (task_id, technician_id)
);

CREATE INDEX IF NOT EXISTS idx_staff_task_assignees_technician
    ON staff_task_assignees (technician_id);

COMMIT;
