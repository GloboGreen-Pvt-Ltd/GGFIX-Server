package com.repairshop.saas.pickup.repository;

import com.repairshop.saas.pickup.entity.CustomerPickupOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerPickupOrderRepository extends JpaRepository<CustomerPickupOrder, UUID> {
    List<CustomerPickupOrder> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);
    Optional<CustomerPickupOrder> findByOrderNumber(String orderNumber);
    List<CustomerPickupOrder> findByCustomerUserIdAndStatusInOrderByCreatedAtDesc(UUID customerUserId, List<String> statuses);
    List<CustomerPickupOrder> findByShopIdOrderByCreatedAtDesc(UUID shopId);
    List<CustomerPickupOrder> findByShopIdAndStatusInOrderByCreatedAtDesc(UUID shopId, List<String> statuses);
}
