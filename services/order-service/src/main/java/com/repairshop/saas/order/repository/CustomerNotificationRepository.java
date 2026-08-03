package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.CustomerNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerNotificationRepository extends JpaRepository<CustomerNotification, UUID> {
    List<CustomerNotification> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);
    long countByCustomerUserIdAndReadFalse(UUID customerUserId);
}
