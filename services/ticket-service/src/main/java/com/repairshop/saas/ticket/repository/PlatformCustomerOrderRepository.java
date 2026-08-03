package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.PlatformCustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformCustomerOrderRepository extends JpaRepository<PlatformCustomerOrder, UUID> {
    Optional<PlatformCustomerOrder> findByOrderNumber(String orderNumber);
    Optional<PlatformCustomerOrder> findByReferenceId(UUID referenceId);
}
