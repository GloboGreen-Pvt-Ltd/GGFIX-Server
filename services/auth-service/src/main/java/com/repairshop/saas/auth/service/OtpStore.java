package com.repairshop.saas.auth.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ephemeral in-memory OTP store for email-verification flows.
 *
 * IMPORTANT: OTPs are NEVER written to the database. They live here for a
 * short TTL (default 10 minutes) and are purged on use or expiry. If the JVM
 * restarts, all outstanding codes are invalidated by design — the admin must
 * re-issue a fresh OTP. This is intentional: persisting OTPs widens the
 * attack surface and offers no real benefit for a short-lived code.
 *
 * Keyed by lowercased identifier (email or mobile). One outstanding OTP per
 * identifier; re-issuing rotates the value.
 */
@Component
public class OtpStore {

    private static final long TTL_MS = 10 * 60 * 1000L; // 10 min
    private final SecureRandom rng = new SecureRandom();
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public String issue(String identifier) {
        String code = String.format("%06d", rng.nextInt(1_000_000));
        store.put(key(identifier), new Entry(code, Instant.now().toEpochMilli() + TTL_MS));
        return code;
    }

    /** Returns true if the code matches and is unexpired; consumes the entry on success. */
    public boolean verify(String identifier, String code) {
        if (identifier == null || code == null) return false;
        String k = key(identifier);
        Entry e = store.get(k);
        if (e == null) return false;
        if (Instant.now().toEpochMilli() > e.expiresAtMs) { store.remove(k); return false; }
        if (!e.code.equals(code.trim())) return false;
        store.remove(k);
        return true;
    }

    /** Test/debug helper — DO NOT expose via API. */
    public String peek(String identifier) {
        Entry e = store.get(key(identifier));
        return e == null ? null : e.code;
    }

    private static String key(String id) { return id.trim().toLowerCase(); }

    private record Entry(String code, long expiresAtMs) {}
}
