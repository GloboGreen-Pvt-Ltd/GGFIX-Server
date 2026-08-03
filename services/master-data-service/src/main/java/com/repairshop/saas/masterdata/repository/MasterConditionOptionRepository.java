package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterConditionOption;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterConditionOptionRepository extends JpaRepository<MasterConditionOption, UUID> {

    List<MasterConditionOption> findByGroupIdOrderBySortOrderAsc(UUID groupId);
}
