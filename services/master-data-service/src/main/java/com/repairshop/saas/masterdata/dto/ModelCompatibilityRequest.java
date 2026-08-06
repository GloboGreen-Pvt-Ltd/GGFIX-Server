package com.repairshop.saas.masterdata.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Create/update body for a Model Compatibility box.
 *
 * The client sends model IDS only — {@link #models} carries no names. The
 * controller resolves each id against master_models and writes the brand and
 * model names into the stored jsonb itself, so a caller cannot label a box with
 * a model name that does not match the id it claims.
 *
 * Every field is nullable so a PUT can be partial: null means "leave as is",
 * which is how the admin's Active toggle avoids blanking the rest of the row.
 */
@Getter
@Setter
public class ModelCompatibilityRequest {

    private String boxNo;

    private String boxName;

    /** master_models ids ticked in the form. Empty list clears the box. */
    private List<UUID> models;

    private String referenceImageUrl;

    private String notes;

    private Integer sortOrder;

    private Boolean isActive;
}
