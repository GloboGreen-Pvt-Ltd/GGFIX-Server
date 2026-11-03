package com.repairshop.saas.subscription.repository;

import com.repairshop.saas.subscription.entity.SubscriptionPlanMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanMasterRepository extends JpaRepository<SubscriptionPlanMaster, UUID> {

    Optional<SubscriptionPlanMaster> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<SubscriptionPlanMaster> findAllByOrderByDisplayOrderAscCodeAsc();
}
