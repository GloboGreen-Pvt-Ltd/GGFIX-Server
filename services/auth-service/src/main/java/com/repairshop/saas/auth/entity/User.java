package com.repairshop.saas.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /**
     * Owner KYC documents (Aadhar front/back + PAN) as a jsonb blob. NULL until
     * the owner or an admin uploads their first document. columnDefinition is
     * intentionally omitted so the H2 dev profile doesn't choke on "jsonb"
     * (mirrors MasterModel's jsonb columns).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kyc_document")
    private KycDocument kycDocument;

    @Column(name = "personal_address", columnDefinition = "TEXT")
    private String personalAddress;

    @Column(nullable = false, length = 50)
    private String role; // SHOP_OWNER, TECHNICIAN, SUPER_ADMIN, MARKET_PERSON

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // ---- Creator: immutable history of who made this account (migration 92) --
    // All three are updatable = false. They are stamped once, server-side, from
    // the authenticated creator, and no API accepts them from a client.

    /** ROLE of the creator — ADMIN or MARKET_PERSON. NULL for self-registered/legacy rows. */
    @Column(name = "created_by", length = 50, updatable = false)
    private String createdBy;

    /** users.id of the creator. NULL for self-registered/legacy rows. */
    @Column(name = "created_person_id", updatable = false)
    private UUID createdPersonId;

    /** Creator's name as it read at creation time. */
    @Column(name = "created_person_name", updatable = false)
    private String createdPersonName;

    // ---- Active person: who currently owns this account's relationship -------
    // Reassignable by an admin, unlike the creator trio. Set only from a real
    // user row looked up server-side.

    /** Role of the currently assigned person — MARKET_PERSON in practice. */
    @Column(name = "active_role", length = 50)
    private String activeRole;

    @Column(name = "active_person_id")
    private UUID activePersonId;

    @Column(name = "active_person_name")
    private String activePersonName;

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
