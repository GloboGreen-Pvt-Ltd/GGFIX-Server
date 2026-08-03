package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of the Invoice Generator form for a ticket. One row per ticket
 * (unique index on ticket_id) — re-generating updates the same row so the
 * owner can correct mistakes without leaving stale invoice numbers behind.
 * See migration 58_invoices.sql for column comments.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "shop_id")
    private UUID shopId;

    @Column(name = "invoice_no", nullable = false, length = 80)
    private String invoiceNo;

    @Column(name = "ticket_date")
    private Instant ticketDate;

    @Column(name = "delivery_date")
    private Instant deliveryDate;

    @Column(name = "gst_no", length = 50)
    private String gstNo;

    // ── Inputs from the Invoice Generator form ──
    @Column(name = "service_charges", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal serviceCharges = BigDecimal.ZERO;

    @Column(name = "total_repair_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalRepairAmount = BigDecimal.ZERO;

    @Column(name = "spare_utility_charge", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal spareUtilityCharge = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    /** WITHOUT | INCLUSIVE | EXCLUSIVE */
    @Column(name = "tax_mode", nullable = false, length = 16)
    @Builder.Default
    private String taxMode = "WITHOUT";

    @Column(name = "gst_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstPercent = BigDecimal.ZERO;

    // ── Computed totals (client-sent, server-persisted) ──
    @Column(name = "amount_2_plus_3", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal amount2Plus3 = BigDecimal.ZERO;

    @Column(name = "base_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal baseAmount = BigDecimal.ZERO;

    @Column(name = "total_gst", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal totalGst = BigDecimal.ZERO;

    @Column(name = "final_payable_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal finalPayableAmount = BigDecimal.ZERO;

    @Column(name = "amount_in_words", columnDefinition = "TEXT")
    private String amountInWords;

    // ── Line item snapshots ──
    @Column(name = "spare_lines_json", columnDefinition = "TEXT")
    private String spareLinesJson;

    @Column(name = "service_lines_json", columnDefinition = "TEXT")
    private String serviceLinesJson;

    // ── Audit ──
    @Column(name = "generated_by")
    private UUID generatedBy;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (generatedAt == null) generatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
