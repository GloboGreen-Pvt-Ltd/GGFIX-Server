package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.UUID;

/**
 * Multipart form fields for "New model". Bound from the request parts alongside the
 * image file, which stays a separate {@code MultipartFile} parameter.
 *
 * The client sends IDs only — never names, and never a path. The S3 key is built
 * server-side from the names those IDs resolve to, so a client cannot choose where
 * its bytes land, and renaming a brand cannot split one folder in two because a
 * client cached the old string.
 *
 * <h2>Scope</h2>
 * These four fields are exactly what this endpoint persists. The admin's "New model"
 * screen also collects a model number and colour / RAM / storage variants; those are
 * deliberately NOT accepted here:
 * <ul>
 *   <li>{@code master_models} has no model_number column in this schema, so taking
 *       one would mean silently discarding it.</li>
 *   <li>Variants are {@code master_model_variants} rows keyed by ram_option_id,
 *       storage_option_id and color_id — option UUIDs, not free text — and are owned
 *       by the existing {@code POST /master/model-variants} endpoint.</li>
 * </ul>
 * The client posts this form first and then the variants against the returned model
 * id. Accepting fields we cannot store would look like it worked and lose the data.
 */
@Data
public class ModelCreateForm {

    /** FK -> master_device_categories.id. Supplies the first key segment. */
    private UUID categoryId;

    /** FK -> master_brands.id. Must be mapped to categoryId. */
    private UUID brandId;

    /** FK -> master_device_series.id. Must belong to brandId. */
    private UUID seriesId;

    /** Display name, e.g. "Vivo Y20". Slugified into the model folder segment. */
    private String modelName;
}
