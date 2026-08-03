package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "technician_kyc_documents",
    uniqueConstraints = @UniqueConstraint(columnNames = {"technician_id", "doc_type"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicianKycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    /** Stable key: aadharFront, aadharBack, pan. */
    @Column(name = "doc_type", nullable = false, length = 40)
    private String docType;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    /** PENDING_REVIEW, APPROVED, REJECTED. */
    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (isRequired == null) isRequired = false;
        if (status == null) status = "PENDING_REVIEW";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
