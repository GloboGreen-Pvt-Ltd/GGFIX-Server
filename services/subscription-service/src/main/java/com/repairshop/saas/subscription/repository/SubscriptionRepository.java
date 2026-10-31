package com.repairshop.saas.subscription.repository;

import com.repairshop.saas.subscription.entity.Subscription;
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
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByOwnerUserId(UUID ownerUserId);

    List<Subscription> findAllByOrderByCreatedAtDesc();

    /** Duplicate-tolerant owner lookup (newest first) used by the v2 code. */
    List<Subscription> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);

    /** The owner's row(s), newest first, row-locked for an activation/extension. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.ownerUserId = :ownerUserId order by s.createdAt desc")
    List<Subscription> lockByOwner(@Param("ownerUserId") UUID ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.id = :id")
    Optional<Subscription> lockById(@Param("id") UUID id);
}
