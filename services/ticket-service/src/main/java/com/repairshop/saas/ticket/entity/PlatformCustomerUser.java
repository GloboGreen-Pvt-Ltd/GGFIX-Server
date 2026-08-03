package com.repairshop.saas.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Read/write view of the platform-wide customer_users table (also owned by
 * auth-service / user-service). ticket-service now CREATES rows here when the
 * shop owner adds a new customer — the old per-shop `customers` table has been
 * collapsed into customer_users so there's a single source of truth.
 */
@Entity
@Table(name = "customer_users")
@Getter
@Setter
@NoArgsConstructor
public class PlatformCustomerUser {

    @Id
    @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name")
    private String fullName;

    private String email;

    private String mobile;

    @Column(name = "id_proof_url")
    private String idProofUrl;

    @Column(name = "is_active")
    private Boolean isActive;

    /** Dev OTP. Auth-service overrides this on real OTP flow; for shop-created
     *  customers we seed "123456" so the owner can hand the customer a working
     *  test login without going through the full sign-up form. */
    @Column(name = "otp_code")
    private String otpCode;
}
