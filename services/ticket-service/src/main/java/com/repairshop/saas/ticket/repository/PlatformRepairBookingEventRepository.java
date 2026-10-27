package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.PlatformRepairBookingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlatformRepairBookingEventRepository extends JpaRepository<PlatformRepairBookingEvent, UUID> {
    List<PlatformRepairBookingEvent> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);

    /** Marker row written when a delivered booking is reopened for Re-Service. */
    String RE_SERVICE = "RE_SERVICE";

    /**
     * Events of the booking's current repair: from the last RE_SERVICE marker
     * on (all of them when it was never reopened). The shop's events endpoint
     * and the dedup / "already recorded" checks read this, so a reopened
     * booking walks its steps again instead of matching the first repair's rows.
     */
    default List<PlatformRepairBookingEvent> findCurrentCycle(UUID bookingId) {
        List<PlatformRepairBookingEvent> all = findByBookingIdOrderByCreatedAtAsc(bookingId);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (RE_SERVICE.equalsIgnoreCase(all.get(i).getStatus())) return all.subList(i, all.size());
        }
        return all;
    }
}
