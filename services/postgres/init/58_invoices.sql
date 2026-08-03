-- 58_invoices.sql
--
-- Invoice + spare / service line snapshot per ticket. One row per ticket
-- (latest generated invoice). The owner taps "Invoice" on the Billing &
-- Delivery row → fills the Invoice Generator form → server stores everything
-- here → the Delivery Invoice Report screen renders straight from this row.
--
-- Why store the inputs AND the computed totals: the rendered Deliver Invoice
-- PDF/print must be bit-stable across re-opens, but we also want to let the
-- owner edit and re-generate. Snapshotting the user inputs (service charges,
-- discount, tax mode, gst %, spare lines JSON) lets us recompute exactly the
-- same numbers later for the report screen even if rounding rules drift in
-- code, while the precomputed base/gst/final fields keep list views fast.

CREATE TABLE IF NOT EXISTS invoices (
    id                   UUID PRIMARY KEY,
    ticket_id            UUID NOT NULL,
    shop_id              UUID,

    -- Display metadata
    invoice_no           VARCHAR(80) NOT NULL,
    ticket_date          TIMESTAMP,
    delivery_date        TIMESTAMP,
    gst_no               VARCHAR(50),

    -- Inputs from the Invoice Generator form
    service_charges      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_repair_amount  NUMERIC(14, 2) NOT NULL DEFAULT 0,   -- (1) sum of spare rates
    spare_utility_charge NUMERIC(14, 2) NOT NULL DEFAULT 0,   -- (3) sum of spare taxable values
    discount             NUMERIC(14, 2) NOT NULL DEFAULT 0,   -- (4)
    tax_mode             VARCHAR(16) NOT NULL DEFAULT 'WITHOUT',  -- WITHOUT | INCLUSIVE | EXCLUSIVE
    gst_percent          NUMERIC(5, 2) NOT NULL DEFAULT 0,    -- 0 / 3 / 5 / 12 / 18 / 28

    -- Computed totals (client computes + sends; server persists as-is so the
    -- two views never disagree if rounding rules diverge).
    amount_2_plus_3      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    base_amount          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_gst            NUMERIC(14, 2) NOT NULL DEFAULT 0,
    final_payable_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    amount_in_words      TEXT,

    -- Line items captured at generation time so the rendered report
    -- doesn't depend on the ticket priceItems list (which the owner may
    -- still be editing in another screen).
    spare_lines_json     TEXT,    -- [{slNo,description,rate,warranty,qty,taxableValue,cgst,sgst,roundedValue,totalGst,totalAmount}]
    service_lines_json   TEXT,    -- [{slNo,description,rate,taxableValue,cgst,sgst,roundedValue,totalGst,totalAmount}]

    -- Audit
    generated_by         UUID,
    generated_at         TIMESTAMP NOT NULL DEFAULT now(),
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_invoices_ticket_id ON invoices(ticket_id);
CREATE INDEX IF NOT EXISTS idx_invoices_shop_id ON invoices(shop_id);
CREATE INDEX IF NOT EXISTS idx_invoices_invoice_no ON invoices(invoice_no);

-- One latest invoice per ticket (re-generating updates the same row).
CREATE UNIQUE INDEX IF NOT EXISTS uq_invoices_ticket_id ON invoices(ticket_id);
