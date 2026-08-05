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
