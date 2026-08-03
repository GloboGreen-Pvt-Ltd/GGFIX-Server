package com.repairshop.saas.pickup.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "customer_pickup_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPickupOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 60)
    private String orderNumber;

    @Column(name = "customer_user_id", nullable = false)
    private UUID customerUserId;

    @Column(name = "shop_id")
    private UUID shopId;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "address_id")
    private UUID addressId;

    @Column(name = "flow_type", nullable = false, length = 50)
    private String flowType;

    @Column(name = "pickup_date")
    private LocalDate pickupDate;

    @Column(name = "pickup_slot_start")
    private LocalTime pickupSlotStart;

    @Column(name = "pickup_slot_end")
    private LocalTime pickupSlotEnd;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "estimate_amount", precision = 12, scale = 2)
    private BigDecimal estimateAmount;

    @Column(name = "final_amount", precision = 12, scale = 2)
    private BigDecimal finalAmount;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "ORDER_PLACED";
        if (flowType == null) flowType = "REPAIR_PICKUP";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
