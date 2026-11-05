package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A configuration attribute (the "key") for a device category — e.g. for
 * Laptop: "Device Processor", "Processor Series", "Available RAM". Its
 * selectable values live in {@link MasterDeviceConfigOption}.
 */
@Entity
@Table(name = "master_device_config_fields", indexes = {
    @Index(name = "idx_device_config_field_category", columnList = "device_category_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterDeviceConfigField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Device category this config field belongs to (Laptop/Mobile/...).
    @Column(name = "device_category_id")
    private UUID deviceCategoryId;

    // Machine code derived from the name (e.g. DEVICE_PROCESSOR). Not unique —
    // the same key can exist under multiple device categories.
    @Column(length = 100)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "is_active")
    private Boolean isActive;
}
