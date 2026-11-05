package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterRepairService;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterRepairServiceRepository extends JpaRepository<MasterRepairService, UUID> {

    List<MasterRepairService> findByDeviceCategoryId(UUID deviceCategoryId);

    List<MasterRepairService> findByCategoryId(UUID categoryId);

    List<MasterRepairService> findByDeviceCategoryIdAndCategoryId(UUID deviceCategoryId, UUID categoryId);

    boolean existsByCode(String code);
}
