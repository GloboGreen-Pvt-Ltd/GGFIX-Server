package com.repairshop.saas.common.subscription;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything a client needs to know about what an account may do, in one
 * payload: the plan, its window, each metered allowance with live usage beside
 * it, and the on/off features.
 *
 * <p>This is the "single source of truth" object. The Subscription screen
 * renders it, the Employees screen renders it, and the APIs enforce against the
 * very same values — because {@link SubscriptionLimitService} builds this from
 * the identical calls the enforcement path uses. A screen cannot show an
 * allowance the API will not honour, because there is only one calculation.
 *
 * <p><b>Usage is per-shop for shop-scoped features.</b> Building this without a
 * shopId leaves the shop-scoped counters at zero usage — the ceilings are still
 * correct, but "3 of 3 used" needs to know which shop is being asked about.
 *
 * @param plan               plan code, or "NONE" for an account with no row
 * @param planName           display name ("Free Trial")
 * @param status             raw subscription status
 * @param startDate          start of the current period
 * @param endDate            when access lapses
 * @param expired            whether the plan has lapsed
 * @param daysRemaining      whole days left; negative clamped to 0
 * @param hasSubscription    whether a row exists at all
 * @param purchasedShopCount shops paid for (Basic); null on the trial
 * @param enforced           false when the kill switch is off, so a client
 *                           showing limits can say they are not being applied
 * @param limits             per-feature {limit, used, remaining, unlimited, scope, allowed}
 * @param features           on/off entitlements keyed by camelCase name
 */
public record Entitlements(
        String plan,
        String planName,
        String status,
        Instant startDate,
        Instant endDate,
        boolean expired,
        long daysRemaining,
        boolean hasSubscription,
        Integer purchasedShopCount,
        boolean enforced,
        Map<String, LimitUsage> limits,
        Map<String, Boolean> features
) {

    /**
     * One metered allowance with its live usage.
     *
     * @param limit     ceiling; null = unlimited (never a sentinel number)
     * @param used      current usage within {@code scope}
     * @param remaining headroom; null when unlimited
     * @param unlimited convenience flag so clients need not null-check `limit`
     * @param scope     PER_SHOP or PER_OWNER — what `used` was counted over
     * @param allowed   whether one more may be added right now
     */
    public record LimitUsage(
            Integer limit,
            long used,
            Integer remaining,
            boolean unlimited,
            String scope,
            boolean allowed
    ) {}

    /** Convenience for callers holding the payload rather than the service. */
    public boolean can(SubscriptionCapability capability) {
        return Boolean.TRUE.equals(features.get(capability.getKey()));
    }

    /** Whether one more of a metered feature is permitted. */
    public boolean can(SubscriptionFeature feature) {
        LimitUsage usage = limits.get(feature.name());
        return usage != null && usage.allowed();
    }

    static Map<String, Boolean> emptyFeatures() {
        return new LinkedHashMap<>();
    }
}
