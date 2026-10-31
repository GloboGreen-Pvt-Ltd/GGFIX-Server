package com.repairshop.saas.subscription.repository;

import com.repairshop.saas.subscription.entity.SubscriptionShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionShopRepository extends JpaRepository<SubscriptionShop, UUID> {

    List<SubscriptionShop> findBySubscriptionId(UUID subscriptionId);

    /** Bulk delete runs immediately, so re-inserting the same (subscription, shop)
     *  pair in the same transaction cannot trip the unique constraint. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from SubscriptionShop s where s.subscriptionId = :subscriptionId")
    int deleteAllForSubscription(@Param("subscriptionId") UUID subscriptionId);
}
