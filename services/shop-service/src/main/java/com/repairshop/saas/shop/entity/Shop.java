package com.repairshop.saas.shop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(length = 255)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 50)
    private String timezone;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 255)
    private String city;

    @Column(length = 255)
    private String state;

    @Column(length = 20)
    private String pincode;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    /**
     * The shop's master pickup switch (migration 14). auth-service owns this
     * column — it is the only service that writes it, via the owner's location
     * PATCH — so it is mapped read-only here. insertable=false keeps
     * shop-service's own createShop out of a NOT NULL violation: the column
     * would otherwise be INSERTed as NULL instead of falling back to the
     * DEFAULT FALSE that makes a new shop pickup-off until its owner opts in.
     */
    @Column(name = "pickup_enabled", insertable = false, updatable = false)
    private Boolean pickupEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (isActive == null) isActive = Boolean.TRUE;
        if (timezone == null) timezone = "Asia/Kolkata";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
