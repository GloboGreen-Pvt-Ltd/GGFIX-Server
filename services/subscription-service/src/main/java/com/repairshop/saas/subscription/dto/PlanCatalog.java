package com.repairshop.saas.subscription.dto;

import com.repairshop.saas.common.subscription.SubscriptionFeature;
import com.repairshop.saas.common.subscription.SubscriptionPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Wire shape for GET /subscriptions/plans.
 *
 * <p>This used to hold the plan numbers itself. It is now a projection of
 * {@link SubscriptionPlan}: the limits it advertises are read from the same
 * enum the create/activate APIs enforce against, so the Subscription screen
 * cannot promise an allowance the Employees screen refuses to honour.
 *
 * <p>{@code employeeLimit} is per shop — see {@link SubscriptionFeature}.
 */
public final class PlanCatalog {

    private PlanCatalog() {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Plan {
        private String code;
        private String name;
        private int durationDays;
        private BigDecimal price;
        private BigDecimal multiShopPrice;   // nullable: per-shop price at 2+ shops
        private Integer shopLimit;           // nullable = unlimited
        private Integer employeeLimit;       // nullable = unlimited; PER SHOP
        private Integer sellLimit;           // nullable = unlimited
        private boolean pickupServiceEnabled;
        private List<String> features;
    }

    public static List<Plan> all() {
        return Arrays.stream(SubscriptionPlan.values())
                .map(PlanCatalog::toDto)
                .toList();
    }

    private static Plan toDto(SubscriptionPlan plan) {
        return Plan.builder()
                .code(plan.getCode())
                .name(plan.getDisplayName())
                .durationDays(plan.getDurationDays())
                .price(plan.getPrice())
                .multiShopPrice(plan.getMultiShopPrice())
                .shopLimit(plan.limitFor(SubscriptionFeature.SHOPS))
                .employeeLimit(plan.limitFor(SubscriptionFeature.EMPLOYEES))
                .sellLimit(plan.limitFor(SubscriptionFeature.SELL_ORDERS))
                .pickupServiceEnabled(plan.isPickupServiceEnabled())
                .features(plan.getFeatures())
                .build();
    }
}
