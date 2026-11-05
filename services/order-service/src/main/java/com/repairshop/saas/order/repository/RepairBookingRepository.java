package com.repairshop.saas.order.repository;

import com.repairshop.saas.order.entity.RepairBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepairBookingRepository extends JpaRepository<RepairBooking, UUID> {
    List<RepairBooking> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerUserId);
    List<RepairBooking> findByCustomerUserIdAndStatusOrderByCreatedAtDesc(UUID customerUserId, String status);
    List<RepairBooking> findByShopIdOrderByCreatedAtDesc(UUID shopId);
    Optional<RepairBooking> findByBookingNumber(String bookingNumber);

    // Pickup-person feed (employee app). assigned_pickup_person_id stores the
    // technician row id, NOT the user id — resolve userId → technicians.id in
    // the controller before calling.
    List<RepairBooking> findByAssignedPickupPersonIdOrderByCreatedAtDesc(UUID pickupPersonId);

    // Used by CustomerOrderController to prune REPAIR/PICKUP/ENQUIRY rows whose
    // referenced repair_bookings row is gone (orphans created by out-of-band
    // deletes — customer_orders has no FK on reference_id).
    @org.springframework.data.jpa.repository.Query(
            "SELECT b.id FROM RepairBooking b WHERE b.id IN :ids")
    List<UUID> findExistingIds(
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
}
