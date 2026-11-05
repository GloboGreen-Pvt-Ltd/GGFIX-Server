package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "technician_salary_advance", indexes = {
    @Index(name = "idx_tech_advance_technician", columnList = "technician_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicianSalaryAdvance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "advance_date", nullable = false)
    private LocalDate advanceDate;

    @Column(name = "status", length = 50, nullable = false)
    private String status; // PAID, UNPAID

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (requestedAt == null) requestedAt = now;
        if (createdAt == null) createdAt = now;
    }
}
