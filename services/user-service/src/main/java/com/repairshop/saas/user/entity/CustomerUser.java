package com.repairshop.saas.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerUser implements Persistable<UUID> {

    // The id is the auth-service user id (assigned from the JWT), never
    // generated locally — this row mirrors the platform customer. Implementing
    // Persistable lets Spring Data treat a freshly-built stub as new and INSERT
    // it, instead of merge()-ing (which UPDATEs a non-existent row and fails
    // with an optimistic-locking error).
    @Id
    private UUID id;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "email", length = 255, unique = true)
    private String email;

    @Column(name = "mobile", length = 50, unique = true)
    private String mobile;

    @Column(name = "alternate_mobile", length = 50)
    private String alternateMobile;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "is_active")
    private Boolean isActive;

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

    // New until persisted: createdAt is only populated by @PrePersist, so a
    // freshly-built stub (createdAt == null) is new and a loaded row is not.
    @Override
    public boolean isNew() {
        return createdAt == null;
    }
}
