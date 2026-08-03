package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.SellOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellOrderRepository extends JpaRepository<SellOrder, UUID> {
    List<SellOrder> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);
    Optional<SellOrder> findBySellNumber(String sellNumber);
}
