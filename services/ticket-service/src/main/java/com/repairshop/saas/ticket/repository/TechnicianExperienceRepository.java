package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.TechnicianExperience;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TechnicianExperienceRepository extends JpaRepository<TechnicianExperience, UUID> {

    List<TechnicianExperience> findByTechnicianIdOrderByJoinDateDesc(UUID technicianId);
}
