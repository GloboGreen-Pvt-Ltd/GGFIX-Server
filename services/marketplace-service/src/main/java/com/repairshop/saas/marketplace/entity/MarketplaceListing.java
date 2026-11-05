package com.repairshop.saas.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A Buy/Sell board listing — published by either a CUSTOMER (mobile sell
 * flow) or a SHOP (owner-to-owner trade). The Buy screen filters these by
 * distance from the current shop's lat/lng.
 */
@Entity
@Table(name = "marketplace_listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketplaceListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seller_type", nullable = false, length = 20)
    private String sellerType;          // 'CUSTOMER' | 'SHOP'

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "shop_id")
    private UUID shopId;                // set when sellerType=SHOP

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "model_id")
    private UUID modelId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_image", length = 1000)
    private String productImage;

    /** "condition" is a reserved word in Postgres, hence the quoted name. */
    @Column(name = "\"condition\"", length = 40)
    private String condition;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "expected_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedPrice;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 120)
    private String city;

    @Column(length = 120)
    private String state;

    @Column(length = 20)
    private String pincode;

    @Column(nullable = false, length = 20)
    private String status;              // AVAILABLE | SOLD | CANCELLED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null || status.isBlank()) status = "AVAILABLE";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
