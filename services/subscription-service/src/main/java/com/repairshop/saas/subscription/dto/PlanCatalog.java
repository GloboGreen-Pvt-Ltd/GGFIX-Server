package com.repairshop.saas.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Static catalog of the two plans returned by GET /subscriptions/plans.
 * Limits are informational (STORED but not enforced in this phase).
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
        private Integer employeeLimit;       // nullable = unlimited
        private Integer sellLimit;           // nullable = unlimited
        private boolean pickupServiceEnabled;
        private List<String> features;
    }

    public static List<Plan> all() {
        Plan freeTrial = Plan.builder()
                .code("FREE_TRIAL")
                .name("Free Trial")
                .durationDays(15)
                .price(BigDecimal.ZERO)
                .multiShopPrice(null)
                .shopLimit(2)
                .employeeLimit(3)
                .sellLimit(5)
                .pickupServiceEnabled(false)
                .features(List.of(
                        "New Service Booking",
                        "Up to 2 Shops",
                        "Buy Products — Unlimited",
                        "Sell Products — up to 5 orders",
                        "Up to 3 Employees per Shop"
                ))
                .build();

        Plan basic = Plan.builder()
                .code("BASIC")
                .name("Basic")
                .durationDays(365)
                .price(new BigDecimal("3000"))
                .multiShopPrice(new BigDecimal("2500"))
                .shopLimit(null)
                .employeeLimit(null)
                .sellLimit(null)
                .pickupServiceEnabled(true)
                .features(List.of(
                        "New Service Booking",
                        "Pickup Service",
                        "Buy Products — Unlimited",
                        "Sell Products — Unlimited",
                        "Unlimited Employees",
                        "Multiple Shops"
                ))
                .build();

        return List.of(freeTrial, basic);
    }
}
