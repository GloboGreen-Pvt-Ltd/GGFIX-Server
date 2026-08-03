package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.Technician;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TechnicianRepository extends JpaRepository<Technician, UUID> {

    Optional<Technician> findByShopIdAndId(UUID shopId, UUID id);

    Optional<Technician> findByShopIdAndUserId(UUID shopId, UUID userId);

    Optional<Technician> findFirstByUserId(UUID userId);

    List<Technician> findByShopIdOrderByNameAsc(UUID shopId);
}
