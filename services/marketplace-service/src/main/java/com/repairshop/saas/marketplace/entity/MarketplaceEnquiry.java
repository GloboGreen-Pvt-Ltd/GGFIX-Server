package com.repairshop.saas.marketplace.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Buyer <-> shop enquiry / purchase request on a marketplace listing
 * (migration 106_marketplace_sell_module.sql). Separate from the customer ->
 * shop sell-order quotations in order-service.
 */
@Entity
@Table(name = "marketplace_enquiries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketplaceEnquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "shop_id", nullable = false)
    private UUID shopId;

    @Column(name = "buyer_user_id", nullable = false)
    private UUID buyerUserId;

    @Column(nullable = false, length = 20)
    private String kind; // ENQUIRY | PURCHASE_REQUEST

    @Column(nullable = false, length = 20)
    private String status; // OPEN | QUOTED | COUNTERED | ACCEPTED | CONFIRMED | REJECTED | CANCELLED

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "last_offer_amount", precision = 12, scale = 2)
    private BigDecimal lastOfferAmount;

    @Column(name = "last_offer_by", length = 10)
    private String lastOfferBy; // SHOP | BUYER

    @Column(name = "agreed_amount", precision = 12, scale = 2)
    private BigDecimal agreedAmount;

    @Column(length = 20)
    private String fulfilment; // PICKUP | SHIPPING

    @Column(name = "buyer_name", length = 255)
    private String buyerName;

    @Column(name = "buyer_phone", length = 20)
    private String buyerPhone;

    @Column(name = "shop_unread", nullable = false)
    private Boolean shopUnread;

    @Column(name = "buyer_unread", nullable = false)
    private Boolean buyerUnread;

    @Column(name = "closed_reason", columnDefinition = "TEXT")
    private String closedReason;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (quantity == null) quantity = 1;
        if (shopUnread == null) shopUnread = true;
        if (buyerUnread == null) buyerUnread = false;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
