package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterConditionGroup;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterConditionGroupRepository extends JpaRepository<MasterConditionGroup, UUID> {

    List<MasterConditionGroup> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByCode(String code);
}
