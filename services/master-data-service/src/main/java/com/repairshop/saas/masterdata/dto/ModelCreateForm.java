package com.repairshop.saas.masterdata.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
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
 * Colours, RAM/storage and model numbers are inline {@code jsonb} arrays on
 * master_models (migrations 69/70/73) rather than separate variant rows, so they are
 * set on the same insert. In a multipart body a repeated field name binds to a List,
 * so the client appends {@code colors} once per value rather than sending JSON.
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

    /** Manufacturer numbers, e.g. V2043 — several per model across regions. */
    private List<String> modelNumber = new ArrayList<>();

    /** Colour names as shown in the picker, e.g. "Dawn White". */
    private List<String> colors = new ArrayList<>();

    /** Variant labels, e.g. "4GB+64GB", or storage-only "64GB". */
    private List<String> ramStorage = new ArrayList<>();

    /** Whether the model is offered in the Sell flow. Defaults to true, as in the entity. */
    private Boolean sellActive;
}
