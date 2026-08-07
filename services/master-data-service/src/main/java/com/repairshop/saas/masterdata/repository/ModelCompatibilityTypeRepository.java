package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.ModelCompatibilityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModelCompatibilityTypeRepository extends JpaRepository<ModelCompatibilityType, UUID> {

    List<ModelCompatibilityType> findAllByOrderBySortOrderAscNameAsc();

    Optional<ModelCompatibilityType> findBySlugIgnoreCase(String slug);

    Optional<ModelCompatibilityType> findByNameIgnoreCase(String name);
}
