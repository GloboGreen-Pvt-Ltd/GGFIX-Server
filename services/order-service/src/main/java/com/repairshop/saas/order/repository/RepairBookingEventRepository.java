package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.RepairBookingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepairBookingEventRepository extends JpaRepository<RepairBookingEvent, UUID> {
    // Also drives the live "Status:" line on the customer My Orders card, via
    // CustomerOrderController#latestEvent. That picks the newest row in Java
    // rather than with a findFirst…OrderByCreatedAtDesc: paired steps can share
    // one timestamp, and only Java can break that tie on lifecycle order so the
    // card reads the same on every refresh and device.
    List<RepairBookingEvent> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
