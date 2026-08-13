package com.repairshop.saas.common.subscription;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * THE plan configuration. Every limit and entitlement in the product is defined
 * here once and read from here everywhere: the plan cards on the Subscription
 * screen, the "3/3" counter on the Employees screen, and the create APIs that
 * reject the fourth employee all resolve through this enum.
 *
 * <p><b>Null means unlimited.</b> Not -1, not 999999 — a null limit is skipped
 * by {@link SubscriptionLimitService} without ever being compared, so an
 * unlimited plan cannot be accidentally capped by an off-by-one, and no
 * sentinel can leak into the UI as a literal "999999".
 *
 * <p>Note the limits here are authoritative over the per-row {@code shop_limit}
 * / {@code employee_limit} / {@code sell_limit} columns on the subscriptions
 * table. Those columns are a snapshot written when the row was created; rows
 * backfilled for legacy owners carry whatever the backfill script guessed, and
 * a row written before a plan changed carries the old allowance. Resolving from
 * the plan code instead means the advertised plan and the enforced plan cannot
 * drift. The columns are kept for admin display and billing history.
 *
 * <p>The one allowance that is NOT static is BASIC's shop limit: Basic is sold
 * per shop, so the ceiling is whatever the owner paid for. See
 * {@link #limitFor(SubscriptionFeature, Integer)}.
 */
public enum SubscriptionPlan {

    FREE_TRIAL(
            "Free Trial",
            15,
            BigDecimal.ZERO,
            null,
            /* shops             */ 2,
            /* employeesPerShop  */ 3,
            /* sellOrders        */ 5,
            /* buyProducts       */ null,
            Set.of(SubscriptionCapability.NEW_SERVICE_BOOKING,
                   SubscriptionCapability.PICKUP_SERVICE,
                   SubscriptionCapability.BUY_PRODUCTS,
                   SubscriptionCapability.SELL_PRODUCTS),
            List.of(
                    "New Service Booking",
                    "Up to 2 Shops",
                    "Buy Products — Unlimited",
                    "Sell Products — up to 5 orders",
                    "Up to 3 Employees per Shop",
                    "Pickup Service"
            )),

    BASIC(
            "Basic",
            365,
            new BigDecimal("3000"),
            new BigDecimal("2500"),
            /* shops             */ null,   // overridden by purchasedShopCount
            /* employeesPerShop  */ null,
            /* sellOrders        */ null,
            /* buyProducts       */ null,
            Set.of(SubscriptionCapability.NEW_SERVICE_BOOKING,
                   SubscriptionCapability.PICKUP_SERVICE,
                   SubscriptionCapability.BUY_PRODUCTS,
                   SubscriptionCapability.SELL_PRODUCTS),
            List.of(
                    "New Service Booking",
                    "Pickup Service",
                    "Buy Products — Unlimited",
                    "Sell Products — Unlimited",
                    "Unlimited Employees",
                    "Multiple Shops"
            ));

    private final String displayName;
    private final int durationDays;
    private final BigDecimal price;
    private final BigDecimal multiShopPrice;
    private final Integer shopLimit;
    private final Integer employeeLimitPerShop;
    private final Integer sellOrderLimit;
    private final Integer buyProductLimit;
    private final Set<SubscriptionCapability> capabilities;
    private final List<String> features;

    SubscriptionPlan(String displayName, int durationDays, BigDecimal price, BigDecimal multiShopPrice,
                     Integer shopLimit, Integer employeeLimitPerShop, Integer sellOrderLimit,
                     Integer buyProductLimit, Set<SubscriptionCapability> capabilities, List<String> features) {
        this.displayName = displayName;
        this.durationDays = durationDays;
        this.price = price;
        this.multiShopPrice = multiShopPrice;
        this.shopLimit = shopLimit;
        this.employeeLimitPerShop = employeeLimitPerShop;
        this.sellOrderLimit = sellOrderLimit;
        this.buyProductLimit = buyProductLimit;
        this.capabilities = capabilities;
        this.features = features;
    }

    /** Static allowance for a feature; null = unlimited. */
    public Integer limitFor(SubscriptionFeature feature) {
        return switch (feature) {
            case SHOPS -> shopLimit;
            case EMPLOYEES -> employeeLimitPerShop;
            case SELL_ORDERS -> sellOrderLimit;
            case BUY_PRODUCTS -> buyProductLimit;
        };
    }

    /**
     * Allowance for a feature on a specific subscription.
     *
     * <p>Basic is priced per shop (₹3,000 for one, ₹2,500 each from two), so
     * its shop ceiling is what the owner actually bought, not infinity. Every
     * other allowance is a property of the plan alone.
     *
     * <p>A Basic row with no recorded shop count falls back to unlimited rather
     * than to zero or one: the count is a billing detail, and a missing billing
     * detail must not retroactively lock a paying owner out of shops they are
     * already running.
     */
    public Integer limitFor(SubscriptionFeature feature, Integer purchasedShopCount) {
        if (this == BASIC && feature == SubscriptionFeature.SHOPS) {
            return (purchasedShopCount != null && purchasedShopCount > 0) ? purchasedShopCount : null;
        }
        return limitFor(feature);
    }

    /**
     * Whether this plan grants an on/off entitlement.
     *
     * <p>MULTIPLE_SHOPS is derived from the shop allowance instead of being
     * declared, so a plan cannot advertise two shops and deny multi-shop in the
     * same breath.
     */
    public boolean grants(SubscriptionCapability capability, Integer purchasedShopCount) {
        if (capability == SubscriptionCapability.MULTIPLE_SHOPS) {
            Integer shops = limitFor(SubscriptionFeature.SHOPS, purchasedShopCount);
            return shops == null || shops > 1;
        }
        return capabilities.contains(capability);
    }

    public boolean isUnlimited(SubscriptionFeature feature) {
        return limitFor(feature) == null;
    }

    /**
     * Resolve a plan from whatever the subscriptions row carries. Accepts both
     * {@code subscription_type} and {@code plan_code} spellings and tolerates
     * case/spacing drift ("free trial", "Free_Trial"). Returns null for an
     * unknown or absent code — callers treat that as "no plan resolved" and
     * fail open rather than guessing an allowance.
     */
    public static SubscriptionPlan from(String raw) {
        if (raw == null) return null;
        String normalised = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if (normalised.isEmpty()) return null;
        for (SubscriptionPlan plan : values()) {
            if (plan.name().equals(normalised)) return plan;
        }
        // "TRIAL" and "FREE" both show up in older rows and admin tooling.
        if (normalised.startsWith("TRIAL") || normalised.startsWith("FREE")) return FREE_TRIAL;
        return null;
    }

    public String getCode() {
        return name();
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public BigDecimal getPrice() {
        return price;
    }

    /** Per-shop price once the owner buys 2+ shops; null when not applicable. */
    public BigDecimal getMultiShopPrice() {
        return multiShopPrice;
    }

    public Integer getShopLimit() {
        return shopLimit;
    }

    public Integer getEmployeeLimitPerShop() {
        return employeeLimitPerShop;
    }

    public Integer getSellOrderLimit() {
        return sellOrderLimit;
    }

    public boolean isPickupServiceEnabled() {
        return capabilities.contains(SubscriptionCapability.PICKUP_SERVICE);
    }

    public List<String> getFeatures() {
        return features;
    }
}
