package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.PlatformRepairBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformRepairBookingRepository extends JpaRepository<PlatformRepairBooking, UUID> {
    Optional<PlatformRepairBooking> findByTicketId(UUID ticketId);
    Optional<PlatformRepairBooking> findByBookingNumber(String bookingNumber);
}
