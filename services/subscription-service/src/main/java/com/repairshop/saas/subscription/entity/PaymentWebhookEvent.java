package com.repairshop.saas.subscription.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** A signature-verified Razorpay webhook delivery, deduped by event id (migration 106). */
@Entity
@Table(name = "payment_webhook_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", length = 60)
    private String eventType;

    @Column(name = "razorpay_order_id", length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private String payload;

    @Column(name = "processed", nullable = false)
    private Boolean processed;

    @Column(name = "processing_error", columnDefinition = "TEXT")
    private String processingError;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    void prePersist() {
        if (receivedAt == null) receivedAt = Instant.now();
        if (provider == null) provider = "RAZORPAY";
        if (processed == null) processed = false;
        if (attempts == null) attempts = 1;
    }
}
