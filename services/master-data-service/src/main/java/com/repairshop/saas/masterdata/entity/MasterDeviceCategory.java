package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "master_device_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterDeviceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Machine-readable identifier (e.g. MOBILE, LAPTOP). Auto-derived from
     * {@code name} by the controller when the admin form doesn't supply it.
     * Mobile app still uses this for saved-device filtering and Home tile lookups.
     */
    @Column(unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_base64", columnDefinition = "TEXT")
    private String imageBase64;

    @Column(name = "is_active")
    private Boolean isActive;
}
