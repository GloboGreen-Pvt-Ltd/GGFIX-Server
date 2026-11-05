package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "master_repair_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterRepairCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    /** FK -> master_device_categories.id. A main category belongs to one device
     * category (Mobile -> Display & Touch, Power & Charging, ...). */
    @Column(name = "device_category_id")
    private UUID deviceCategoryId;

    @Column(nullable = false, length = 255)
    private String name;

    /** Customer-facing label, e.g. name "Display & Touch" -> "Screen / Touch Problems". */
    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "icon_base64", columnDefinition = "TEXT")
    private String iconBase64;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active")
    private Boolean isActive;
}
