package com.repairshop.saas.subscription.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the admin-editable plan master (migration 106). Named "Master" to
 * keep it apart from the legacy {@code SubscriptionPlan} enum in
 * common-subscription, which still governs FREE_TRIAL / BASIC rows.
 * Money is integer paise; a null limit means unlimited.
 */
@Entity
@Table(name = "subscription_plans")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "plan_type", nullable = false, length = 30)
    private String planType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_paise", nullable = false)
    private Long pricePaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "duration_value", nullable = false)
    private Integer durationValue;

    @Column(name = "duration_unit", nullable = false, length = 10)
    private String durationUnit;

    @Column(name = "employee_limit")
    private Integer employeeLimit;

    @Column(name = "shop_limit")
    private Integer shopLimit;

    @Column(name = "sell_order_limit")
    private Integer sellOrderLimit;

    @Column(name = "buy_order_limit")
    private Integer buyOrderLimit;

    @Column(name = "service_booking_enabled", nullable = false)
    private Boolean serviceBookingEnabled;

    @Column(name = "pickup_enabled", nullable = false)
    private Boolean pickupEnabled;

    @Column(name = "sms_enabled", nullable = false)
    private Boolean smsEnabled;

    @Column(name = "whatsapp_enabled", nullable = false)
    private Boolean whatsappEnabled;

    @Column(name = "reports_enabled", nullable = false)
    private Boolean reportsEnabled;

    @Column(name = "export_enabled", nullable = false)
    private Boolean exportEnabled;

    @Column(name = "priority_support_enabled", nullable = false)
    private Boolean prioritySupportEnabled;

    @Column(name = "additional_shop_discount_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal additionalShopDiscountPct;

    /** ACTIVE | INACTIVE | COMING_SOON. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "icon", length = 60)
    private String icon;

    /** Bumped on every admin edit; copied into order/plan snapshots. */
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (version == null) version = 1;
        if (currency == null) currency = "INR";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
