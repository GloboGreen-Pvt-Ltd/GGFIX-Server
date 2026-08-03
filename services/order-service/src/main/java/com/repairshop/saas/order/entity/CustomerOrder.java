package com.repairshop.saas.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "order_number", nullable = false, unique = true, length = 60) private String orderNumber;
    @Column(name = "customer_user_id", nullable = false) private UUID customerUserId;
    @Column(name = "shop_id") private UUID shopId;
    @Column(name = "order_type", nullable = false, length = 50) private String orderType;
    @Column(name = "reference_id") private UUID referenceId;
    @Column(nullable = false, length = 50) private String status;
    @Column(name = "total_amount", precision = 12, scale = 2) private BigDecimal totalAmount;
    @Column(name = "payload_json", columnDefinition = "TEXT") private String payloadJson;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "PENDING";
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
