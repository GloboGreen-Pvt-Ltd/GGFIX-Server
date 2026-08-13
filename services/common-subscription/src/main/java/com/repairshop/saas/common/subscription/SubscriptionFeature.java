package com.repairshop.saas.common.subscription;

/**
 * A metered capability of a subscription plan.
 *
 * <p>The {@link #scope} is the important part and the reason this enum exists at
 * all. "Up to 3 employees" and "up to 2 shops" look like the same kind of number
 * but they are counted over different populations: the employee allowance is
 * granted <em>per shop</em> (an owner on the trial with two shops may hire three
 * people in each, six in total), while the shop allowance is counted <em>per
 * owner</em>. Collapsing the two — counting employees across all of an owner's
 * shops — is exactly the bug this system was built to fix, so the distinction is
 * modelled explicitly rather than left to each call site to remember.
 */
public enum SubscriptionFeature {

    /** Shops an owner may run. Counted across the whole account. */
    SHOPS(LimitScope.PER_OWNER, "shops", "Shops"),

    /** Active employees. Counted within ONE shop — see the class note. */
    EMPLOYEES(LimitScope.PER_SHOP, "employees", "Employees"),

    /** Marketplace sell orders/listings. Counted within one shop. */
    SELL_ORDERS(LimitScope.PER_SHOP, "sell orders", "Sell orders"),

    /** Marketplace purchases. Unlimited on both current plans; here so the
     *  catalogue is complete and a future paid tier has somewhere to hang. */
    BUY_PRODUCTS(LimitScope.PER_SHOP, "product purchases", "Buy products");

    /** Which population a feature's allowance is counted over. */
    public enum LimitScope {
        /** Counted across every shop the owner has. */
        PER_OWNER,
        /** Counted within a single shop; each shop gets the full allowance. */
        PER_SHOP
    }

    private final LimitScope scope;
    private final String noun;
    private final String label;

    SubscriptionFeature(LimitScope scope, String noun, String label) {
        this.scope = scope;
        this.noun = noun;
        this.label = label;
    }

    public LimitScope getScope() {
        return scope;
    }

    /** Lower-case plural used inside sentences ("allows up to 3 employees"). */
    public String getNoun() {
        return noun;
    }

    /** Title-case label for UI headings. */
    public String getLabel() {
        return label;
    }

    public boolean isPerShop() {
        return scope == LimitScope.PER_SHOP;
    }
}
