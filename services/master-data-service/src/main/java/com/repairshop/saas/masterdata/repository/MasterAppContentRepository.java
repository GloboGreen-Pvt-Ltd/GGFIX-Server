package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterAppContent;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterAppContentRepository extends JpaRepository<MasterAppContent, UUID> {

    Optional<MasterAppContent> findByCode(String code);
}
