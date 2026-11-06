package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.MarketplaceOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MarketplaceOrderEventRepository extends JpaRepository<MarketplaceOrderEvent, UUID> {
    List<MarketplaceOrderEvent> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
