package com.repairshop.saas.common.subscription;

/**
 * The answer to "may this account do one more of X right now?".
 *
 * <p>This is the contract shared by the enforcement path and the UI path: the
 * create API throws when {@link #allowed()} is false, and the Employees screen
 * renders {@link #currentUsage()}/{@link #limit()} from the very same object.
 * They cannot disagree because there is only one calculation.
 *
 * @param allowed      whether one more unit may be added
 * @param currentUsage units already in use within the feature's scope
 * @param limit        the allowance; null = unlimited
 * @param remaining    units left; null = unlimited
 * @param plan         resolved plan code, or "NONE" when the owner has no row
 * @param planName     human-readable plan name for messages ("Free Trial")
 * @param feature      which allowance was evaluated
 * @param unlimited    convenience flag so clients need not null-check `limit`
 * @param expired      whether the subscription is past its end date
 * @param status       raw subscription status (ACTIVE / FREE_TRIAL / EXPIRED …)
 * @param reason       machine-readable outcome; see the REASON_* constants
 * @param message      user-facing sentence, already phrased for display
 */
public record LimitCheck(
        boolean allowed,
        long currentUsage,
        Integer limit,
        Integer remaining,
        String plan,
        String planName,
        String feature,
        boolean unlimited,
        boolean expired,
        String status,
        String reason,
        String message
) {
    public static final String REASON_OK = "OK";
    public static final String REASON_LIMIT_REACHED = "LIMIT_REACHED";
    public static final String REASON_SUBSCRIPTION_EXPIRED = "SUBSCRIPTION_EXPIRED";
    /** An on/off capability the plan does not include (see SubscriptionCapability). */
    public static final String REASON_FEATURE_UNAVAILABLE = "FEATURE_UNAVAILABLE";
    /** Owner has no subscription row at all — legacy account; fails open. */
    public static final String REASON_NO_SUBSCRIPTION = "NO_SUBSCRIPTION";
    /** Enforcement switched off by configuration; fails open. */
    public static final String REASON_ENFORCEMENT_DISABLED = "ENFORCEMENT_DISABLED";

    /** An allow with no ceiling — unlimited plans, and every fail-open path. */
    static LimitCheck unlimited(long usage, OwnerSubscription sub, SubscriptionFeature feature, String reason) {
        return new LimitCheck(
                true, usage, null, null,
                sub.planCode(), sub.planName(), feature.name(),
                true, sub.expired(), sub.status(), reason, null);
    }
}
