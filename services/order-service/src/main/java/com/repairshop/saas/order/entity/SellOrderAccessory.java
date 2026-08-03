package com.repairshop.saas.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sell_order_accessories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SellOrderAccessory {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "sell_order_id", nullable = false) private UUID sellOrderId;
    @Column(name = "accessory_id") private UUID accessoryId;
    @Column(name = "accessory_code", length = 100) private String accessoryCode;
    @Column(length = 200) private String label;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
