package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.MarketplaceEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceEnquiryRepository extends JpaRepository<MarketplaceEnquiry, UUID>,
        JpaSpecificationExecutor<MarketplaceEnquiry> {

    Optional<MarketplaceEnquiry> findByIdAndShopId(UUID id, UUID shopId);

    Optional<MarketplaceEnquiry> findByIdAndBuyerUserId(UUID id, UUID buyerUserId);

    Optional<MarketplaceEnquiry> findFirstByProductIdAndBuyerUserIdAndStatusInOrderByCreatedAtDesc(
            UUID productId, UUID buyerUserId, Collection<String> statuses);

    List<MarketplaceEnquiry> findByProductIdAndStatusIn(UUID productId, Collection<String> statuses);

    List<MarketplaceEnquiry> findByBuyerUserIdOrderByUpdatedAtDesc(UUID buyerUserId);

    long countByShopIdAndShopUnreadTrue(UUID shopId);

    /** [status, kind, count] */
    @Query("select e.status, e.kind, count(e) from MarketplaceEnquiry e " +
           "where e.shopId = :shopId group by e.status, e.kind")
    List<Object[]> countByStatusAndKind(@Param("shopId") UUID shopId);

    /** [status, kind, count] for one listing */
    @Query("select e.status, e.kind, count(e) from MarketplaceEnquiry e " +
           "where e.shopId = :shopId and e.productId = :productId group by e.status, e.kind")
    List<Object[]> countByStatusAndKindForProduct(@Param("shopId") UUID shopId, @Param("productId") UUID productId);

    // Read markers are bulk updates so opening an enquiry never bumps its
    // @Version (which would make a concurrent buyer/shop action fail).
    @Modifying
    @Query("update MarketplaceEnquiry e set e.shopUnread = false where e.id = :id")
    int markShopRead(@Param("id") UUID id);

    @Modifying
    @Query("update MarketplaceEnquiry e set e.buyerUnread = false where e.id = :id")
    int markBuyerRead(@Param("id") UUID id);
}
