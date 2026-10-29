package com.repairshop.saas.ticket.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Role checks on the {@code roles} request attribute JwtAuthFilter sets.
 *
 * An "employee" is a Staff-app login (TECHNICIAN / STAFF / PICKUP_PERSON) that
 * does not also carry an owner role. SHOP_OWNER / SUPER_ADMIN tokens are never
 * employees, so every employee-only restriction leaves the owner apps untouched.
 */
public final class CallerRoles {

    private static final Set<String> EMPLOYEE_ROLES = Set.of("TECHNICIAN", "STAFF", "PICKUP_PERSON");
    private static final Set<String> OWNER_ROLES = Set.of("SHOP_OWNER", "SUPER_ADMIN");

    private CallerRoles() {}

    public static boolean isEmployee(HttpServletRequest request) {
        List<String> roles = roles(request);
        return roles.stream().anyMatch(EMPLOYEE_ROLES::contains)
                && roles.stream().noneMatch(OWNER_ROLES::contains);
    }

    /** 403 for employee callers on owner-side actions; everyone else passes as before. */
    public static void denyEmployee(HttpServletRequest request) {
        if (isEmployee(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This action requires the shop owner role.");
        }
    }

    private static List<String> roles(HttpServletRequest request) {
        Object raw = request.getAttribute("roles");
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream().map(r -> String.valueOf(r).trim().toUpperCase(Locale.ROOT)).toList();
    }
}
