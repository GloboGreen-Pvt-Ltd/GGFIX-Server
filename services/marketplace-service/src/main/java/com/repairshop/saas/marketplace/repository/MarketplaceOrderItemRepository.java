package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.MarketplaceOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplaceOrderItemRepository extends JpaRepository<MarketplaceOrderItem, UUID> {

    List<MarketplaceOrderItem> findByOrderId(UUID orderId);
}
