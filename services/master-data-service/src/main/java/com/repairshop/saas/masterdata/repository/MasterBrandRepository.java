package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterBrand;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterBrandRepository extends JpaRepository<MasterBrand, UUID> {

    /** Rows still using one image URL — the guard before deleting a superseded logo. */
    long countByImageUrl(String imageUrl);
}
