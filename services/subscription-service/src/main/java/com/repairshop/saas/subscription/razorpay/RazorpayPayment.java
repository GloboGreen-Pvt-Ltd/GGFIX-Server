package com.repairshop.saas.subscription.razorpay;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** The fields of a Razorpay payment entity this service relies on. */
public record RazorpayPayment(
        String id,
        String orderId,
        long amount,
        String currency,
        String status,
        String method,
        String email,
        String contact,
        String vpa,
        String errorDescription,
        Instant createdAt
) {
    public static RazorpayPayment from(JsonNode n) {
        long created = n.path("created_at").asLong(0);
        return new RazorpayPayment(
                text(n, "id"), text(n, "order_id"), n.path("amount").asLong(0), text(n, "currency"),
                text(n, "status"), text(n, "method"), text(n, "email"), text(n, "contact"), text(n, "vpa"),
                text(n, "error_description"), created > 0 ? Instant.ofEpochSecond(created) : null);
    }

    public boolean isCaptured() {
        return "captured".equalsIgnoreCase(status);
    }

    public boolean isAuthorized() {
        return "authorized".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
