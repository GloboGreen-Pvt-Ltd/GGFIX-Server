package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterDeviceConfigField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MasterDeviceConfigFieldRepository extends JpaRepository<MasterDeviceConfigField, UUID> {

    List<MasterDeviceConfigField> findAllByOrderBySortOrderAscNameAsc();

    List<MasterDeviceConfigField> findByDeviceCategoryIdOrderBySortOrderAscNameAsc(UUID deviceCategoryId);
}
