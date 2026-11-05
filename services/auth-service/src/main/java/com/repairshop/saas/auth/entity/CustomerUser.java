package com.repairshop.saas.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(length = 255, unique = true)
    private String email;

    @Column(length = 50, unique = true)
    private String mobile;

    @Column(name = "alternate_mobile", length = 50)
    private String alternateMobile;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "otp_code", length = 16)
    private String otpCode;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

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
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
