package com.repairshop.saas.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sell_order_quotations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"sell_order_id", "shop_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SellOrderQuotation {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "sell_order_id", nullable = false) private UUID sellOrderId;
    @Column(name = "shop_id", nullable = false) private UUID shopId;
    @Column(name = "shop_name", length = 255) private String shopName;
    @Column(name = "shop_phone", length = 50) private String shopPhone;
    @Column(name = "shop_city", length = 255) private String shopCity;
    @Column(name = "quotation_price", nullable = false, precision = 12, scale = 2) private BigDecimal quotationPrice;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(nullable = false, length = 50) private String status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "PROPOSED";
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
