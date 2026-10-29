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

    /**
     * One technician's jobs (Staff app list / assignedToMe). {@code anyStatus}
     * stands in for "no status filter" so an empty IN list is never bound.
     * {@code q} matches booking and device text like searchByShop, minus the
     * customer's phone, which employees don't see before accepting; pass ""
     * for no search — '%%' matches every row.
     */
    @Query("""
            SELECT t FROM Ticket t
            WHERE t.shopId = :shopId
              AND t.assignedTechnicianId = :technicianId
              AND (:anyStatus = true OR t.status IN :statuses)
              AND (
                LOWER(COALESCE(t.trackingId, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(t.bookingNumber, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(t.customerName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(t.deviceDisplayName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(t.repairServicesSummary, '')) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Ticket> searchByAssignedTechnician(
            @Param("shopId") UUID shopId,
            @Param("technicianId") UUID technicianId,
            @Param("anyStatus") boolean anyStatus,
            @Param("statuses") List<String> statuses,
            @Param("q") String q,
            Pageable pageable);

    @Query("SELECT t.status, COUNT(t) FROM Ticket t WHERE t.shopId = :shopId AND t.assignedTechnicianId = :technicianId GROUP BY t.status")
    List<Object[]> countByAssignedTechnicianGroupByStatus(@Param("shopId") UUID shopId,
                                                          @Param("technicianId") UUID technicianId);

    boolean existsByShopIdAndTrackingId(UUID shopId, String trackingId);

    /**
     * Every ticket in this shop carrying an IMEI, for the uniqueness gate in
     * front of invoice generation.
     *
     * Scoped to the shop on purpose — the same handset legitimately passes
     * through different shops, and a cross-tenant match would both block a
     * valid booking and leak that another shop holds the device. Narrowing to
     * "still open" and excluding the ticket being edited happens in the
     * service: the row counts here are tiny, and the lifecycle vocabulary that
     * decides what "open" means already lives there.
     */
    List<Ticket> findByShopIdAndImei(UUID shopId, String imei);

    long countByShopId(UUID shopId);

    long countByShopIdAndCustomerId(UUID shopId, UUID customerId);

    Optional<Ticket> findFirstByShopIdAndCustomerIdOrderByCreatedAtDesc(UUID shopId, UUID customerId);

    long countByShopIdAndStatus(UUID shopId, String status);

    long countByShopIdAndAssignedTechnicianIdNotNull(UUID shopId);

    @Query("SELECT t.status, COUNT(t) FROM Ticket t WHERE t.shopId = :shopId GROUP BY t.status")
    List<Object[]> countByShopIdGroupByStatus(@Param("shopId") UUID shopId);
}
