package com.repairshop.saas.shop.repository;

import com.repairshop.saas.shop.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopRepository extends JpaRepository<Shop, UUID> {

    List<Shop> findByIsActiveTrue();

    Optional<Shop> findBySlug(String slug);

    List<Shop> findByCityIgnoreCaseAndIsActiveTrue(String city);
}
