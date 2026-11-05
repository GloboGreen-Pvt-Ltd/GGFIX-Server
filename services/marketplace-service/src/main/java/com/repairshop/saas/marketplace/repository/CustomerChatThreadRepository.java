package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.CustomerChatThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerChatThreadRepository extends JpaRepository<CustomerChatThread, UUID> {

    List<CustomerChatThread> findByCustomerUserIdOrderByLastMessageAtDesc(UUID customerUserId);

    List<CustomerChatThread> findByShopIdOrderByLastMessageAtDesc(UUID shopId);

    Optional<CustomerChatThread> findByCustomerUserIdAndShopId(UUID customerUserId, UUID shopId);

    // Batch lookups for the marketplace enquiry lists (chatThreadId per row).
    List<CustomerChatThread> findByShopIdAndCustomerUserIdIn(UUID shopId, Collection<UUID> customerUserIds);

    List<CustomerChatThread> findByCustomerUserIdAndShopIdIn(UUID customerUserId, Collection<UUID> shopIds);
}
