package com.repairshop.saas.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiry-ms:86400000}") long expiryMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMs = expiryMs;
    }

    public String generateToken(UUID userId, UUID shopId, String email, List<String> roles) {
        return generateToken(userId, shopId, email, roles, null);
    }

    /**
     * Overload that stamps a loginScope claim ("OWNER" or "SHOP") so downstream
     * services and /auth/switch-shop can refuse cross-scope actions. Pass null
     * to omit the claim (legacy owner tokens).
     */
    public String generateToken(UUID userId, UUID shopId, String email, List<String> roles, String loginScope) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key);
        if (shopId != null) builder.claim("shopId", shopId.toString());
        if (loginScope != null && !loginScope.isBlank()) builder.claim("loginScope", loginScope);
        return builder.compact();
    }

    /**
     * Issue a JWT for a platform customer (mobile app). No shopId claim — customers
     * are not tied to any single shop.
     */
    public String issueCustomerToken(UUID customerUserId, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);
        return Jwts.builder()
                .subject(customerUserId.toString())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
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
        return UUID.fromString(parseClaims(token).get("shopId", String.class));
    }

    /** Returns "OWNER", "SHOP", or null when the claim isn't present (legacy tokens). */
    public String getLoginScope(String token) {
        return parseClaims(token).get("loginScope", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        List<?> list = parseClaims(token).get("roles", List.class);
        return list != null ? list.stream().map(Object::toString).collect(Collectors.toList()) : List.of();
    }

    public long getExpiryMs() {
        return expiryMs;
    }
}
