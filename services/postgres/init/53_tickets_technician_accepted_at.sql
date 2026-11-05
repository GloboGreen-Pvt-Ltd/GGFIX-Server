-- =============================================================================
-- 53_tickets_technician_accepted_at.sql
--
-- Adds the column needed to distinguish "assigned to a technician but the
-- technician hasn't tapped Accept yet" from "technician explicitly accepted
-- the job and started work."
--
-- Before this column existed, CustomerOrderMirrorService inferred acceptance
-- from the ticket lifecycle status — if status was in IN_DIAGNOSIS / IN_REPAIR
-- / QUOTED / APPROVED / READY / DELIVERED, the customer/owner timeline auto-
-- emitted TECHNICIAN_ACCEPTED_SERVICE + TECHNICIAN_WORK_STARTED at the same
-- moment ASSIGNED_TO_TECHNICIAN was written. That fired four events within a
-- handful of milliseconds even though the technician had never opened the
-- task — and the employee app's Task Assign screen, which bucketed on the
-- ticket status, never showed the Accept button because the ticket was
-- already "past" CREATED.
--
-- With technician_accepted_at the source of truth becomes the technician's
-- explicit POST /tickets/{id}/accept. The owner's assign-technician PATCH
-- nulls this column out (assign + reassign both); the accept endpoint sets
-- it to now(). The mirror service emits TECHNICIAN_ACCEPTED_SERVICE only
-- when this column is non-null.
-- =============================================================================

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS technician_accepted_at TIMESTAMPTZ;
