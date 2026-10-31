package com.repairshop.saas.subscription.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    // ── Subscription v2 (migration 106; all nullable, null on legacy rows) ──

    /** subscription_plans.id of the plan this term was bought on. */
    @Column(name = "plan_id")
    private UUID planId;

    /** subscription_orders.id that produced the current term. */
    @Column(name = "order_id")
    private UUID orderId;

    /** The Plan JSON at purchase time; later plan edits never touch it. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_snapshot")
    private String planSnapshot;

    /** RAZORPAY | TRIAL | MANUAL. */
    @Column(name = "source", length = 20)
    private String source;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

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
