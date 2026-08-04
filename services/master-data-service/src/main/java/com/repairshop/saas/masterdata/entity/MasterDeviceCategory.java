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

    /*
     * ---- S3 media (migration 80) -------------------------------------------
     * imageUrl / imageBase64 above hold the legacy value: with Cloudinary
     * unconfigured the admin uploader inlined the whole file as a base64 data URI
     * into imageUrl. New uploads set imageKey instead and the public URL is
     * composed at read time, so the CDN hostname is never baked into a row.
     */

    /** S3 object key, e.g. {@code master/categories/audio-device-1f0ab993.jpg}. */
    @Column(name = "image_key", length = 768)
    private String imageKey;

    /** Filename as uploaded. Display and audit only; never used to build the key. */
    @Column(name = "image_original_name", length = 255)
    private String imageOriginalName;

    /** MIME type validated from the file's own bytes, not as claimed by the client. */
    @Column(name = "image_content_type", length = 100)
    private String imageContentType;

    @Column(name = "image_size_bytes")
    private Long imageSizeBytes;
}
