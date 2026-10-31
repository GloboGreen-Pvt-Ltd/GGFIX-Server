package com.repairshop.saas.subscription.razorpay;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RazorpaySignatureTest {

    // Reference values computed independently with:
    //   printf '%s' 'order_IluGWxBm9U8zJ8|pay_IluGWyBm9U8zJ9' | openssl dgst -sha256 -hmac 'rzp_test_secret_123'
    private static final String SECRET = "rzp_test_secret_123";
    private static final String ORDER = "order_IluGWxBm9U8zJ8";
    private static final String PAYMENT = "pay_IluGWyBm9U8zJ9";
    private static final String SIGNATURE = "baa196ebe626455104230f946ab98b66ebb319a145184a4d4e29a31234b2a993";

    //   printf '%s' '{"event":"payment.captured","payload":{}}' | openssl dgst -sha256 -hmac 'whsec_abc'
    private static final String WEBHOOK_SECRET = "whsec_abc";
    private static final String WEBHOOK_BODY = "{\"event\":\"payment.captured\",\"payload\":{}}";
    private static final String WEBHOOK_SIGNATURE = "2c448573cf4c76122e1c9457844994ecf779432ff0c18f4d215c41782e11abc9";

    @Test
    void validPaymentSignatureVerifies() {
        assertTrue(RazorpaySignature.verifyPayment(ORDER, PAYMENT, SIGNATURE, SECRET));
        assertTrue(RazorpaySignature.verifyPayment(ORDER, PAYMENT, SIGNATURE.toUpperCase(), SECRET));
    }

    @Test
    void tamperedPaymentSignatureIsRejected() {
        assertFalse(RazorpaySignature.verifyPayment(ORDER, PAYMENT, SIGNATURE, "wrong_secret"));
        assertFalse(RazorpaySignature.verifyPayment(ORDER, "pay_other", SIGNATURE, SECRET));
        assertFalse(RazorpaySignature.verifyPayment("order_other", PAYMENT, SIGNATURE, SECRET));
        assertFalse(RazorpaySignature.verifyPayment(ORDER, PAYMENT, SIGNATURE.substring(1) + "0", SECRET));
        assertFalse(RazorpaySignature.verifyPayment(ORDER, PAYMENT, "", SECRET));
        assertFalse(RazorpaySignature.verifyPayment(ORDER, PAYMENT, SIGNATURE, ""));
        assertFalse(RazorpaySignature.verifyPayment(null, PAYMENT, SIGNATURE, SECRET));
    }

    @Test
    void webhookSignatureIsOverTheRawBodyBytes() {
        byte[] raw = WEBHOOK_BODY.getBytes(StandardCharsets.UTF_8);
        assertTrue(RazorpaySignature.verifyWebhook(raw, WEBHOOK_SIGNATURE, WEBHOOK_SECRET));

        // Same JSON, re-serialised with different whitespace: must NOT verify.
        byte[] reformatted = "{ \"event\": \"payment.captured\", \"payload\": {} }".getBytes(StandardCharsets.UTF_8);
        assertFalse(RazorpaySignature.verifyWebhook(reformatted, WEBHOOK_SIGNATURE, WEBHOOK_SECRET));
        assertFalse(RazorpaySignature.verifyWebhook(raw, WEBHOOK_SIGNATURE, "other_secret"));
        assertFalse(RazorpaySignature.verifyWebhook(raw, null, WEBHOOK_SECRET));
    }
}
