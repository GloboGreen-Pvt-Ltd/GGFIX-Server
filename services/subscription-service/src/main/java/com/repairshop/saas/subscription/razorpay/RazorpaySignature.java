package com.repairshop.saas.subscription.razorpay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Razorpay signature checks. Both compare in constant time
 * ({@link MessageDigest#isEqual}) so the comparison leaks nothing about how
 * many leading characters of a forged signature were right.
 */
public final class RazorpaySignature {

    private RazorpaySignature() {}

    /** Checkout handler signature: HMAC-SHA256(orderId + "|" + paymentId, keySecret), hex. */
    public static boolean verifyPayment(String razorpayOrderId, String razorpayPaymentId,
                                        String signature, String keySecret) {
        if (isBlank(razorpayOrderId) || isBlank(razorpayPaymentId) || isBlank(signature) || isBlank(keySecret)) {
            return false;
        }
        byte[] payload = (razorpayOrderId + "|" + razorpayPaymentId).getBytes(StandardCharsets.UTF_8);
        return matches(hmacSha256Hex(payload, keySecret), signature);
    }

    /** Webhook signature: HMAC-SHA256 over the RAW request body bytes, hex. */
    public static boolean verifyWebhook(byte[] rawBody, String signature, String webhookSecret) {
        if (rawBody == null || isBlank(signature) || isBlank(webhookSecret)) return false;
        return matches(hmacSha256Hex(rawBody, webhookSecret), signature);
    }

    public static String hmacSha256Hex(byte[] data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean matches(String expectedHex, String providedHex) {
        byte[] expected = expectedHex.getBytes(StandardCharsets.US_ASCII);
        byte[] provided = providedHex.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, provided);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
