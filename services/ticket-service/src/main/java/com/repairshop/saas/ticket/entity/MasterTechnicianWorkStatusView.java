package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

// Read-only view of master-data-service's master_technician_work_statuses table.
// Lives in ticket-service so emitProgressStepEvent can advance ticket.status
// using the admin-managed code → ticketStatus mapping the BookingsHistory badge
// reads from. Same DB / same schema as master-data-service — no cross-service
// HTTP call required (matches the existing PlatformCustomerOrder / *Booking
// cross-schema read pattern in this service).
@Entity
@Table(name = "master_technician_work_statuses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MasterTechnicianWorkStatusView {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "ticket_status", nullable = false, length = 50)
    private String ticketStatus;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
