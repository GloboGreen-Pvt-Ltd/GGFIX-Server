package com.repairshop.saas.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * OTP issued to a mobile number that has no customer_users row yet, so the
 * Create Account flow can prove the caller controls the number BEFORE the
 * account exists. See migration 95 for why this is not a column on
 * customer_users. Keyed by mobile: one live code per number, replaced on resend
 * and deleted once /auth/customer-register consumes it.
 */
@Entity
@Table(name = "customer_signup_otps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSignupOtp {

    @Id
    @Column(name = "mobile", length = 50, nullable = false)
    private String mobile;

    @Column(name = "otp_code", length = 16, nullable = false)
    private String otpCode;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (attempts == null) attempts = 0;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
