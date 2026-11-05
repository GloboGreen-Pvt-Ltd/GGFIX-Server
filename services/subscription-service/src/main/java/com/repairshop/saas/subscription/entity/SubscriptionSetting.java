package com.repairshop.saas.subscription.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** One key/value row of subscription_settings (migration 106). */
@Entity
@Table(name = "subscription_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionSetting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
