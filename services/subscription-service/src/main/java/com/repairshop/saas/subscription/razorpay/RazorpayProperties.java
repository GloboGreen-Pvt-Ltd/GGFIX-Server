package com.repairshop.saas.subscription.razorpay;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Razorpay credentials and mode, read ONLY from the environment
 * (RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET,
 * RAZORPAY_MODE via application.yml). Secrets are never logged or returned;
 * {@link #toString()} masks them.
 *
 * <p>RAZORPAY_MODE (TEST | LIVE, default TEST) is the server's payment mode.
 * Payments count as configured only when the key id's prefix (rzp_test_ /
 * rzp_live_) matches it, so a test key can never run a LIVE server and vice
 * versa. Every order/payment is stamped with {@link #serverMode()}.
 */
@Slf4j
@Component
public class RazorpayProperties {

    public static final String TEST = "TEST";
    public static final String LIVE = "LIVE";
    public static final String UNCONFIGURED = "UNCONFIGURED";

    public static final String NOT_CONFIGURED_MESSAGE = "Payments are not configured";
    public static final String MODE_MISMATCH_MESSAGE = "Razorpay key does not match RAZORPAY_MODE";

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final String configuredMode;

    public RazorpayProperties(@Value("${razorpay.key-id:}") String keyId,
                              @Value("${razorpay.key-secret:}") String keySecret,
                              @Value("${razorpay.webhook-secret:}") String webhookSecret,
                              @Value("${razorpay.mode:TEST}") String mode) {
        this.keyId = trim(keyId);
        this.keySecret = trim(keySecret);
        this.webhookSecret = trim(webhookSecret);
        String m = trim(mode).toUpperCase();
        this.configuredMode = m.isEmpty() ? TEST : m;
    }

    @PostConstruct
    void warnOnMismatch() {
        if (isModeMismatch()) {
            // Mode names only — never the key itself.
            log.warn("{}: RAZORPAY_MODE={} but the key id is a {} key. Payments are disabled until they match.",
                    MODE_MISMATCH_MESSAGE, configuredMode, keyMode() == null ? "non-Razorpay" : keyMode());
        }
    }

    public String keyId() {
        return keyId;
    }

    public String keySecret() {
        return keySecret;
    }

    public String webhookSecret() {
        return webhookSecret;
    }

    /** Both halves of the key pair are present (says nothing about the mode). */
    public boolean hasKeys() {
        return !keyId.isEmpty() && !keySecret.isEmpty();
    }

    /** TEST / LIVE from the key prefix; null for an unrecognised key. */
    public String keyMode() {
        if (keyId.startsWith("rzp_live_")) return LIVE;
        if (keyId.startsWith("rzp_test_")) return TEST;
        return null;
    }

    /** Keys present but their prefix disagrees with RAZORPAY_MODE (or the mode is invalid). */
    public boolean isModeMismatch() {
        return hasKeys() && !configuredMode.equals(keyMode());
    }

    /** API calls are allowed: keys present AND key prefix matches RAZORPAY_MODE. */
    public boolean isConfigured() {
        return hasKeys() && !isModeMismatch();
    }

    public boolean isWebhookConfigured() {
        return !webhookSecret.isEmpty();
    }

    /** Why payments are unavailable (for the 409 message); null when they are available. */
    public String unavailableReason() {
        if (isModeMismatch()) return MODE_MISMATCH_MESSAGE;
        return isConfigured() ? null : NOT_CONFIGURED_MESSAGE;
    }

    /** The mode stamped on orders/payments: RAZORPAY_MODE (anything but LIVE reads as TEST). */
    public String serverMode() {
        return LIVE.equals(configuredMode) ? LIVE : TEST;
    }

    /** Admin display: RAZORPAY_MODE when keys are present, else UNCONFIGURED. */
    public String mode() {
        return hasKeys() ? serverMode() : UNCONFIGURED;
    }

    /** Client banner: TEST / LIVE only when payments actually work, else UNCONFIGURED. */
    public String checkoutMode() {
        return isConfigured() ? serverMode() : UNCONFIGURED;
    }

    /** "rzp_test_****WXYZ" — enough to tell keys apart, useless to an attacker. */
    public String keyIdMasked() {
        if (keyId.isEmpty()) return null;
        String prefix = keyId.startsWith("rzp_live_") ? "rzp_live_" : keyId.startsWith("rzp_test_") ? "rzp_test_" : "";
        String tail = keyId.length() > 4 ? keyId.substring(keyId.length() - 4) : "";
        return prefix + "****" + tail;
    }

    @Override
    public String toString() {
        return "RazorpayProperties{mode=" + configuredMode + ", keyId=" + keyIdMasked()
                + ", modeMismatch=" + isModeMismatch() + ", webhookConfigured=" + isWebhookConfigured() + "}";
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
