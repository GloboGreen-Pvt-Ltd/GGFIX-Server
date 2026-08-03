package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Writeable mirror of order-service's repair_bookings. ticket-service inserts
 * a row here when a shop owner creates a ticket for a customer that is linked
 * to a platform customer_users id, so the booking surfaces in the customer
 * app's "My Orders" feed. Only the columns the shop-side fan-out writes are
 * mapped; pickup/customer-side fields stay null.
 */
@Entity
@Table(name = "repair_bookings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlatformRepairBooking {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "booking_number", nullable = false, unique = true, length = 60) private String bookingNumber;
    // Nullable: walk-in customers (no platform_user_id link) still get a
    // booking mirror row so the owner Service History rail and the technician
    // can read the timeline events. Customer-side feed mirroring (customer_orders,
    // notifications) is skipped for these rows since there's no recipient.
    @Column(name = "customer_user_id") private UUID customerUserId;
    @Column(name = "shop_id") private UUID shopId;
    @Column(name = "ticket_id") private UUID ticketId;
    // Denormalized customer snapshot the order-service writes at create time
    // (migration 30). Used as the ticket-side fallback when mintTicketFromBooking
    // ran before the snapshot logic learned a field — the owner Booking Details
    // "Customer Details" card reads these via TicketService.resolveBookingFallback.
    @Column(name = "customer_name", length = 255) private String customerName;
    @Column(name = "customer_mobile", length = 50) private String customerMobile;
    @Column(name = "pickup_address_id") private UUID pickupAddressId;
    @Column(name = "brand_id") private UUID brandId;
    @Column(name = "model_id") private UUID modelId;
    @Column(name = "ram_option_id") private UUID ramOptionId;
    @Column(name = "storage_option_id") private UUID storageOptionId;
    @Column(length = 100) private String color;
    @Column(name = "service_mode", nullable = false, length = 50) private String serviceMode;
    @Column(name = "issue_summary", columnDefinition = "TEXT") private String issueSummary;
    @Column(name = "estimate_amount", precision = 12, scale = 2) private BigDecimal estimateAmount;
    @Column(name = "final_amount", precision = 12, scale = 2) private BigDecimal finalAmount;
    @Column(nullable = false, length = 50) private String status;
    @Column(name = "estimated_ready_at") private Instant estimatedReadyAt;
    @Column(name = "estimated_delivery_at") private Instant estimatedDeliveryAt;
    @Column(name = "customer_approval", length = 20) private String customerApproval;
    @Column(name = "device_pin", length = 20) private String devicePin;
    @Column(name = "missing_damage_parts", columnDefinition = "TEXT") private String missingDamageParts;
    @Column(name = "technician_name", length = 120) private String technicianName;
    @Column(name = "technician_code", length = 40) private String technicianCode;
    // CSV of technician-uploaded post-acceptance image URLs. The order-service
    // RepairBookingResponse splits this back to a List<String> the customer's
    // detail screen renders as "Technician Photos". CustomerOrderMirrorService
    // converts tickets.technician_photos_json (a JSON array) into this CSV on
    // every mirror so the customer sees the same images the owner does.
    @Column(name = "technician_photos", columnDefinition = "TEXT") private String technicianPhotos;
    // TEXT (not varchar(500)) — Cloudinary fetch-wrapped / normalized image URLs
    // can exceed 500 chars, which overflowed and 500'd the booking insert.
    @Column(name = "front_image_url", columnDefinition = "TEXT") private String frontImageUrl;
    @Column(name = "back_image_url", columnDefinition = "TEXT") private String backImageUrl;
    @Column(name = "video_url", columnDefinition = "TEXT") private String videoUrl;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "ORDER_PLACED";
        if (serviceMode == null) serviceMode = "WALK_IN";
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
