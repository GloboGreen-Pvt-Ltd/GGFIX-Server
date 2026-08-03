-- One-shot backfill of "In Service Process" step events into
-- repair_booking_events for tickets whose state already implies the step
-- should be visible. Safe to re-run: every insert is gated by NOT EXISTS so
-- nothing gets duplicated.

-- TECH_WORK_STARTED: ticket has reached IN_REPAIR or beyond.
INSERT INTO repair_booking_events (booking_id, status, note, actor, created_at)
SELECT rb.id, 'TECH_WORK_STARTED', 'Technician Work Started', 'TECHNICIAN', NOW()
FROM repair_bookings rb
JOIN tickets t ON rb.ticket_id = t.id
WHERE UPPER(t.status) IN ('IN_REPAIR', 'APPROVED', 'READY', 'DELIVERED')
  AND NOT EXISTS (
    SELECT 1 FROM repair_booking_events e
    WHERE e.booking_id = rb.id AND UPPER(e.status) = 'TECH_WORK_STARTED'
  );

-- TECH_UPLOADED_IMAGES: ticket has at least one technician-uploaded URL.
INSERT INTO repair_booking_events (booking_id, status, note, actor, created_at)
SELECT rb.id, 'TECH_UPLOADED_IMAGES', 'Technician uploaded device images', 'TECHNICIAN', NOW()
FROM repair_bookings rb
JOIN tickets t ON rb.ticket_id = t.id
WHERE t.technician_photos_json IS NOT NULL
  AND t.technician_photos_json LIKE '%http%'
  AND NOT EXISTS (
    SELECT 1 FROM repair_booking_events e
    WHERE e.booking_id = rb.id AND UPPER(e.status) = 'TECH_UPLOADED_IMAGES'
  );

-- TECH_COMPLIANCE: ticket has at least one non-internal repair note.
INSERT INTO repair_booking_events (booking_id, status, note, actor, created_at)
SELECT rb.id, 'TECH_COMPLIANCE',
       'Technician compliance issue has been verified and updated.',
       'TECHNICIAN', NOW()
FROM repair_bookings rb
JOIN tickets t ON rb.ticket_id = t.id
WHERE EXISTS (
    SELECT 1 FROM repair_notes rn
    WHERE rn.ticket_id = t.id
      AND (rn.is_internal IS NULL OR rn.is_internal = false)
  )
  AND NOT EXISTS (
    SELECT 1 FROM repair_booking_events e
    WHERE e.booking_id = rb.id AND UPPER(e.status) = 'TECH_COMPLIANCE'
  );

-- ISSUE_IDENTIFIED: technician created a NEW (not REFERENCE) solution pack.
INSERT INTO repair_booking_events (booking_id, status, note, actor, created_at)
SELECT rb.id, 'ISSUE_IDENTIFIED', 'Issue identified by technician',
       'TECHNICIAN', NOW()
FROM repair_bookings rb
JOIN tickets t ON rb.ticket_id = t.id
WHERE EXISTS (
    SELECT 1 FROM ticket_solution_packs sp
    WHERE sp.ticket_id = t.id AND UPPER(sp.pack_type) = 'NEW'
  )
  AND NOT EXISTS (
    SELECT 1 FROM repair_booking_events e
    WHERE e.booking_id = rb.id AND UPPER(e.status) = 'ISSUE_IDENTIFIED'
  );

-- WAITING_APPROVAL: ticket has passed through a state where the customer
-- was being asked to approve. Once status is QUOTED or beyond, the
-- "waiting" step has happened — even if the customer has since approved.
INSERT INTO repair_booking_events (booking_id, status, note, actor, created_at)
SELECT rb.id, 'WAITING_APPROVAL', 'Waiting for Customer Approval',
       'TECHNICIAN', NOW()
FROM repair_bookings rb
JOIN tickets t ON rb.ticket_id = t.id
WHERE UPPER(t.status) IN ('QUOTED', 'APPROVED', 'IN_REPAIR', 'READY', 'DELIVERED')
  AND NOT EXISTS (
    SELECT 1 FROM repair_booking_events e
    WHERE e.booking_id = rb.id AND UPPER(e.status) = 'WAITING_APPROVAL'
  );
