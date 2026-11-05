package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.PlatformCustomerNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlatformCustomerNotificationRepository extends JpaRepository<PlatformCustomerNotification, UUID> {
}
