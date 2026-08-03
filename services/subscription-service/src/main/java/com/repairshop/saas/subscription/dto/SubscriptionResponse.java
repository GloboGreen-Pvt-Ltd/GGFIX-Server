package com.repairshop.saas.subscription.dto;

import com.repairshop.saas.subscription.entity.Subscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Wire view of a Subscription row. Mirrors the entity fields plus a derived
 * {@code daysRemaining} = whole days between now and inactiveDate (0 when the
 * date is null or already in the past).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponse {

    private UUID id;
    private UUID ownerUserId;
    private UUID shopId;
    private String planCode;
    private String status;
    private String subscriptionType;
    private Instant trialStartDate;
    private Instant trialEndDate;
    private Instant subscriptionStartDate;
    private Instant subscriptionEndDate;
    private Instant activeDate;
    private Instant inactiveDate;
    private Integer shopLimit;
    private Integer employeeLimit;
    private Integer sellLimit;
    private Boolean pickupServiceEnabled;
    private Boolean buyProductUnlimited;
    private Boolean sellProductUnlimited;
    private Integer shopCount;
    private BigDecimal priceAmount;
    private Instant startedAt;
    private Instant currentPeriodEnd;
    private Instant createdAt;
    private Instant updatedAt;

    /** Derived: whole days between now and inactiveDate; 0 if null or past. */
    private int daysRemaining;

    public static SubscriptionResponse from(Subscription s) {
        if (s == null) return null;
        return SubscriptionResponse.builder()
                .id(s.getId())
                .ownerUserId(s.getOwnerUserId())
                .shopId(s.getShopId())
                .planCode(s.getPlanCode())
                .status(s.getStatus())
                .subscriptionType(s.getSubscriptionType())
                .trialStartDate(s.getTrialStartDate())
                .trialEndDate(s.getTrialEndDate())
                .subscriptionStartDate(s.getSubscriptionStartDate())
                .subscriptionEndDate(s.getSubscriptionEndDate())
                .activeDate(s.getActiveDate())
                .inactiveDate(s.getInactiveDate())
                .shopLimit(s.getShopLimit())
                .employeeLimit(s.getEmployeeLimit())
                .sellLimit(s.getSellLimit())
                .pickupServiceEnabled(s.getPickupServiceEnabled())
                .buyProductUnlimited(s.getBuyProductUnlimited())
                .sellProductUnlimited(s.getSellProductUnlimited())
                .shopCount(s.getShopCount())
                .priceAmount(s.getPriceAmount())
                .startedAt(s.getStartedAt())
                .currentPeriodEnd(s.getCurrentPeriodEnd())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .daysRemaining(daysRemaining(s.getInactiveDate()))
                .build();
    }

    private static int daysRemaining(Instant inactiveDate) {
        if (inactiveDate == null) return 0;
        long days = ChronoUnit.DAYS.between(Instant.now(), inactiveDate);
        return days > 0 ? (int) days : 0;
    }
}
