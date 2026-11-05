-- IMEI captured by the pickup-person estimate wizard. Previously stuffed into
-- repair_bookings.issue_summary as part of a JSON appendix; now persisted in
-- its own column so the booking row carries structured data.
ALTER TABLE repair_bookings
    ADD COLUMN IF NOT EXISTS imei VARCHAR(40);
