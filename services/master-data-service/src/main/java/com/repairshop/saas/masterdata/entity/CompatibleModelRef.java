package com.repairshop.saas.masterdata.entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One model ticked on a {@link ModelCompatibility} box, as stored inside the
 * {@code model_compatibility.models} jsonb array.
 *
 * The brand and model NAMES are kept alongside their ids on purpose. The admin
 * table and the shop-side lookup both want to print "ZEBRONICS — ZEB BEETLES",
 * and resolving that from ids would mean every consumer also fetching the whole
 * brand and model catalogue. The ids remain the identity — a rename in
 * master_models does not break the link, it only leaves the cached label stale
 * until the box is edited again.
 *
 * A plain bean rather than a record: Hibernate reads this back through Jackson
 * and the no-arg constructor keeps that mapping trouble-free on both Postgres
 * jsonb and the H2 dev profile.
 *
 * Nulls are left out of the stored JSON so the column keeps holding exactly the
 * four keys it always held — the {@link #custom} flag only appears on the rows
 * that need it, and every box written before it existed reads back unchanged.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompatibleModelRef {

    private UUID brandId;

    private String brandName;

    /**
     * master_models.id — except on a {@link #custom} ref, where it is a UUID
     * minted here purely so the entry has a stable identity to key a list on and
     * to survive a round trip through the edit form. It resolves to no row.
     */
    private UUID modelId;

    private String modelName;

    /**
     * True when this model exists ONLY on this box: the shop typed a name the
     * catalogue does not carry and added it from the compatibility form.
     *
     * Kept out of master_models on purpose. A fitment list is written off a
     * supplier's sheet and carries names the catalogue may never gain ("Reno 3"
     * as a shop writes it, a model sold in one region, a name misspelt on the
     * box). Letting that create catalogue rows would push unvetted names into
     * every brand/model picker in all four apps, so the name is stored inline in
     * this box's jsonb and is visible nowhere else.
     *
     * Null on catalogue-backed refs rather than false, so the stored JSON of an
     * ordinary model is byte-for-byte what it was before this flag existed.
     */
    private Boolean custom;
}
