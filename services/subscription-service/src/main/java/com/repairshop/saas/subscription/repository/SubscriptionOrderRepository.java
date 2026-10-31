package com.repairshop.saas.subscription.repository;

import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionOrderRepository extends JpaRepository<SubscriptionOrder, UUID> {

    /** Row lock (SELECT … FOR UPDATE): every state change of an order goes through this. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SubscriptionOrder o where o.id = :id")
    Optional<SubscriptionOrder> lockById(@Param("id") UUID id);

    Optional<SubscriptionOrder> findByRazorpayOrderId(String razorpayOrderId);

    List<SubscriptionOrder> findByOwnerUserIdAndStatusInOrderByCreatedAtDesc(UUID ownerUserId, Collection<String> statuses);

    List<SubscriptionOrder> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);

    List<SubscriptionOrder> findBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);

    List<SubscriptionOrder> findByStatusIn(Collection<String> statuses);

    boolean existsByOwnerUserIdAndPurpose(UUID ownerUserId, String purpose);

    boolean existsByOwnerUserIdAndStatus(UUID ownerUserId, String status);
}
