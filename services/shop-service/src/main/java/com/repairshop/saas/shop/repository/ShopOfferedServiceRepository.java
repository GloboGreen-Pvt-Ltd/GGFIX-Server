package com.repairshop.saas.shop.repository;

import com.repairshop.saas.shop.entity.ShopOfferedService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopOfferedServiceRepository extends JpaRepository<ShopOfferedService, UUID> {

    List<ShopOfferedService> findByShopId(UUID shopId);

    Optional<ShopOfferedService> findByShopIdAndServiceCode(UUID shopId, String serviceCode);
}
