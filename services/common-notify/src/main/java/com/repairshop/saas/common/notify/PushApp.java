package com.repairshop.saas.common.notify;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/** The NearGFix app a device token belongs to — stored as {@code push_device_tokens.app}. */
public enum PushApp {
    /** NearGFix Partner — shop owners and SHOP-login counter devices; addressed by shop. */
    PARTNER,
    /** NearGFix Customer; addressed by user. */
    CUSTOMER,
    /** NearGFix Employee — technicians, staff, pickup persons; addressed by user. */
    EMPLOYEE;

    private static final Set<String> EMPLOYEE_ROLES = Set.of("TECHNICIAN", "STAFF", "EMPLOYEE", "PICKUP_PERSON");

    /** Lenient parse of a client-sent app name ("partner", "Customer", …); null when unknown. */
    public static PushApp parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Whether a caller with these (normalised, no ROLE_ prefix) roles may register for this app. */
    public boolean allows(Collection<String> roles) {
        if (roles == null) return false;
        return switch (this) {
            case PARTNER -> roles.contains("SHOP_OWNER");
            case CUSTOMER -> roles.contains("CUSTOMER");
            case EMPLOYEE -> roles.stream().anyMatch(EMPLOYEE_ROLES::contains);
        };
    }

    /** The app a caller most likely uses when the client did not say. */
    public static PushApp inferFrom(Collection<String> roles) {
        if (roles == null) return null;
        if (PARTNER.allows(roles)) return PARTNER;
        if (EMPLOYEE.allows(roles)) return EMPLOYEE;
        if (CUSTOMER.allows(roles)) return CUSTOMER;
        return null;
    }
}
