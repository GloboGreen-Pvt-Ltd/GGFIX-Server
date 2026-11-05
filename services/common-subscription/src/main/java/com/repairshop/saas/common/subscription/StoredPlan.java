package com.repairshop.saas.common.subscription;

/**
 * A Subscription v2 plan (BASIC_6M, PREMIUM_12M, …) whose allowances live on
 * the owner's {@code subscriptions} row rather than in {@link SubscriptionPlan}.
 *
 * <p>v2 plans are admin-editable rows in {@code subscription_plans}; activation
 * copies the purchased limits onto the subscription row, so enforcement reads
 * those columns and a later price/limit edit never changes what an existing
 * customer already paid for. A null limit means unlimited.
 *
 * @param code                  plan code as stored on the row
 * @param displayName           plan name from the master (falls back to a prettified code)
 * @param shopLimit             subscriptions.shop_limit (shops purchased)
 * @param employeeLimitPerShop  subscriptions.employee_limit
 * @param sellOrderLimit        subscriptions.sell_limit
 * @param pickupEnabled         subscriptions.pickup_service_enabled
 * @param serviceBookingEnabled subscription_plans.service_booking_enabled
 */
public record StoredPlan(
        String code,
        String displayName,
        Integer shopLimit,
        Integer employeeLimitPerShop,
        Integer sellOrderLimit,
        boolean pickupEnabled,
        boolean serviceBookingEnabled
) {

    public Integer limitFor(SubscriptionFeature feature) {
        return switch (feature) {
            case SHOPS -> shopLimit;
            case EMPLOYEES -> employeeLimitPerShop;
            case SELL_ORDERS -> sellOrderLimit;
            case BUY_PRODUCTS -> null;   // unlimited on every v2 plan
        };
    }

    public boolean grants(SubscriptionCapability capability) {
        return switch (capability) {
            case NEW_SERVICE_BOOKING -> serviceBookingEnabled;
            case PICKUP_SERVICE -> pickupEnabled;
            case BUY_PRODUCTS -> true;
            case SELL_PRODUCTS -> sellOrderLimit == null || sellOrderLimit > 0;
            case MULTIPLE_SHOPS -> shopLimit == null || shopLimit > 1;
        };
    }

    /** "BASIC_6M" → "Basic" when the plan master could not be read. */
    public static String prettify(String code) {
        if (code == null || code.isBlank()) return "current";
        String head = code.trim().split("_")[0].toLowerCase();
        return Character.toUpperCase(head.charAt(0)) + head.substring(1);
    }
}
