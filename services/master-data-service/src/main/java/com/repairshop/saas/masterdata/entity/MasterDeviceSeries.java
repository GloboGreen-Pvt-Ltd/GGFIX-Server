package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "master_device_series",
        uniqueConstraints = @UniqueConstraint(name = "uq_series_cb_slug", columnNames = { "category_brand_id", "slug" })
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterDeviceSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Legacy column kept for backward compatibility with the original
     * brand-only series API. New code should use {@link #categoryBrandId}.
     */
    @Column(name = "brand_id")
    private UUID brandId;

    /**
     * FK -> master_category_brand_mapping.id. Identifies which (category, brand)
     * pair this series belongs to. Required for all new series; older rows may
     * still have only {@link #brandId} populated.
     */
    @Column(name = "category_brand_id")
    private UUID categoryBrandId;

    @Column(nullable = false, length = 150)
    private String name;

    /** SEO-friendly slug, unique within (category_brand_id). */
    @Column(length = 180)
    private String slug;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
