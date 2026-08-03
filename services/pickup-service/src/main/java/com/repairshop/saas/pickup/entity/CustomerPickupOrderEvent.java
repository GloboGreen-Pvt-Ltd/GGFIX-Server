package com.repairshop.saas.pickup.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_pickup_order_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerPickupOrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pickup_order_id", nullable = false)
    private UUID pickupOrderId;

    @Column(nullable = false, length = 100)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(length = 100)
    private String actor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
