package com.repairshop.saas.shop.repository;

import com.repairshop.saas.shop.entity.ShopPickupSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShopPickupSlotRepository extends JpaRepository<ShopPickupSlot, UUID> {

    List<ShopPickupSlot> findByShopId(UUID shopId);
}
