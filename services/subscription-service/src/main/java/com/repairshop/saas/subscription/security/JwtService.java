package com.repairshop.saas.subscription.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /**
     * Returns the shopId claim if present, or null if missing/blank.
     * Tolerant of tokens issued by the customer app that don't carry a shop context.
     */
    public UUID getShopId(String token) {
        String shopId = parseClaims(token).get("shopId", String.class);
        if (shopId == null || shopId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(shopId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        List<?> list = parseClaims(token).get("roles", List.class);
        return list != null ? list.stream().map(Object::toString).toList() : List.of();
    }
}
