package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.MarketplaceOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, UUID> {

    List<MarketplaceOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<MarketplaceOrder> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);

    List<MarketplaceOrder> findByShopIdAndProductIdIsNotNullOrderByCreatedAtDesc(UUID shopId);

    List<MarketplaceOrder> findByAssignedStaffUserIdOrderByCreatedAtDesc(UUID staffUserId);

    java.util.Optional<MarketplaceOrder> findByCustomerUserIdAndIdempotencyKey(UUID customerUserId, String key);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from MarketplaceOrder o where o.id = :id")
    java.util.Optional<MarketplaceOrder> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
