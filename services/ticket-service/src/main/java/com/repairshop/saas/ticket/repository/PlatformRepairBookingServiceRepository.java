package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.PlatformRepairBookingService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlatformRepairBookingServiceRepository extends JpaRepository<PlatformRepairBookingService, UUID> {
    // Bulk JPQL DELETE — a single SQL statement that bypasses the
    // persistence-context staleness check. The previous derived
    // deleteByBookingId loaded entities first and triggered
    // ObjectOptimisticLockingFailureException when two concurrent mirror
    // rebuilds raced (CustomerOrderMirrorService is fired from two read
    // endpoints when the ticket-detail screen opens). clearAutomatically=true
    // evicts stale references so a follow-up save in the same tx is a fresh
    // INSERT.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PlatformRepairBookingService s where s.bookingId = :bookingId")
    void deleteByBookingId(@Param("bookingId") UUID bookingId);
}
