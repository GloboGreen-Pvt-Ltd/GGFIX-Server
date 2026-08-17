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

    /** model_compatibility_types.id. Null leaves the box untyped. */
    private UUID partTypeId;

    /**
     * Explicitly move a box back to "no type".
     *
     * Needed because null means "leave as is" on every other field, and it has
     * to keep meaning that here: the admin's Active toggle sends {"isActive":…}
     * alone, and treating its absent partTypeId as "clear" would silently strip
     * the type off a box every time someone switched it off.
     */
    private Boolean clearPartType;

    private String boxNo;

    private String boxName;

    /** master_models ids ticked in the form. Empty list clears the box. */
    private List<UUID> models;

    /**
     * Models typed into the form that the catalogue does not carry, stored on
     * this box alone — see CompatibleModelRef#custom for why they are not
     * written to master_models.
     *
     * Sent alongside {@link #models}, not inside it, because the two are checked
     * differently: an id in {@code models} must resolve to a real model or the
     * request is rejected, while these carry their own name and only need a
     * brand. Both together replace the box's whole list, so sending {@code
     * models} without this field on a PUT would drop the added ones.
     */
    private List<CustomModel> customModels;

    private String referenceImageUrl;

    private String notes;

    private Integer sortOrder;

    private Boolean isActive;

    /** One model added by name from the form, for {@link #customModels}. */
    @Getter
    @Setter
    public static class CustomModel {

        /**
         * The id a previous save minted for this entry. Sent back when the box is
         * edited so the entry keeps its identity; null on a newly typed name, and
         * the controller mints one.
         */
        private UUID modelId;

        /** master_brands.id the name was typed under. */
        private UUID brandId;

        /** The name as typed, e.g. "Oppo Reno 3". */
        private String modelName;
    }
}
