package com.repairshop.saas.subscription.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** A captured Razorpay payment for a subscription order (migration 106). */
@Entity
@Table(name = "subscription_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPayment {

    public static final String CAPTURED = "CAPTURED";
    public static final String PARTIALLY_REFUNDED = "PARTIALLY_REFUNDED";
    public static final String REFUNDED = "REFUNDED";

    public static final String ACTIVATION_ACTIVATED = "ACTIVATED";
    public static final String ACTIVATION_DUPLICATE = "DUPLICATE_PAYMENT";
    public static final String ACTIVATION_NEEDS_REVIEW = "NEEDS_REVIEW";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "razorpay_order_id", length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", nullable = false, length = 64)
    private String razorpayPaymentId;

    @Column(name = "receipt_no", nullable = false, length = 40)
    private String receiptNo;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** TEST | LIVE — the Razorpay mode the payment was made in (= its order's mode). */
    @Column(name = "payment_mode", length = 10, updatable = false)
    private String paymentMode;

    @Column(name = "method", length = 30)
    private String method;

    @Column(name = "vpa", length = 120)
    private String vpa;

    @Column(name = "payer_email")
    private String payerEmail;

    @Column(name = "payer_contact", length = 50)
    private String payerContact;

    /** CAPTURED | PARTIALLY_REFUNDED | REFUNDED. */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    /** ACTIVATED | DUPLICATE_PAYMENT | NEEDS_REVIEW. */
    @Column(name = "activation_status", nullable = false, length = 30)
    private String activationStatus;

    @Column(name = "refunded_paise", nullable = false)
    private Long refundedPaise;

    @Column(name = "last_refund_id", length = 64)
    private String lastRefundId;

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    private String refundReason;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (refundedPaise == null) refundedPaise = 0L;
        if (currency == null) currency = "INR";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
