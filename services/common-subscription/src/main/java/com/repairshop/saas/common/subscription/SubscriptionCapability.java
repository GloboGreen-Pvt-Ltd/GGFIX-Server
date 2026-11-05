package com.repairshop.saas.common.subscription;

/**
 * An on/off entitlement — a capability the plan either grants or withholds, as
 * opposed to the metered allowances in {@link SubscriptionFeature}.
 *
 * <p>The two are kept apart because they fail differently. A metered feature
 * refuses the <em>next</em> unit and its message quotes a count ("3 of 3
 * employees"); a capability refuses the action outright and its message names
 * the plan. Folding capabilities into the counter model would mean expressing
 * "off" as a limit of zero, and every counter message would then have to
 * special-case that zero to avoid telling users they may add "up to 0".
 *
 * <p>The {@link #getKey()} values are the JSON keys of the entitlements
 * payload's {@code features} object, so the client reads
 * {@code entitlements.features.pickupService} directly.
 */
public enum SubscriptionCapability {

    NEW_SERVICE_BOOKING("newServiceBooking", "New Service Booking"),
    PICKUP_SERVICE("pickupService", "Pickup Service"),
    BUY_PRODUCTS("buyProducts", "Buy Products"),
    SELL_PRODUCTS("sellProducts", "Sell Products"),

    /**
     * Whether the account may run more than one shop. Derived from the shop
     * allowance rather than declared per plan — a plan that permits two shops
     * cannot coherently also report "multiple shops: no", and deriving it means
     * the two can never contradict each other.
     */
    MULTIPLE_SHOPS("multipleShops", "Multiple Shops");

    private final String key;
    private final String label;

    SubscriptionCapability(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** camelCase key used in the entitlements payload's `features` object. */
    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }
}
