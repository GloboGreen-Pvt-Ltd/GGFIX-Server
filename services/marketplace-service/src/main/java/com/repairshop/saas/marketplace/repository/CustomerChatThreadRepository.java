package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.CustomerChatThread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerChatThreadRepository extends JpaRepository<CustomerChatThread, UUID> {

    List<CustomerChatThread> findByCustomerUserIdOrderByLastMessageAtDesc(UUID customerUserId);

    List<CustomerChatThread> findByShopIdOrderByLastMessageAtDesc(UUID shopId);

    Optional<CustomerChatThread> findByCustomerUserIdAndShopId(UUID customerUserId, UUID shopId);
}
