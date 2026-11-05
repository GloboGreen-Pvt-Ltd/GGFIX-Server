package com.repairshop.saas.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_addresses", indexes = {
    @Index(name = "idx_customer_addresses_user", columnList = "customer_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_user_id", nullable = false)
    private UUID customerUserId;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "mobile", length = 50)
    private String mobile;

    @Column(name = "pincode", length = 20)
    private String pincode;

    @Column(name = "locality", length = 255)
    private String locality;

    @Column(name = "area", length = 255)
    private String area;

    @Column(name = "address_line", columnDefinition = "TEXT")
    private String addressLine;

    @Column(name = "city", length = 255)
    private String city;

    @Column(name = "district", length = 255)
    private String district;

    @Column(name = "taluk", length = 255)
    private String taluk;

    @Column(name = "state", length = 255)
    private String state;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (label == null) label = "Home";
        if (isDefault == null) isDefault = false;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
