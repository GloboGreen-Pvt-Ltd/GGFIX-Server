package com.repairshop.saas.common.subscription;

import java.time.Instant;
import java.util.UUID;

/**
 * A read-only snapshot of one owner's subscription, resolved from the
 * `subscriptions` table and already reduced to what enforcement cares about:
 * which {@link SubscriptionPlan} applies, whether it is still in date, and how
 * many shops were paid for.
 *
 * @param ownerUserId        the SHOP_OWNER user id the subscription is keyed by
 * @param plan               resolved plan, or null when the row is missing/unknown
 * @param status             raw status column (FREE_TRIAL / ACTIVE / EXPIRED / CANCELLED)
 * @param startsAt           when the current period began
 * @param endsAt             when access lapses; null when the row carries no end date
 * @param expired            true when cancelled, marked expired, or past {@code endsAt}
 * @param purchasedShopCount shops paid for on Basic; null on the trial, where
 *                           the allowance is a plan constant rather than a purchase
 * @param present            whether a subscription row was found at all
 * @param storedPlan         a Subscription v2 plan whose limits are stored on the
 *                           row; null for the enum plans (FREE_TRIAL / BASIC) and
 *                           for missing/unknown rows. Only set when {@code plan} is null.
 */
public record OwnerSubscription(
        UUID ownerUserId,
        SubscriptionPlan plan,
        String status,
        Instant startsAt,
        Instant endsAt,
        boolean expired,
        Integer purchasedShopCount,
        boolean present,
        StoredPlan storedPlan
) {

    /** Enum-plan form (FREE_TRIAL / BASIC), unchanged from before v2. */
    public OwnerSubscription(UUID ownerUserId, SubscriptionPlan plan, String status, Instant startsAt,
                             Instant endsAt, boolean expired, Integer purchasedShopCount, boolean present) {
        this(ownerUserId, plan, status, startsAt, endsAt, expired, purchasedShopCount, present, null);
    }

    /** No row for this owner — a legacy account created before subscriptions. */
    public static OwnerSubscription absent(UUID ownerUserId) {
        return new OwnerSubscription(ownerUserId, null, "NONE", null, null, false, null, false);
    }

    /** Whether a plan (enum or stored v2) could be resolved for this row. */
    public boolean resolved() {
        return plan != null || storedPlan != null;
    }

    /** Plan code for wire payloads; "NONE" when nothing resolved. */
    public String planCode() {
        if (plan != null) return plan.getCode();
        return storedPlan != null ? storedPlan.code() : "NONE";
    }

    /** Plan name for user-facing sentences; "current" reads acceptably inline
     *  in "Your current plan allows …" when no plan could be resolved. */
    public String planName() {
        if (plan != null) return plan.getDisplayName();
        return storedPlan != null ? storedPlan.displayName() : "current";
    }

    /** Effective allowance for a metered feature under this subscription. */
    public Integer limitFor(SubscriptionFeature feature) {
        if (plan != null) return plan.limitFor(feature, purchasedShopCount);
        return storedPlan != null ? storedPlan.limitFor(feature) : null;
    }

    /** Whether this subscription grants an on/off entitlement. A lapsed plan
     *  grants nothing — that is the whole point of an expiry. */
    public boolean grants(SubscriptionCapability capability) {
        if (expired) return false;
        if (plan != null) return plan.grants(capability, purchasedShopCount);
        return storedPlan != null && storedPlan.grants(capability);
    }
}
