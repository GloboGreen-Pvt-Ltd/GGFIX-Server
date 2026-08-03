package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterBanner;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterBannerRepository extends JpaRepository<MasterBanner, UUID> {

    List<MasterBanner> findAllByOrderBySortOrderAsc();
}
