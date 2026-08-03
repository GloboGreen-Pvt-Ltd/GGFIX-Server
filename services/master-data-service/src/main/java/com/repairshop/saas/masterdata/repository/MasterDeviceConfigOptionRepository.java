package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterDeviceConfigOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MasterDeviceConfigOptionRepository extends JpaRepository<MasterDeviceConfigOption, UUID> {

    List<MasterDeviceConfigOption> findByFieldIdOrderBySortOrderAsc(UUID fieldId);

    void deleteByFieldId(UUID fieldId);
}
