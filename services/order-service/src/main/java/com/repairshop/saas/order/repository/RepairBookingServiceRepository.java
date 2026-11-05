package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.RepairBookingService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepairBookingServiceRepository extends JpaRepository<RepairBookingService, UUID> {
    List<RepairBookingService> findByBookingId(UUID bookingId);
}
