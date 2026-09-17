package com.repairshop.saas.shop.repository;

import com.repairshop.saas.shop.entity.ShopKycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopKycDocumentRepository extends JpaRepository<ShopKycDocument, UUID> {

    List<ShopKycDocument> findByShopIdOrderByCreatedAtAsc(UUID shopId);

    Optional<ShopKycDocument> findByShopIdAndDocType(UUID shopId, String docType);

    void deleteByShopIdAndDocType(UUID shopId, String docType);
}
