package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterStorageOption;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterStorageOptionRepository extends JpaRepository<MasterStorageOption, UUID> {
}
