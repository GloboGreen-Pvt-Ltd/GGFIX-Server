package com.repairshop.saas.marketplace.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

/**
 * Who may create or change a shop's marketplace listings.
 *
 * <ul>
 *   <li>Platform admins: any shop.</li>
 *   <li>Shop owners: the shop in their token (the active shop — /auth/switch-shop
 *       re-issues the token), or any other shop they own ({@code shops.owner_user_id}),
 *       except in a shop-mobile login ({@code loginScope=SHOP}), which is limited to
 *       that one shop.</li>
 *   <li>Everyone else (customers, technicians, staff tokens that merely carry a
 *       shopId): nobody's.</li>
 * </ul>
 * The Partner app only opens the Sell module for SHOP_OWNER sessions; this keeps
 * the API in line with that.
 */
@Component
@RequiredArgsConstructor
public class ShopAccess {

    private static final Logger log = LoggerFactory.getLogger(ShopAccess.class);

    public static final Set<String> SELLER_ROLES = Set.of("SHOP_OWNER", "OWNER");
    public static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN");

    private final JdbcTemplate jdbc;

    public static boolean hasAnyRole(Set<String> roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String name = a.getAuthority();
            if (name != null && name.startsWith("ROLE_") && roles.contains(name.substring(5).toUpperCase())) return true;
        }
        return false;
    }

    public static boolean isAdmin() {
        return hasAnyRole(ADMIN_ROLES);
    }

    public static boolean isSeller() {
        return hasAnyRole(SELLER_ROLES);
    }

    /** Throws 403 unless the caller is a shop owner (or admin) session. */
    public static void requireSellerRole() {
        if (!isSeller() && !isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only shop owners can manage marketplace listings.");
        }
    }

    public boolean ownsShop(UUID userId, UUID shopId) {
        if (userId == null || shopId == null) return false;
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM shops WHERE id = ? AND owner_user_id = ?", Integer.class, shopId, userId);
            return n != null && n > 0;
        } catch (DataAccessException e) {
            log.warn("Shop ownership check failed for shop {}: {}", shopId, e.getMessage());
            return false;
        }
    }

    public boolean canManage(HttpServletRequest req, UUID targetShopId) {
        if (targetShopId == null) return false;
        if (isAdmin()) return true;
        if (!isSeller()) return false;
        if (targetShopId.equals(CallerContext.uuidAttr(req, "shopId"))) return true;
        if ("SHOP".equalsIgnoreCase(String.valueOf(req.getAttribute("loginScope")))) return false;
        return ownsShop(CallerContext.uuidAttr(req, "userId"), targetShopId);
    }

    public void requireManage(HttpServletRequest req, UUID targetShopId) {
        if (!canManage(req, targetShopId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't manage listings for this shop.");
        }
    }
}
