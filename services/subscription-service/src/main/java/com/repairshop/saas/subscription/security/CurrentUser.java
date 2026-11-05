package com.repairshop.saas.subscription.security;

import com.repairshop.saas.subscription.exception.ApiException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the caller from the SecurityContext that {@link JwtAuthFilter} fills
 * (principal = JWT sub as a string, authorities = "ROLE_" + each JWT role).
 *
 * <p>Admin = SUPER_ADMIN (the stored platform-admin role, see auth-service
 * Roles/AdminSeeder) or the literal ADMIN alias auth-service also accepts.
 */
@Component
public class CurrentUser {

    public static final Set<String> ADMIN_ROLES = Set.of("SUPER_ADMIN", "ADMIN");

    /** The authenticated caller, if the request carried a valid JWT. */
    public record Actor(UUID userId, List<String> roles) {
        public String primaryRole() {
            for (String r : roles) if (ADMIN_ROLES.contains(r)) return r;
            return roles.isEmpty() ? null : roles.get(0);
        }

        public boolean isAdmin() {
            return roles.stream().anyMatch(ADMIN_ROLES::contains);
        }
    }

    public Optional<Actor> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof UsernamePasswordAuthenticationToken) || auth.getPrincipal() == null) {
            return Optional.empty();
        }
        try {
            UUID userId = UUID.fromString(auth.getPrincipal().toString());
            List<String> roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                    .map(a -> a.trim().toUpperCase())
                    .toList();
            return Optional.of(new Actor(userId, roles));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Owner routes: the JWT sub IS the owner. 401 without a valid token. */
    public Actor requireUser() {
        return current().orElseThrow(() -> ApiException.unauthorized("Sign in to manage your subscription"));
    }

    public UUID requireOwnerId() {
        return requireUser().userId();
    }

    /** Management routes: 401 without a token, 403 without an admin role. */
    public Actor requireAdmin() {
        Actor actor = requireUser();
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("ADMIN_ONLY", "Only platform administrators can do this");
        }
        return actor;
    }
}
