package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterDeviceCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterDeviceCategoryRepository extends JpaRepository<MasterDeviceCategory, UUID> {

    List<MasterDeviceCategory> findAllByOrderByNameAsc();

    Optional<MasterDeviceCategory> findByCodeIgnoreCase(String code);

    /**
     * Guard before deleting a superseded object: never remove a key another row
     * still points at. The unique index should make that impossible, but a delete
     * is irreversible and this is one indexed lookup.
     */
    boolean existsByImageKey(String imageKey);
}
