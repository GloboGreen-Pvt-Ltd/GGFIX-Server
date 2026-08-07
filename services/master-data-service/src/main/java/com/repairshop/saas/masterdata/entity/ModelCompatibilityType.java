package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A part type on the Model Compatibility screen — "Tempered Glass",
 * "Mobile Case", "Mobile Model Number".
 *
 * These drive the admin sidebar: one child entry per row. That is the reason
 * this is a table rather than an enum — the shop adds a type by saving a row,
 * not by waiting for a release. See migration 80.
 */
@Entity
@Table(name = "model_compatibility_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelCompatibilityType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    /** URL-safe key; the admin links to {@code ?type=<slug>}. Unique, case-insensitively. */
    @Column(nullable = false, length = 140)
    private String slug;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (sortOrder == null) sortOrder = 0;
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
