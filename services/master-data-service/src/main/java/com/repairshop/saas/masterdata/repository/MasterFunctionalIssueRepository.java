package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterFunctionalIssue;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterFunctionalIssueRepository extends JpaRepository<MasterFunctionalIssue, UUID> {

    List<MasterFunctionalIssue> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByCode(String code);
}
