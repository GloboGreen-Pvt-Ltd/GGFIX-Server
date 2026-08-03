package com.repairshop.saas.subscription.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps the shared `subscriptions` table (01_schema.sql + migration 66).
 * The base columns (shop_id / plan_code / status / started_at /
 * current_period_end / created_at / updated_at) are reused as-is; the
 * migration-66 columns carry the richer plan/type + trial window + STORED
 * (not enforced) limits.
 */
@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "shop_id")
    private UUID shopId;

    @Column(name = "plan_code", length = 50)
    private String planCode;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "subscription_type", length = 30)
    private String subscriptionType;

    @Column(name = "trial_start_date")
    private Instant trialStartDate;

    @Column(name = "trial_end_date")
    private Instant trialEndDate;

    @Column(name = "subscription_start_date")
    private Instant subscriptionStartDate;

    @Column(name = "subscription_end_date")
    private Instant subscriptionEndDate;

    @Column(name = "active_date")
    private Instant activeDate;

    @Column(name = "inactive_date")
    private Instant inactiveDate;

    @Column(name = "shop_limit")
    private Integer shopLimit;

    @Column(name = "employee_limit")
    private Integer employeeLimit;

    @Column(name = "sell_limit")
    private Integer sellLimit;

    @Column(name = "pickup_service_enabled")
    private Boolean pickupServiceEnabled;

    @Column(name = "buy_product_unlimited")
    private Boolean buyProductUnlimited;

    @Column(name = "sell_product_unlimited")
    private Boolean sellProductUnlimited;

    @Column(name = "shop_count")
    private Integer shopCount;

    @Column(name = "price_amount", precision = 10, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (startedAt == null) startedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
