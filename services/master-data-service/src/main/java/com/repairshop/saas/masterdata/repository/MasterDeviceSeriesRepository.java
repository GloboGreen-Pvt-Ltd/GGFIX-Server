package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterDeviceSeries;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterDeviceSeriesRepository extends JpaRepository<MasterDeviceSeries, UUID> {

    List<MasterDeviceSeries> findByBrandIdOrderBySortOrderAscNameAsc(UUID brandId);

    List<MasterDeviceSeries> findByCategoryBrandIdOrderBySortOrderAscNameAsc(UUID categoryBrandId);
}
