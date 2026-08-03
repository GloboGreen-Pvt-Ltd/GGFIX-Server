package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.TechnicianSalaryAdvance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TechnicianSalaryAdvanceRepository extends JpaRepository<TechnicianSalaryAdvance, UUID> {

    List<TechnicianSalaryAdvance> findTop10ByTechnicianIdOrderByRequestedAtDesc(UUID technicianId);
}
