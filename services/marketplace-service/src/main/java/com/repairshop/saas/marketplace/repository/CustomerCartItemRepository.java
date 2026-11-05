package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.CustomerCartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerCartItemRepository extends JpaRepository<CustomerCartItem, UUID> {

    List<CustomerCartItem> findByCustomerUserId(UUID customerUserId);

    Optional<CustomerCartItem> findByCustomerUserIdAndProductId(UUID customerUserId, UUID productId);

    @Modifying
    @Transactional
    void deleteByCustomerUserIdAndProductId(UUID customerUserId, UUID productId);

    @Modifying
    @Transactional
    void deleteByCustomerUserId(UUID customerUserId);
}
