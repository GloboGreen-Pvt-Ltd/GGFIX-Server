package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "technician_experience", indexes = {
    @Index(name = "idx_tech_exp_technician", columnList = "technician_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicianExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "shop_name", length = 255)
    private String shopName;

    @Column(name = "location", length = 500)
    private String location;

    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;

    @Column(name = "relieving_date")
    private LocalDate relievingDate; // null = current/present

    @Column(name = "working_type", length = 50)
    private String workingType; // FULL_TIME, PART_TIME

    @Column(name = "last_salary", length = 50)
    private String lastSalary;

    @Column(name = "total_service")
    private Integer totalService;

    @Column(name = "completed_count")
    private Integer completedCount;

    @Column(name = "return_count")
    private Integer returnCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
