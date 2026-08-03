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
}
