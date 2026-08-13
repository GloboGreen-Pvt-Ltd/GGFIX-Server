package com.repairshop.saas.auth.entity;

/**
 * Canonical users.role values and the role predicates the account-management
 * endpoints gate on.
 *
 * Naming note: the platform-administrator role has always been stored as
 * SUPER_ADMIN (see AdminSeeder and the seeded accounts), so "ADMIN" in the
 * account-management spec maps onto SUPER_ADMIN rather than renaming it —
 * a rename would invalidate every seeded admin row, the loginType clients
 * route on, and the admin web's login gate. {@link #isAdmin} accepts the
 * literal "ADMIN" too so a hand-inserted row spelled that way still works.
 */
public final class Roles {

    public static final String SUPER_ADMIN   = "SUPER_ADMIN";
    public static final String ADMIN         = "ADMIN";
    public static final String MARKET_PERSON = "MARKET_PERSON";
    public static final String SHOP_OWNER    = "SHOP_OWNER";
    public static final String TECHNICIAN    = "TECHNICIAN";

    private Roles() {}

    private static String norm(String role) {
        return role == null ? "" : role.trim().toUpperCase();
    }

    /** Platform administrator — the only role allowed to change account status. */
    public static boolean isAdmin(String role) {
        String r = norm(role);
        return SUPER_ADMIN.equals(r) || ADMIN.equals(r);
    }

    public static boolean isMarketPerson(String role) {
        return MARKET_PERSON.equals(norm(role));
    }

    public static boolean isShopOwner(String role) {
        return SHOP_OWNER.equals(norm(role));
    }

    /** Back-office staff — may create shop owners. Only admins may (de)activate them. */
    public static boolean isStaff(String role) {
        return isAdmin(role) || isMarketPerson(role);
    }
}
