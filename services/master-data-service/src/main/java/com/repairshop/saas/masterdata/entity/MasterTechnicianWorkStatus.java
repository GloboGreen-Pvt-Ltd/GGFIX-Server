package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// One row per option in the technician Ticket Detail screen's "Technician
// Work Status" dropdown. label is what the technician sees; ticket_status is
// the ticket status the PATCH applies when selected — keeping the two
// distinct lets the admin add new labels that all map to the same backend
// status (e.g. "Start" and "In Progress" both -> IN_REPAIR).
@Entity
@Table(name = "master_technician_work_statuses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MasterTechnicianWorkStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "ticket_status", nullable = false, length = 50)
    private String ticketStatus;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (sortOrder == null) sortOrder = 0;
        if (isActive == null) isActive = true;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
