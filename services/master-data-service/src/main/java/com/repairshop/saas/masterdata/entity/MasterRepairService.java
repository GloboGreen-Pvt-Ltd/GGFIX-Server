package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "master_repair_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterRepairService {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Optional FK to master_repair_categories(id). Raw UUID for consistency.
     */
    @Column(name = "category_id")
    private UUID categoryId;

    /**
     * Optional FK to master_device_categories(id). Raw UUID for consistency.
     */
    @Column(name = "device_category_id")
    private UUID deviceCategoryId;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "icon_base64", columnDefinition = "TEXT")
    private String iconBase64;
}
