package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A configurable tile on the customer-facing site's Repair / Sell / Buy
 * category menus — name, image and sort order, replacing the hardcoded PNGs
 * those screens shipped with per flow.
 *
 * See migration 97 for the table and for why category_type is a CHECK
 * constraint rather than its own lookup table.
 */
@Entity
@Table(name = "master_category_menu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterCategoryMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", length = 20, nullable = false)
    private CategoryMenuType categoryType;

    @Column(name = "menu_name", length = 255, nullable = false)
    private String menuName;

    @Column(length = 255)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

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
