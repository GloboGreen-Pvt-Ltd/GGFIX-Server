package com.repairshop.saas.subscription.repository;

import com.repairshop.saas.subscription.entity.SubscriptionAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionAuditLogRepository extends JpaRepository<SubscriptionAuditLog, UUID> {

    List<SubscriptionAuditLog> findAllByOrderByAtDesc(Pageable pageable);
}
