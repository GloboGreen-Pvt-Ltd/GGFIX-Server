package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.PlatformShopNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlatformShopNotificationRepository extends JpaRepository<PlatformShopNotification, UUID> {

    /** Dedupe guard for re-emitted timeline events — a technician editing a
     *  compliance note must not raise the same shop alert twice. */
    boolean existsByBookingIdAndStatusKey(UUID bookingId, String statusKey);
}
