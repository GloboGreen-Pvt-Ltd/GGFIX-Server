package com.repairshop.saas.auth.repository;

import com.repairshop.saas.auth.entity.Shop;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopRepository extends JpaRepository<Shop, UUID> {

    Optional<Shop> findBySlug(String slug);

    boolean existsBySlug(String slug);

    java.util.List<Shop> findByOwnerUserIdOrderByCreatedAtAsc(UUID ownerUserId);

    java.util.List<Shop> findByMobile(String mobile);

    /** Pickup-enabled shops with usable coords — filtered by distance in the service layer. */
    java.util.List<Shop> findByPickupEnabledTrueAndLatitudeNotNullAndLongitudeNotNull();
}
