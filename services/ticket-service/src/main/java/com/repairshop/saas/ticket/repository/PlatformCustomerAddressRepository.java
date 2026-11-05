package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.PlatformCustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformCustomerAddressRepository extends JpaRepository<PlatformCustomerAddress, UUID> {

    // Prefer the row flagged as default; fall back to the most recently
    // created one so prefill still works even when no address was marked.
    @Query("SELECT a FROM PlatformCustomerAddress a " +
            "WHERE a.customerUserId = :userId " +
            "ORDER BY CASE WHEN a.isDefault = true THEN 0 ELSE 1 END, a.id DESC")
    java.util.List<PlatformCustomerAddress> findForUser(@Param("userId") UUID userId);

    default Optional<PlatformCustomerAddress> findPreferred(UUID userId) {
        if (userId == null) return Optional.empty();
        return findForUser(userId).stream().findFirst();
    }
}
