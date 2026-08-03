package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.MasterTechnicianWorkStatusView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MasterTechnicianWorkStatusViewRepository
        extends JpaRepository<MasterTechnicianWorkStatusView, UUID> {

    Optional<MasterTechnicianWorkStatusView> findByCodeIgnoreCase(String code);
}
