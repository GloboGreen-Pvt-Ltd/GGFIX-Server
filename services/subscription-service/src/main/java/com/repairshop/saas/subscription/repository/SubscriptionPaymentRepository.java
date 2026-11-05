package com.repairshop.saas.subscription.repository;

import com.repairshop.saas.subscription.entity.SubscriptionPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {

    Optional<SubscriptionPayment> findByRazorpayPaymentId(String razorpayPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SubscriptionPayment p where p.id = :id")
    Optional<SubscriptionPayment> lockById(@Param("id") UUID id);

    List<SubscriptionPayment> findByOwnerUserIdOrderByPaidAtDesc(UUID ownerUserId);

    List<SubscriptionPayment> findBySubscriptionIdOrderByPaidAtDesc(UUID subscriptionId);

    List<SubscriptionPayment> findAllByOrderByPaidAtDesc();
}
