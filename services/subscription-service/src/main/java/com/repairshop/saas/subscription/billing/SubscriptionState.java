package com.repairshop.saas.subscription.billing;

import com.repairshop.saas.subscription.entity.Subscription;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * How a `subscriptions` row reads for v2 (/me, quotes, admin). The end date
 * resolves in the same order as common-subscription's limit engine
 * (inactive_date → subscription_end_date → trial_end_date), so the screen and
 * the enforcement can never disagree on whether a plan is still running.
 */
public final class SubscriptionState {

    public static final String NONE = "NONE";
    public static final String TRIAL = "TRIAL";
    public static final String ACTIVE = "ACTIVE";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELLED = "CANCELLED";

    /** Value the legacy rows (auth-service, migration 92) use for a running trial. */
    public static final String DB_TRIAL_STATUS = "FREE_TRIAL";

    private SubscriptionState() {}

    public static boolean isTrial(Subscription s) {
        if (s == null) return false;
        String code = upper(s.getPlanCode());
        String status = upper(s.getStatus());
        return "FREE_TRIAL".equals(code) || code.startsWith("TRIAL")
                || DB_TRIAL_STATUS.equals(status) || TRIAL.equals(status);
    }

    public static Instant endsAt(Subscription s) {
        if (s == null) return null;
        if (s.getInactiveDate() != null) return s.getInactiveDate();
        if (s.getSubscriptionEndDate() != null) return s.getSubscriptionEndDate();
        if (s.getTrialEndDate() != null) return s.getTrialEndDate();
        return s.getCurrentPeriodEnd();
    }

    public static Instant startedAt(Subscription s) {
        if (s == null) return null;
        Instant primary = isTrial(s) ? s.getTrialStartDate() : s.getSubscriptionStartDate();
        if (primary != null) return primary;
        if (s.getActiveDate() != null) return s.getActiveDate();
        return s.getStartedAt();
    }

    /** NONE | TRIAL | ACTIVE | EXPIRED | CANCELLED. */
    public static String status(Subscription s, Instant now) {
        if (s == null) return NONE;
        String raw = upper(s.getStatus());
        if (CANCELLED.equals(raw)) return CANCELLED;
        Instant end = endsAt(s);
        if (EXPIRED.equals(raw) || (end != null && !end.isAfter(now))) return EXPIRED;
        return isTrial(s) ? TRIAL : ACTIVE;
    }

    public static boolean isRunning(String status) {
        return ACTIVE.equals(status) || TRIAL.equals(status);
    }

    public static long daysRemaining(Instant end, Instant now) {
        if (end == null || !end.isAfter(now)) return 0;
        return ChronoUnit.DAYS.between(now, end);
    }

    private static String upper(String v) {
        return v == null ? "" : v.trim().toUpperCase();
    }
}
