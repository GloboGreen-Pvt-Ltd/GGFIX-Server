package com.repairshop.saas.masterdata.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// Mirror of the JwtService used by the tenant services (ticket/shop/etc.) so
// master-data can validate the SAME JWT (shared app.jwt.secret / JWT_SECRET).
// Used only to authenticate /media/upload — reference-data reads stay public.
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

    public UUID getShopId(String token) {
        String raw = parseClaims(token).get("shopId", String.class);
        return raw != null && !raw.isBlank() ? UUID.fromString(raw) : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        List<?> list = parseClaims(token).get("roles", List.class);
        return list != null ? list.stream().map(Object::toString).toList() : List.of();
    }
}
