package com.repairshop.saas.masterdata.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Manufacturer model number(s), stored inline as a jsonb array of codes
     * (e.g. ["MZB0L8AIN","MZB0L88IN","MZB0L89IN"]). A device is often sold under
     * several regional model numbers; each is a distinct entry so the IMEI lookup
     * can match any one of them. Was a single slash-separated string before
     * migration 73. Optional — an empty array means "no model number recorded".
     *
     * Same jsonb mapping as {@link #colors} / {@link #ramStorage}: columnDefinition
     * is omitted so Postgres maps SqlTypes.JSON to jsonb (migration 73) while the
     * H2 dev profile falls back to its own JSON type.
     */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_number")
    private List<String> modelNumber = new ArrayList<>();

    /** SEO-friendly slug, unique within (series_id). Auto-generated from name; not shown in the admin UI. */
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

    /**
     * Whether this model is offered in the customer Sell / trade-in flow.
     * Controlled by the admin Models table "Sell Active" switch. Defaults true
     * so existing models stay sellable; the mobile Sell product picker hides any
     * model with this set to false.
     */
    @Builder.Default
    @Column(name = "sell_active", nullable = false)
    private Boolean sellActive = true;

    /**
     * Colors this model ships in, stored inline as a jsonb array of names
     * (e.g. ["Diamond Black","Skyline Blue","Cosmic Green"]). Replaces the old
     * per-model colour rows in master_model_variants. Master_colors still holds
     * each name's swatch hex for display; this column is the source of truth for
     * which colours a model actually offers.
     *
     * columnDefinition is intentionally omitted: on Postgres Hibernate maps
     * SqlTypes.JSON to jsonb (matching migration 70) and the default (validate)
     * profile relies on the migration for DDL; leaving it off lets the H2 dev
     * profile fall back to its own JSON type instead of choking on "jsonb".
     */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "colors")
    private List<String> colors = new ArrayList<>();

    /**
     * RAM + Storage combinations this model ships in, stored inline as a jsonb
     * array of labels (e.g. ["4 GB + 128 GB","6 GB + 128 GB"]). Replaces the old
     * per-model spec rows in master_model_variants.
     */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ram_storage")
    private List<String> ramStorage = new ArrayList<>();
}
