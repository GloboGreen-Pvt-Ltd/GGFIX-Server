package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.Ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByShopIdAndId(UUID shopId, UUID id);

    Optional<Ticket> findByShopIdAndTrackingId(UUID shopId, String trackingId);

    Page<Ticket> findByShopId(UUID shopId, Pageable pageable);

    Page<Ticket> findByShopIdAndStatus(UUID shopId, String status, Pageable pageable);

    Page<Ticket> findByAssignedTechnicianId(UUID assignedTechnicianId, Pageable pageable);

    @Query("""
            SELECT t FROM Ticket t
            WHERE t.shopId = :shopId
              AND (:status IS NULL OR t.status = :status)
              AND (
                LOWER(COALESCE(t.trackingId, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(t.customerName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(t.customerPhone, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(t.deviceDisplayName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(t.repairServicesSummary, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Ticket> searchByShop(
            @Param("shopId") UUID shopId,
            @Param("status") String status,
            @Param("q") String q,
            Pageable pageable);

    boolean existsByShopIdAndTrackingId(UUID shopId, String trackingId);

    long countByShopId(UUID shopId);

    long countByShopIdAndCustomerId(UUID shopId, UUID customerId);

    Optional<Ticket> findFirstByShopIdAndCustomerIdOrderByCreatedAtDesc(UUID shopId, UUID customerId);

    long countByShopIdAndStatus(UUID shopId, String status);

    long countByShopIdAndAssignedTechnicianIdNotNull(UUID shopId);

    @Query("SELECT t.status, COUNT(t) FROM Ticket t WHERE t.shopId = :shopId GROUP BY t.status")
    List<Object[]> countByShopIdGroupByStatus(@Param("shopId") UUID shopId);
}
