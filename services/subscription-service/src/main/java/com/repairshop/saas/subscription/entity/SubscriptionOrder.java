package com.repairshop.saas.subscription.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A purchase attempt (migration 106). The quote/plan/shops snapshots and the
 * amount are written once at creation and never updated ({@code updatable =
 * false}, plus a DB trigger) — what the owner agreed to pay is what gets
 * checked against Razorpay.
 */
@Entity
@Table(name = "subscription_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionOrder {

    public static final String CREATED = "CREATED";
    public static final String AWAITING_PAYMENT = "AWAITING_PAYMENT";
    public static final String PAID = "PAID";
    public static final String ACTIVATED = "ACTIVATED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_no", nullable = false, length = 40, updatable = false)
    private String orderNo;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "plan_id", updatable = false)
    private UUID planId;

    @Column(name = "plan_code", nullable = false, length = 50, updatable = false)
    private String planCode;

    @Column(name = "plan_name", length = 120, updatable = false)
    private String planName;

    @Column(name = "duration_label", length = 40, updatable = false)
    private String durationLabel;

    /** NEW | RENEW | UPGRADE | TRIAL. */
    @Column(name = "purpose", nullable = false, length = 20, updatable = false)
    private String purpose;

    /** WEB | ANDROID | IOS | ADMIN. */
    @Column(name = "channel", nullable = false, length = 20, updatable = false)
    private String channel;

    /** RAZORPAY | TRIAL | MANUAL. */
    @Column(name = "source", nullable = false, length = 20, updatable = false)
    private String source;

    @Column(name = "shop_count", nullable = false, updatable = false)
    private Integer shopCount;

    /** [{shopId, name}] at purchase time. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shops_snapshot", nullable = false, updatable = false)
    private String shopsSnapshot;

    /** The Quote JSON the owner accepted. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quote_snapshot", nullable = false, updatable = false)
    private String quoteSnapshot;

    /** The Plan JSON at purchase time. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_snapshot", nullable = false, updatable = false)
    private String planSnapshot;

    @Column(name = "total_paise", nullable = false, updatable = false)
    private Long totalPaise;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    /** TEST | LIVE — the server's RAZORPAY_MODE when the order was created. Never changes. */
    @Column(name = "payment_mode", length = 10, updatable = false)
    private String paymentMode;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "razorpay_order_id", length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "payment_attempts", nullable = false)
    private Integer paymentAttempts;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /** Cancel reason / admin manual-activation reason. */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /** The term actually granted, set at activation. */
    @Column(name = "term_starts_at")
    private Instant termStartsAt;

    @Column(name = "term_ends_at")
    private Instant termEndsAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isPending() {
        return CREATED.equals(status) || AWAITING_PAYMENT.equals(status);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (paymentAttempts == null) paymentAttempts = 0;
        if (currency == null) currency = "INR";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
