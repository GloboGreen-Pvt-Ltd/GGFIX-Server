package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterFaqItem;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterFaqItemRepository extends JpaRepository<MasterFaqItem, UUID> {

    List<MasterFaqItem> findAllByOrderBySortOrderAsc();
}
