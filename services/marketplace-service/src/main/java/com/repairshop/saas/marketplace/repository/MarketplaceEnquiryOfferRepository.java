package com.repairshop.saas.marketplace.repository;

import com.repairshop.saas.marketplace.entity.MarketplaceEnquiryOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarketplaceEnquiryOfferRepository extends JpaRepository<MarketplaceEnquiryOffer, UUID> {

    List<MarketplaceEnquiryOffer> findByEnquiryIdOrderByCreatedAtAsc(UUID enquiryId);
}
