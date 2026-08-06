package com.repairshop.saas.masterdata.entity;

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
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompatibleModelRef {

    private UUID brandId;

    private String brandName;

    private UUID modelId;

    private String modelName;
}
