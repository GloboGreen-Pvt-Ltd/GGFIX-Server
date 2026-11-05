package com.repairshop.saas.pickup.repository;

import com.repairshop.saas.pickup.entity.CustomerPickupOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerPickupOrderEventRepository extends JpaRepository<CustomerPickupOrderEvent, UUID> {
    List<CustomerPickupOrderEvent> findByPickupOrderIdOrderByCreatedAtAsc(UUID pickupOrderId);
}
