package com.repairshop.saas.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sell_order_conditions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SellOrderCondition {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "sell_order_id", nullable = false) private UUID sellOrderId;
    @Column(name = "group_code", nullable = false, length = 100) private String groupCode;
    @Column(name = "group_name", length = 200) private String groupName;
    @Column(name = "option_id") private UUID optionId;
    @Column(name = "option_label", length = 255) private String optionLabel;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
