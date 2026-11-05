package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.MarketplaceListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, UUID> {

    /**
     * Status filter. Sorting handled in service so we can apply distance after.
     * Lightweight pre-filter on text fields when q is supplied.
     */
    @Query("""
        SELECT l FROM MarketplaceListing l
        WHERE l.status = :status
          AND (:q IS NULL OR :q = ''
               OR LOWER(l.productName)  LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(l.description,'')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(l.city,'')) LIKE LOWER(CONCAT('%', :q, '%')))
    """)
    List<MarketplaceListing> findByStatusAndQuery(@Param("status") String status, @Param("q") String q);
}
