package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A physical spare-part box and the device models its part fits.
 *
 * Managed from Admin panel -> Master Data -> Model Compatibility. Staff know a
 * part by where it lives — "the display for that Zebronics is in box A-12" — so
 * the box number and name are the identity of the row and the model list is
 * what makes it searchable.
 *
 * See migration 79 for the table and for why the model list is inline jsonb
 * rather than a join table.
 */
@Entity
@Table(name = "model_compatibility")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelCompatibility {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Shelf label, e.g. "A-12". Unique case-insensitively (see migration 79). */
    @Column(name = "box_no", nullable = false, length = 60)
    private String boxNo;

    /** What the box holds, e.g. "Display Combo — 6.5 inch". */
    @Column(name = "box_name", nullable = false, length = 255)
    private String boxName;

    /**
     * Models this box's part fits, stored inline as a jsonb array of
     * {@link CompatibleModelRef}. May legitimately be empty: a box can be
     * created and labelled before anyone works out what fits it.
     *
     * columnDefinition is deliberately omitted, matching MasterModel#colors —
     * on Postgres Hibernate maps SqlTypes.JSON to jsonb (migration 79) while the
     * H2 dev profile falls back to its own JSON type instead of choking on
     * "jsonb".
     */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "models")
    private List<CompatibleModelRef> models = new ArrayList<>();

    /**
     * Public media.ggfix.in URL of a reference photo of the part. Stores the URL
     * in one column and adds no image_key / content-type metadata, for the
     * reasons written up on TaxonomyMediaService.
     */
    @Column(name = "reference_image_url", columnDefinition = "TEXT")
    private String referenceImageUrl;

    /** Free-text note — fitment caveats, supplier, anything the shop needs. */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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
        if (models == null) models = new ArrayList<>();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
