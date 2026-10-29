package com.repairshop.saas.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repair_booking_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RepairBookingEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "booking_id", nullable = false) private UUID bookingId;
    @Column(nullable = false, length = 100) private String status;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(length = 100) private String actor;
    // Optional media attached to the compliance-note step event. Mirrors
    // PlatformRepairBookingEvent in ticket-service so the customer-facing
    // events list returned by RepairBookingController carries the same
    // voice-note + image URLs the technician submitted.
    @Column(name = "audio_url", columnDefinition = "TEXT") private String audioUrl;
    @Column(name = "images_json", columnDefinition = "TEXT") private String imagesJson;
    // updatable=true (matching PlatformRepairBookingEvent in ticket-service,
    // which maps this same table) so a step that is legitimately re-entered can
    // refresh its timestamp instead of keeping the first one. CUSTOMER_APPROVED
    // is the case that needs it: the customer approves again after every
    // re-estimate, and the timelines pick the first row per status key, so a
    // frozen created_at dated the approval BEFORE the re-estimate it followed.
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }

    // Shop-internal steps ticket-service writes to this shared table that must
    // not reach customer or pickup responses. TECHNICIAN_DECLINED carries the
    // technician's free-text reason for handing a job back to the owner, and as
    // the newest row it would otherwise become the My Orders "Status:" line.
    private static final java.util.Set<String> SHOP_INTERNAL_STATUSES = java.util.Set.of("TECHNICIAN_DECLINED");

    public static boolean isShopInternal(RepairBookingEvent e) {
        return e.getStatus() != null && SHOP_INTERNAL_STATUSES.contains(e.getStatus().trim().toUpperCase());
    }
}
