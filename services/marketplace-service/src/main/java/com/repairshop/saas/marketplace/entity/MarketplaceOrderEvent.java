package com.repairshop.saas.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Audit trail of a fixed-price Buy order (one row per status change). */
@Entity
@Table(name = "marketplace_order_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MarketplaceOrderEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(nullable = false, length = 50) private String status;
    @Column(name = "actor_user_id") private UUID actorUserId;
    @Column(name = "actor_role", length = 20) private String actorRole;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
