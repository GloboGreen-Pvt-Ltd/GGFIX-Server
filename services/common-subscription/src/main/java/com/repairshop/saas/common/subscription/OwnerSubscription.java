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
 */
public record OwnerSubscription(
        UUID ownerUserId,
        SubscriptionPlan plan,
        String status,
        Instant startsAt,
        Instant endsAt,
        boolean expired,
        Integer purchasedShopCount,
        boolean present
) {

    /** No row for this owner — a legacy account created before subscriptions. */
    public static OwnerSubscription absent(UUID ownerUserId) {
        return new OwnerSubscription(ownerUserId, null, "NONE", null, null, false, null, false);
    }

    /** Plan code for wire payloads; "NONE" when nothing resolved. */
    public String planCode() {
        return plan != null ? plan.getCode() : "NONE";
    }

    /** Plan name for user-facing sentences; "current" reads acceptably inline
     *  in "Your current plan allows …" when no plan could be resolved. */
    public String planName() {
        return plan != null ? plan.getDisplayName() : "current";
    }

    /** Effective allowance for a metered feature under this subscription. */
    public Integer limitFor(SubscriptionFeature feature) {
        return plan != null ? plan.limitFor(feature, purchasedShopCount) : null;
    }

    /** Whether this subscription grants an on/off entitlement. A lapsed plan
     *  grants nothing — that is the whole point of an expiry. */
    public boolean grants(SubscriptionCapability capability) {
        return plan != null && !expired && plan.grants(capability, purchasedShopCount);
    }
}
