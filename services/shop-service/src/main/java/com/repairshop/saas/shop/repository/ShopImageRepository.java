package com.repairshop.saas.shop.repository;

import com.repairshop.saas.shop.entity.ShopImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShopImageRepository extends JpaRepository<ShopImage, UUID> {

    List<ShopImage> findByShopIdOrderBySortOrderAsc(UUID shopId);
}
