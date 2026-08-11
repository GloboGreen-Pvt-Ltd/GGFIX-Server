-- Delete ONE booking/ticket outright, and collect the S3 objects it owns so
-- they can be removed too.
--
-- IRREVERSIBLE, MANUAL, REVIEW-FIRST. Nothing here runs on its own. Work the
-- steps in order; each one prints what the next one would destroy.
--
-- ORDER MATTERS: run step 2 (collect the media URLs) BEFORE step 3 (delete the
-- rows). The URLs live in the rows you are about to delete — once they are gone
-- the S3 objects are unreachable orphans that nothing points at, and you would
-- be reduced to guessing keys from a bucket listing.

\set ref '''CSPEN7657020'''


-- =============================================================================
-- 1. Locate it, and prove you are on the right database
-- =============================================================================
-- The "#CSPEN…" chip in the app header is not reliably the booking number, so
-- both identifiers are matched. If total_bookings is tiny, STOP — the running
-- services are not using this database and you are about to delete from the
-- wrong one.

SELECT current_database(), count(*) AS total_bookings FROM repair_bookings;

SELECT t.id AS ticket_id, t.tracking_id, t.status, t.created_at,
       b.id AS booking_id, b.booking_number
  FROM tickets t
  FULL JOIN repair_bookings b ON b.ticket_id = t.id
 WHERE t.tracking_id = :ref OR b.booking_number = :ref;


-- =============================================================================
-- 2. The photo objects to delete — SAVE THIS OUTPUT
-- =============================================================================
-- Scoped to three sets only, tagged by `source` so each is reviewable apart:
--     device_photos       the booking's own device shoot
--     technician_uploads  "Your Side Device Image"
--     issue_verified      the Issue Verified & Updated photos
--
-- AUDIO IS DELIBERATELY EXCLUDED. The complaint voice note
-- (tickets.issue_audio_url) and any compliance-note recording
-- (repair_notes.audio_url, repair_booking_events.audio_url) are NOT listed and
-- will survive in S3. If step 3 deletes the rows, those clips become orphans
-- that no query can find again — add the three audio columns back to the CTE
-- above before deleting the rows if they should go too.
--
-- Pulls each https URL out of the columns above, whether one holds a bare URL
-- or a JSON blob. Run it to a file:
--
--   psql ... -X -A -F'|' -t -f delete_booking_and_media.sql > booking-media.txt
--
-- Each line is `source|url`; the key for step 4 is everything after the bucket
-- host. Nothing else in the bucket belongs to this booking.

WITH tgt AS (
    SELECT t.id AS ticket_id, b.id AS booking_id
      FROM tickets t
      FULL JOIN repair_bookings b ON b.ticket_id = t.id
     WHERE t.tracking_id = :ref OR b.booking_number = :ref
), urls AS (
    -- 1. Device Photos. The four repair_bookings columns are the mirror of the
    --    same shoot (CustomerOrderMirrorService splits device_photos_json into
    --    front/back/side/other), so they repeat URLs the ticket already holds —
    --    DISTINCT collapses them. Kept anyway: if the mirror ran and the ticket
    --    JSON was later rewritten, the mirror is the only row still naming the
    --    original object.
    SELECT 'device_photos' AS source, t.device_photos_json AS raw
      FROM tickets t JOIN tgt ON tgt.ticket_id = t.id
    UNION ALL
    SELECT 'device_photos', b.front_image_url
      FROM repair_bookings b JOIN tgt ON tgt.booking_id = b.id
    UNION ALL
    SELECT 'device_photos', b.back_image_url
      FROM repair_bookings b JOIN tgt ON tgt.booking_id = b.id
    UNION ALL
    SELECT 'device_photos', b.side_image_url
      FROM repair_bookings b JOIN tgt ON tgt.booking_id = b.id
    UNION ALL
    SELECT 'device_photos', b.other_image_url
      FROM repair_bookings b JOIN tgt ON tgt.booking_id = b.id
    UNION ALL
    -- 2. Technician uploads ("Your Side Device Image" on the ticket screen).
    SELECT 'technician_uploads', t.technician_photos_json
      FROM tickets t JOIN tgt ON tgt.ticket_id = t.id
    UNION ALL
    -- 3. Issue Verified & Updated photos. Stored on the compliance note and
    --    copied onto its timeline event, so both are read; DISTINCT dedupes.
    SELECT 'issue_verified', n.images_json
      FROM repair_notes n JOIN tgt ON tgt.ticket_id = n.ticket_id
    UNION ALL
    SELECT 'issue_verified', e.images_json
      FROM repair_booking_events e JOIN tgt ON tgt.booking_id = e.booking_id
)
SELECT DISTINCT u.source, m.parts[1] AS url
  FROM urls u,
       LATERAL regexp_matches(u.raw, 'https?://[^"'',[:space:]\]]+', 'g') AS m(parts)
 WHERE u.raw IS NOT NULL
 ORDER BY 1, 2;


-- =============================================================================
-- 3. Delete the rows
-- =============================================================================
-- Only after step 2's output is saved. repair_booking_events cascades from
-- repair_bookings, and repair_notes / ticket_status_history cascade from
-- tickets, so the two deletes below are enough — but they are wrapped in one
-- transaction so a foreign key you did not expect rolls the whole thing back
-- instead of leaving the record half-gone.

-- BEGIN;
-- DELETE FROM repair_bookings
--  WHERE booking_number = :ref
--     OR ticket_id IN (SELECT id FROM tickets WHERE tracking_id = :ref);
-- DELETE FROM tickets
--  WHERE tracking_id = :ref;
-- COMMIT;


-- =============================================================================
-- 4. Then the S3 objects
-- =============================================================================
-- ⚠ DELETE ONLY THE EXACT KEYS FROM STEP 2. Never delete by prefix.
-- ggfix-media-1762 also holds master_models catalog images — the device photos
-- every shop and customer sees in the pickers. A prefix sweep over Devicefiles/
-- takes those with it, and they are not restorable from the app.
--
-- ⚠ CHECK THE ACCOUNT FIRST. The AWS credentials on this machine have
-- previously resolved to the WRONG account (see the web-mirror deploy notes) —
-- an S3 delete that "succeeds" against the wrong account is silent.
--
--   aws sts get-caller-identity            # confirm the account before anything
--
-- Then, per URL saved in step 2 (key = everything after the bucket host):
--
--   aws s3api delete-object --bucket ggfix-media-1762 --key "Devicefiles/<file>"
--
-- Verify one object is really gone before looping the rest:
--
--   aws s3api head-object --bucket ggfix-media-1762 --key "Devicefiles/<file>"
--   # expect: An error occurred (404) when calling the HeadObject operation
