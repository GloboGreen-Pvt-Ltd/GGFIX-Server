package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterBrand;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterBrandRepository extends JpaRepository<MasterBrand, UUID> {

    /**
     * Guard before deleting a superseded object: never remove a key another row
     * still points at. The unique index should make that impossible, but a delete
     * is irreversible and this is one indexed lookup.
     */
    boolean existsByImageKey(String imageKey);
}
