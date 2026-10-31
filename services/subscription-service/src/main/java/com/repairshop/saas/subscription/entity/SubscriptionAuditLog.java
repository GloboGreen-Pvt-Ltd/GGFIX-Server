package com.repairshop.saas.subscription.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit trail for every subscription write (migration 106). */
@Entity
@Table(name = "subscription_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_role", length = 50, updatable = false)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 60, updatable = false)
    private String action;

    @Column(name = "entity_type", length = 40, updatable = false)
    private String entityType;

    @Column(name = "entity_id", length = 64, updatable = false)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", updatable = false)
    private String details;

    @PrePersist
    void prePersist() {
        if (at == null) at = Instant.now();
    }
}
