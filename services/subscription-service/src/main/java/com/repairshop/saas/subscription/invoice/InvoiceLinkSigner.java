package com.repairshop.saas.subscription.invoice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Short-lived, HMAC-SHA256 signed public PDF links — used ONLY as the BotBee
 * document-header URL, because the WhatsApp provider fetches the file without
 * a JWT. Signature over "invoice-pdf:{id}:{exp}"; exp = epoch seconds, at most
 * 60 minutes ahead. Secret: app.invoice.link-secret (defaults to the JWT secret).
 */
@Component
public class InvoiceLinkSigner {

    public static final Duration MAX_TTL = Duration.ofMinutes(60);

    private final byte[] secret;
    private final String publicBaseUrl;
    private final Duration ttl;
    private final Clock clock;

    public InvoiceLinkSigner(@Value("${app.invoice.link-secret:${app.jwt.secret}}") String secret,
                             @Value("${app.invoice.public-base-url:https://api.ggfix.in/subscription}") String publicBaseUrl,
                             @Value("${app.invoice.link-ttl-minutes:60}") long ttlMinutes,
                             Clock clock) {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("app.invoice.link-secret is empty");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        this.ttl = Duration.ofMinutes(Math.max(1, Math.min(ttlMinutes, MAX_TTL.toMinutes())));
        this.clock = clock;
    }

    public record SignedLink(String url, long exp, String sig) {}

    public SignedLink sign(UUID invoiceId) {
        long exp = clock.instant().plus(ttl).getEpochSecond();
        String sig = signature(invoiceId, exp);
        return new SignedLink(publicBaseUrl + "/subscriptions/invoices/public/" + invoiceId + ".pdf?exp=" + exp
                + "&sig=" + sig, exp, sig);
    }

    /** Valid = signature matches (constant-time), not expired, and not further ahead than the max TTL. */
    public boolean verify(UUID invoiceId, long exp, String sig) {
        if (invoiceId == null || sig == null || sig.isBlank()) return false;
        Instant now = clock.instant();
        if (exp <= now.getEpochSecond()) return false;
        if (exp > now.plus(MAX_TTL).plusSeconds(60).getEpochSecond()) return false;
        byte[] expected = signature(invoiceId, exp).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, sig.trim().getBytes(StandardCharsets.US_ASCII));
    }

    private String signature(UUID invoiceId, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal(("invoice-pdf:" + invoiceId + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
