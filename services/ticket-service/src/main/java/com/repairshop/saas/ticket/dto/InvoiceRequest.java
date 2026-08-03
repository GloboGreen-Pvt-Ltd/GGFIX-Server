package com.repairshop.saas.ticket.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload from the Invoice Generator screen. Client computes the totals
 * (amount2Plus3, baseAmount, totalGst, finalPayableAmount) using the same
 * rounding the report screen displays and sends them straight through —
 * the server only validates non-null and persists, so the visual numbers
 * the owner saw at "Invoice Generated" are byte-stable for the report.
 */
@Data
public class InvoiceRequest {
    private String invoiceNo;
    private Instant ticketDate;
    private Instant deliveryDate;
    private String gstNo;

    // Inputs
    private BigDecimal serviceCharges;
    private BigDecimal totalRepairAmount;
    private BigDecimal spareUtilityCharge;
    private BigDecimal discount;
    private String taxMode;         // WITHOUT | INCLUSIVE | EXCLUSIVE
    private BigDecimal gstPercent;

    // Computed totals
    private BigDecimal amount2Plus3;
    private BigDecimal baseAmount;
    private BigDecimal totalGst;
    private BigDecimal finalPayableAmount;
    private String amountInWords;

    // Snapshot line arrays as JSON strings (client serializes).
    private String spareLinesJson;
    private String serviceLinesJson;
}
