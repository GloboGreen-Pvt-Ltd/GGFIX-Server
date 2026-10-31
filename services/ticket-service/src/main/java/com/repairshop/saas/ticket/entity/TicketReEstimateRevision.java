package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One re-estimate on a ticket: technician request → partner revision →
 * customer decision. See migration 107 and ReEstimateWorkflowService.
 */
@Entity
@Table(name = "ticket_reestimate_revisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketReEstimateRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "revision_no", nullable = false)
    private Integer revisionNo;

    /** TECHNICIAN | PARTNER */
    @Column(name = "source", length = 20, nullable = false)
    private String source;

    /** REQUESTED | AWAITING_APPROVAL | APPROVED | REJECTED | DISMISSED */
    @Column(name = "status", length = 30, nullable = false)
    private String status;

    // ---- Technician request ----
    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "technician_name", length = 255)
    private String technicianName;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "additional_issue", columnDefinition = "TEXT")
    private String additionalIssue;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "proposed_amount", precision = 12, scale = 2)
    private BigDecimal proposedAmount;

    @Column(name = "technician_notes", columnDefinition = "TEXT")
    private String technicianNotes;

    @Column(name = "request_images_json", columnDefinition = "TEXT")
    private String requestImagesJson;

    // ---- Partner revision ----
    @Column(name = "submitted_by_user_id")
    private UUID submittedByUserId;

    @Column(name = "submitted_by_name", length = 255)
    private String submittedByName;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** The items this revision added: [{id,code,label,amount,warranty}]. */
    @Column(name = "items_json", columnDefinition = "TEXT")
    private String itemsJson;

    @Column(name = "additional_amount", precision = 12, scale = 2)
    private BigDecimal additionalAmount;

    @Column(name = "original_estimated_price", precision = 12, scale = 2)
    private BigDecimal originalEstimatedPrice;

    @Column(name = "original_price_items_json", columnDefinition = "TEXT")
    private String originalPriceItemsJson;

    /** Other pre-revision ticket fields restored on REJECTED (summary, approval, delivery dates). */
    @Column(name = "original_state_json", columnDefinition = "TEXT")
    private String originalStateJson;

    @Column(name = "revised_total", precision = 12, scale = 2)
    private BigDecimal revisedTotal;

    @Column(name = "revision_reason", columnDefinition = "TEXT")
    private String revisionReason;

    @Column(name = "revision_images_json", columnDefinition = "TEXT")
    private String revisionImagesJson;

    // ---- Decision ----
    /** APPROVED | REJECTED | DISMISSED */
    @Column(name = "decision", length = 20)
    private String decision;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decided_by_name", length = 255)
    private String decidedByName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    /** SHOP | CUSTOMER_APP */
    @Column(name = "decided_via", length = 20)
    private String decidedVia;

    @Column(name = "technician_acknowledged_at")
    private Instant technicianAcknowledgedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
