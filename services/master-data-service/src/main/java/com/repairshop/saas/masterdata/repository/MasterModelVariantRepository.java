package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterModelVariant;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterModelVariantRepository extends JpaRepository<MasterModelVariant, UUID> {

    List<MasterModelVariant> findByModelId(UUID modelId);
}
