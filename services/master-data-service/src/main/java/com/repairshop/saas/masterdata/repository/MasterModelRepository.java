package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterModel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterModelRepository extends JpaRepository<MasterModel, UUID> {

    List<MasterModel> findByBrandIdOrderByName(UUID brandId);

    List<MasterModel> findBySeriesIdOrderByName(UUID seriesId);

    List<MasterModel> findByCategoryIdOrderByName(UUID categoryId);

    /* ---- S3 media (migration 63) ---------------------------------------- */

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
