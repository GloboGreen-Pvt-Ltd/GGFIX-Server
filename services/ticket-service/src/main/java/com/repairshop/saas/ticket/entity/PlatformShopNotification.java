package com.repairshop.saas.ticket.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Shop-owner-side notification row. The table is owned by order-service
 * (see its ShopNotification entity + database/schema/29_shop_notifications.sql);
 * this is the ticket-service-side writer so technician-driven Service History
 * events can reach the owner's feed without a cross-service call.
 *
 * Mirrors {@link PlatformCustomerNotification}, but scoped by shop_id.
 */
@Entity
@Table(name = "shop_notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlatformShopNotification {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "booking_id") private UUID bookingId;
    @Column(name = "booking_number", length = 60) private String bookingNumber;
    // Lets the owner app deep-link straight into Service History (migration 86).
    @Column(name = "ticket_id") private UUID ticketId;
    @Column(name = "status_key", length = 100) private String statusKey;
    @Column(nullable = false, length = 200) private String title;
    @Column(columnDefinition = "TEXT") private String body;
    @Column(length = 30) private String type;
    @Column(name = "is_read", nullable = false) private Boolean isRead;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @PrePersist void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (isRead == null) isRead = false;
        if (type == null) type = "bookings";
    }
}
