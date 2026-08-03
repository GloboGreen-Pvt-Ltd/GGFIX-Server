package com.repairshop.saas.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sell_order_issues")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SellOrderIssue {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "sell_order_id", nullable = false) private UUID sellOrderId;
    @Column(name = "issue_id") private UUID issueId;
    @Column(name = "issue_code", length = 100) private String issueCode;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
