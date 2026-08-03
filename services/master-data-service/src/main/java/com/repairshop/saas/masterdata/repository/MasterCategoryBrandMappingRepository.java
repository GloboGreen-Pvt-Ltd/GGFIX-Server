package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterCategoryBrandMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MasterCategoryBrandMappingRepository extends JpaRepository<MasterCategoryBrandMapping, UUID> {

    List<MasterCategoryBrandMapping> findByCategoryId(UUID categoryId);

    List<MasterCategoryBrandMapping> findByBrandId(UUID brandId);

    Optional<MasterCategoryBrandMapping> findByCategoryIdAndBrandId(UUID categoryId, UUID brandId);

    boolean existsByCategoryIdAndBrandId(UUID categoryId, UUID brandId);
}
