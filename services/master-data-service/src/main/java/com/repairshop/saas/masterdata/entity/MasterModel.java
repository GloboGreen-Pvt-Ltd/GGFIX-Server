package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "master_models",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_model_brand_name", columnNames = { "brand_id", "name" }),
                @UniqueConstraint(name = "uq_model_series_slug", columnNames = { "series_id", "slug" })
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MasterModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(nullable = false, length = 255)
    private String name;

    /** SEO-friendly slug, unique within (series_id). */
    @Column(length = 180)
    private String slug;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_base64", columnDefinition = "TEXT")
    private String imageBase64;

    /**
     * Free-form classification label for the UI (e.g. DEVICE / SPARE_PART).
     */
    @Column(name = "category", length = 50)
    private String category;

    /** Optional FK -> master_device_categories.id. */
    @Column(name = "category_id")
    private UUID categoryId;

    /** Optional FK -> master_device_series.id. */
    @Column(name = "series_id")
    private UUID seriesId;

    /*
     * ---- S3 media (migration 63) -------------------------------------------
     * imageUrl above still holds the legacy Cloudinary URL (or a base64 data URI)
     * for rows created before the move to media.ggfix.in. New uploads populate the
     * keys below instead, and the public URL is composed at read time rather than
     * stored, so the CDN hostname never ends up baked into a row.
     */

    /**
     * Folder shared by every image of this model, e.g. {@code mobile/vivo/y-series/vivo-y20}.
     * Derived from category/brand/series/name and stable across image replacements.
     */
    @Column(name = "media_folder_key", length = 512)
    private String mediaFolderKey;

    /**
     * Full S3 object key, e.g. {@code mobile/vivo/y-series/vivo-y20/main-a82f5c1.jpg}.
     * The leaf changes on every upload — that is what defeats CDN and browser caching.
     */
    @Column(name = "image_key", length = 768)
    private String imageKey;

    /** Filename as uploaded. Display and audit only; never used to build the key. */
    @Column(name = "image_original_name", length = 255)
    private String imageOriginalName;

    /** MIME type as validated from the file's own bytes, not as claimed by the client. */
    @Column(name = "image_content_type", length = 100)
    private String imageContentType;

    @Column(name = "image_size_bytes")
    private Long imageSizeBytes;
}
