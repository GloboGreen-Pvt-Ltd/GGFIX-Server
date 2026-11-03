package com.repairshop.saas.marketplace.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Reads the request attributes set by {@link JwtAuthFilter}. */
public final class CallerContext {

    private CallerContext() {}

    /**
     * /shop/marketplace/**: 403 without a shopId claim, or when the token isn't a
     * shop owner / admin session (a technician or staff token also carries a shopId).
     */
    public static UUID requireShopId(HttpServletRequest req) {
        UUID id = uuidAttr(req, "shopId");
        if (id == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A shop session is required for this endpoint.");
        ShopAccess.requireSellerRole();
        return id;
    }

    public static UUID requireUserId(HttpServletRequest req) {
        UUID id = uuidAttr(req, "userId");
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user context");
        return id;
    }

    public static UUID uuidAttr(HttpServletRequest req, String name) {
        Object v = req.getAttribute(name);
        if (v == null) return null;
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
