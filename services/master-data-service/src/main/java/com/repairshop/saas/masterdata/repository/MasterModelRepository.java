package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterModel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterModelRepository extends JpaRepository<MasterModel, UUID> {

    List<MasterModel> findByBrandIdOrderByName(UUID brandId);

    List<MasterModel> findBySeriesIdOrderByName(UUID seriesId);

    List<MasterModel> findByCategoryIdOrderByName(UUID categoryId);

    /**
     * Models whose model_number jsonb array contains the given code, case-insensitively
     * — the bridge used by the IMEI.info lookup (e.g. "CPH2735" -> OPPO A5 5G). Since
     * model_number is now an array, this matches a model that lists the code among its
     * several regional numbers. Postgres-only (jsonb functions); only ever executed by
     * the IMEI lookup, which runs on the default (Postgres) profile.
     */
    @Query(value = "SELECT * FROM master_models m WHERE EXISTS ("
            + "SELECT 1 FROM jsonb_array_elements_text(m.model_number) AS e WHERE upper(e) = upper(:code))",
            nativeQuery = true)
    List<MasterModel> findByModelNumberContainingIgnoreCase(@Param("code") String code);

    /* ---- S3 media (migration 79) ---------------------------------------- */

    /**
     * Guard before deleting a superseded object: if any other row still points at
     * the key, the object is shared and must be left alone. Should not happen given
     * the unique index on image_key, but a delete is irreversible and the check is
     * a single indexed lookup.
     */
    Optional<MasterModel> findByImageKey(String imageKey);

    /**
     * Every model sharing one folder, i.e. all models in the same
     * category/brand/series. Backs the "reuse the common base path" behaviour and
     * makes a folder's contents listable without touching S3.
     */
    List<MasterModel> findByMediaFolderKeyOrderByName(String mediaFolderKey);

    /** Name uniqueness within a brand, matching the uq_model_brand_name constraint. */
    boolean existsByBrandIdAndNameIgnoreCase(UUID brandId, String name);
}
