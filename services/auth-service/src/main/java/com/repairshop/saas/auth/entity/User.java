package com.repairshop.saas.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "shop_id", "email" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "otp_code", length = 16)
    private String otpCode;

    @Column(length = 255)
    private String name;

    @Column(length = 50)
    private String phone;

    @Column(name = "secondary_mobile", length = 50)
    private String secondaryMobile;

    @Column(name = "addr_state", length = 120)
    private String addrState;

    @Column(name = "addr_district", length = 120)
    private String addrDistrict;

    @Column(name = "addr_taluk", length = 120)
    private String addrTaluk;

    @Column(name = "addr_area", length = 160)
    private String addrArea;

    @Column(name = "addr_street", length = 200)
    private String addrStreet;

    @Column(name = "addr_pincode", length = 20)
    private String addrPincode;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(name = "id_proof_url", length = 1000)
    private String idProofUrl;

    @Column(name = "personal_address", columnDefinition = "TEXT")
    private String personalAddress;

    @Column(nullable = false, length = 50)
    private String role; // SHOP_OWNER, TECHNICIAN, SUPER_ADMIN

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (isActive == null) isActive = true;
        if (emailVerified == null) emailVerified = false;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
