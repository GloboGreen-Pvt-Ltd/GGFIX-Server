package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.MarketplaceProduct;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceProductRepository extends JpaRepository<MarketplaceProduct, UUID>,
        JpaSpecificationExecutor<MarketplaceProduct> {

    List<MarketplaceProduct> findByStatusOrderByCreatedAtDesc(String status);

    List<MarketplaceProduct> findByTypeAndStatus(String type, String status);

    List<MarketplaceProduct> findByModelIdAndStatus(UUID modelId, String status);

    // ---- Sell module ----

    Optional<MarketplaceProduct> findByIdAndShopId(UUID id, UUID shopId);

    Optional<MarketplaceProduct> findByShopIdAndClientRef(UUID shopId, String clientRef);

    /** SELECT ... FOR UPDATE: serialises stock changes on one listing. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MarketplaceProduct p where p.id = :id")
    Optional<MarketplaceProduct> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByShopIdAndImeiAndStatusIn(UUID shopId, String imei, Collection<String> statuses);

    boolean existsByShopIdAndImeiAndStatusInAndIdNot(UUID shopId, String imei, Collection<String> statuses, UUID id);

    /** [status, count] for the shop's SELL listings (raw statuses, DELETED included). */
    @Query("select p.status, count(p) from MarketplaceProduct p " +
           "where p.shopId = :shopId and upper(p.type) = 'SELL' group by p.status")
    List<Object[]> countSellByStatus(@Param("shopId") UUID shopId);
}
