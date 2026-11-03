package com.repairshop.saas.subscription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.subscription.entity.PaymentWebhookEvent;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.razorpay.RazorpayPayment;
import com.repairshop.saas.subscription.razorpay.RazorpayProperties;
import com.repairshop.saas.subscription.razorpay.RazorpaySignature;
import com.repairshop.saas.subscription.repository.PaymentWebhookEventRepository;
import com.repairshop.saas.subscription.repository.SubscriptionOrderRepository;
import com.repairshop.saas.subscription.service.OrderLifecycleService.CapturedPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * POST /subscriptions/payment/webhook/razorpay. The signature is checked over
 * the RAW body bytes before anything is parsed; events are deduped by
 * X-Razorpay-Event-Id (sha256 of the body when the header is absent).
 * payment.captured / order.paid (and payment.authorized → capture) funnel into
 * the same idempotent {@link OrderLifecycleService#activatePaid}.
 *
 * <p>Business refusals (unknown order, amount mismatch) are recorded and
 * answered 200 so Razorpay does not retry forever; infrastructure failures
 * answer 500 so it does retry, and the retry re-processes the stored event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final RazorpayProperties props;
    private final PaymentWebhookEventRepository events;
    private final SubscriptionOrderRepository orders;
    private final OrderLifecycleService lifecycle;
    private final CheckoutService checkout;
    private final ObjectMapper mapper;
    private final Clock clock;

    public Map<String, Object> handle(byte[] rawBody, String signature, String eventIdHeader) {
        if (!props.isWebhookConfigured()) throw ApiException.paymentsNotConfigured();
        if (props.isModeMismatch()) throw ApiException.paymentsNotConfigured(RazorpayProperties.MODE_MISMATCH_MESSAGE);
        if (!RazorpaySignature.verifyWebhook(rawBody, signature, props.webhookSecret())) {
            throw ApiException.badRequest("INVALID_SIGNATURE", "Webhook signature is invalid");
        }
        JsonNode root;
        try {
            root = mapper.readTree(rawBody);
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_PAYLOAD", "Webhook body is not JSON");
        }
        String eventId = eventIdHeader != null && !eventIdHeader.isBlank()
                ? eventIdHeader.trim() : "sha256:" + RazorpaySignature.sha256Hex(rawBody);
        String type = root.path("event").asText("");
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String rzpOrderId = text(paymentEntity, "order_id");
        if (rzpOrderId == null) rzpOrderId = text(root.path("payload").path("order").path("entity"), "id");
        String rzpPaymentId = text(paymentEntity, "id");

        PaymentWebhookEvent event = events.findByEventId(eventId).orElse(null);
        if (event != null && Boolean.TRUE.equals(event.getProcessed())) return result("duplicate", eventId);
        if (event == null) {
            try {
                event = events.saveAndFlush(PaymentWebhookEvent.builder()
                        .provider("RAZORPAY")
                        .eventId(eventId)
                        .eventType(type)
                        .razorpayOrderId(rzpOrderId)
                        .razorpayPaymentId(rzpPaymentId)
                        .payload(new String(rawBody, StandardCharsets.UTF_8))
                        .processed(false)
                        .attempts(1)
                        .build());
            } catch (DataIntegrityViolationException race) {
                event = events.findByEventId(eventId).orElseThrow(() -> race);
                if (Boolean.TRUE.equals(event.getProcessed())) return result("duplicate", eventId);
            }
        } else {
            event.setAttempts((event.getAttempts() == null ? 1 : event.getAttempts()) + 1);
        }

        String outcome;
        try {
            outcome = process(type, paymentEntity, rzpOrderId);
            event.setProcessingError(null);
        } catch (ApiException business) {
            outcome = "rejected:" + business.getCode();
            event.setProcessingError(business.getCode() + ": " + business.getMessage());
            log.warn("Razorpay webhook {} ({}) rejected: {}", eventId, type, business.getMessage());
        } catch (RuntimeException e) {
            event.setProcessingError(truncate(e.toString()));
            events.save(event);
            log.error("Razorpay webhook {} ({}) failed; Razorpay will retry", eventId, type, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WEBHOOK_FAILED", "Webhook processing failed");
        }
        event.setProcessed(true);
        event.setProcessedAt(clock.instant());
        events.save(event);
        return result(outcome, eventId);
    }

    private String process(String type, JsonNode paymentEntity, String rzpOrderId) {
        switch (type) {
            case "payment.captured", "order.paid", "payment.authorized" -> {
                Optional<SubscriptionOrder> order = rzpOrderId == null ? Optional.empty()
                        : orders.findByRazorpayOrderId(rzpOrderId);
                if (order.isEmpty()) return "unknown_order";
                RazorpayPayment payment = RazorpayPayment.from(paymentEntity);
                if (payment.isAuthorized()) {
                    if (!props.isConfigured()) return "authorized_not_captured";
                    if (payment.amount() != order.get().getTotalPaise()) {
                        throw ApiException.conflict("AMOUNT_MISMATCH", "Paid amount does not match the order total");
                    }
                    payment = checkout.captureSafely(order.get(), payment);
                }
                if (!payment.isCaptured()) return "not_captured";
                return lifecycle.activatePaid(order.get().getId(), CapturedPayment.from(payment), "WEBHOOK")
                        .outcome().name();
            }
            case "payment.failed" -> {
                Optional<SubscriptionOrder> order = rzpOrderId == null ? Optional.empty()
                        : orders.findByRazorpayOrderId(rzpOrderId);
                if (order.isEmpty()) return "unknown_order";
                lifecycle.recordFailure(order.get().getId(), text(paymentEntity, "error_description"));
                return "failure_recorded";
            }
            default -> {
                return "ignored";
            }
        }
    }

    private static Map<String, Object> result(String status, String eventId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("eventId", eventId);
        return body;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n == null ? null : n.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static String truncate(String s) {
        return s == null ? null : s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
