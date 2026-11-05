package com.repairshop.saas.subscription.invoice;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The public PDF link: valid / expired / tampered. */
class InvoiceLinkSignerTest {

    private static final Instant NOW = Instant.parse("2026-11-02T06:30:00Z");
    private static final String SECRET = "test-secret-for-invoice-links-0123456789";

    private static InvoiceLinkSigner signer(Instant at, String secret, long ttlMinutes) {
        return new InvoiceLinkSigner(secret, "https://api.ggfix.in/subscription/", ttlMinutes,
                Clock.fixed(at, ZoneOffset.UTC));
    }

    @Test
    void validLinkVerifies() {
        UUID id = UUID.randomUUID();
        InvoiceLinkSigner.SignedLink link = signer(NOW, SECRET, 60).sign(id);

        assertEquals(NOW.plus(Duration.ofMinutes(60)).getEpochSecond(), link.exp());
        assertTrue(link.url().startsWith("https://api.ggfix.in/subscription/subscriptions/invoices/public/" + id + ".pdf?exp="));
        assertTrue(link.url().endsWith("&sig=" + link.sig()));
        assertTrue(signer(NOW.plusSeconds(59 * 60), SECRET, 60).verify(id, link.exp(), link.sig()));
    }

    @Test
    void expiredLinkIsRejected() {
        UUID id = UUID.randomUUID();
        InvoiceLinkSigner.SignedLink link = signer(NOW, SECRET, 60).sign(id);
        assertFalse(signer(NOW.plusSeconds(60 * 60), SECRET, 60).verify(id, link.exp(), link.sig()));
        assertFalse(signer(NOW.plus(Duration.ofDays(1)), SECRET, 60).verify(id, link.exp(), link.sig()));
    }

    @Test
    void tamperedLinkIsRejected() {
        UUID id = UUID.randomUUID();
        InvoiceLinkSigner s = signer(NOW, SECRET, 60);
        InvoiceLinkSigner.SignedLink link = s.sign(id);

        assertFalse(s.verify(UUID.randomUUID(), link.exp(), link.sig()));          // other invoice
        assertFalse(s.verify(id, link.exp() + 600, link.sig()));                   // extended expiry
        assertFalse(s.verify(id, link.exp(), link.sig().substring(1) + "A"));      // altered signature
        assertFalse(s.verify(id, link.exp(), null));
        assertFalse(s.verify(id, link.exp(), ""));
        assertFalse(signer(NOW, "another-secret-0123456789-0123456789", 60).verify(id, link.exp(), link.sig()));
    }

    @Test
    void ttlIsCappedAtSixtyMinutes() {
        UUID id = UUID.randomUUID();
        InvoiceLinkSigner.SignedLink link = signer(NOW, SECRET, 24 * 60).sign(id);
        assertEquals(NOW.plus(InvoiceLinkSigner.MAX_TTL).getEpochSecond(), link.exp());
        // A link claiming a far-future expiry is refused even with a matching secret.
        assertFalse(signer(NOW, SECRET, 60).verify(id, NOW.plus(Duration.ofHours(5)).getEpochSecond(), link.sig()));
    }
}
