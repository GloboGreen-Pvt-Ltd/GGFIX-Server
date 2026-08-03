package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.MarketplaceProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplaceProductRepository extends JpaRepository<MarketplaceProduct, UUID> {

    List<MarketplaceProduct> findByStatusOrderByCreatedAtDesc(String status);

    List<MarketplaceProduct> findByTypeAndStatus(String type, String status);

    List<MarketplaceProduct> findByModelIdAndStatus(UUID modelId, String status);
}
