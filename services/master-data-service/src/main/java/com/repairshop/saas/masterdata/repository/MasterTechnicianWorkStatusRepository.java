package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterTechnicianWorkStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MasterTechnicianWorkStatusRepository
        extends JpaRepository<MasterTechnicianWorkStatus, UUID> {

    List<MasterTechnicianWorkStatus> findAllByOrderBySortOrderAscLabelAsc();

    List<MasterTechnicianWorkStatus> findByIsActiveTrueOrderBySortOrderAscLabelAsc();

    boolean existsByCode(String code);
}
