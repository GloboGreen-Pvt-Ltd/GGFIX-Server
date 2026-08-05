package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "master_brands")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterBrand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Public URL or path to display brand logo/image in mobile app and admin UI.
     */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    /**
     * Optional base64-encoded logo image (PNG/JPEG) for dropdowns and offline use.
     * When set, mobile app can use data:image/png;base64,{imageBase64} without external URLs.
     */
    @Column(name = "image_base64", columnDefinition = "TEXT")
    private String imageBase64;
}
