package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.RepairBookingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairBookingEventRepository extends JpaRepository<RepairBookingEvent, UUID> {
    List<RepairBookingEvent> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
    // Latest timeline event for a booking. Drives the live "Status:" line on
    // the customer My Orders card so a PENDING customer_orders row reflects
    // the most recent shop/technician step (e.g. "Technician Work Started").
    Optional<RepairBookingEvent> findFirstByBookingIdOrderByCreatedAtDesc(UUID bookingId);
}
