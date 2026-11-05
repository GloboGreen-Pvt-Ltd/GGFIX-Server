package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.MarketplaceOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, UUID> {

    List<MarketplaceOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
