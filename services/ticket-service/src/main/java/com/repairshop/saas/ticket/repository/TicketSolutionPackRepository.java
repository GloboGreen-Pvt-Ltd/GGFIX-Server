package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.TicketSolutionPack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TicketSolutionPackRepository extends JpaRepository<TicketSolutionPack, UUID> {
    List<TicketSolutionPack> findByTicketIdOrderByCreatedAtDesc(UUID ticketId);
    List<TicketSolutionPack> findByTicketIdAndPackTypeOrderByCreatedAtDesc(UUID ticketId, String packType);

    // Shop-wide search for the reference-view screen. Each filter is optional
    // (null = match-any). issueCategory / issueSubcategory match case-insensitively
    // so the UI doesn't have to canonicalize labels coming from radio selections.
    @Query("""
        SELECT p FROM TicketSolutionPack p
        WHERE p.shopId = :shopId
          AND (:packType IS NULL OR p.packType = :packType)
          AND (:brandId IS NULL OR p.brandId = :brandId)
          AND (:modelId IS NULL OR p.modelId = :modelId)
          AND (:issueCategoryId IS NULL OR p.issueCategoryId = :issueCategoryId)
          AND (:issueSubcategoryId IS NULL OR p.issueSubcategoryId = :issueSubcategoryId)
          AND (:issueCategory IS NULL OR LOWER(p.issueCategory) = LOWER(:issueCategory))
          AND (:issueSubcategory IS NULL OR LOWER(p.issueSubcategory) = LOWER(:issueSubcategory))
        ORDER BY p.createdAt DESC
    """)
    List<TicketSolutionPack> searchByShop(
            @Param("shopId") UUID shopId,
            @Param("packType") String packType,
            @Param("brandId") UUID brandId,
            @Param("modelId") UUID modelId,
            @Param("issueCategoryId") UUID issueCategoryId,
            @Param("issueSubcategoryId") UUID issueSubcategoryId,
            @Param("issueCategory") String issueCategory,
            @Param("issueSubcategory") String issueSubcategory);
}
