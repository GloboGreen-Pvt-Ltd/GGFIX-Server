package com.repairshop.saas.subscription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.repairshop.saas.subscription.dto.v2.V2Requests.CancelRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.PurchaseRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.VerifyRequest;
import com.repairshop.saas.subscription.dto.v2.V2Views.OrderResult;
import com.repairshop.saas.subscription.dto.v2.V2Views.OrderView;
import com.repairshop.saas.subscription.dto.v2.V2Views.PaymentOrderView;
import com.repairshop.saas.subscription.dto.v2.V2Views.Prefill;
import com.repairshop.saas.subscription.dto.v2.V2Views.VerifyResult;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.razorpay.RazorpayClient;
import com.repairshop.saas.subscription.razorpay.RazorpayException;
import com.repairshop.saas.subscription.razorpay.RazorpayPayment;
import com.repairshop.saas.subscription.razorpay.RazorpayProperties;
import com.repairshop.saas.subscription.razorpay.RazorpaySignature;
import com.repairshop.saas.subscription.repository.SubscriptionOrderRepository;
import com.repairshop.saas.subscription.service.OrderLifecycleService.CapturedPayment;
import com.repairshop.saas.subscription.service.QuoteService.QuoteContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner checkout: purchase → Razorpay order → verify / status / webhook →
 * {@link OrderLifecycleService#activatePaid}. Deliberately NOT one big
 * transaction: Razorpay HTTP calls happen outside any DB transaction and each
 * state change is its own short, row-locked transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    static final List<String> PENDING = List.of(SubscriptionOrder.CREATED, SubscriptionOrder.AWAITING_PAYMENT);

    private final QuoteService quotes;
    private final OrderLifecycleService lifecycle;
    private final SubscriptionOrderRepository orders;
    private final RazorpayClient razorpay;
    private final RazorpayProperties props;
    private final SettingsService settings;
    private final OwnerDirectory directory;
    private final OrderViews views;
    private final MySubscriptionService mine;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Clock clock;

    /** What Razorpay says about an order's payments, after acting on any paid one. */
    public record Reconciled(SubscriptionOrder order, List<RazorpayPayment> payments) {}

    // ── purchase ────────────────────────────────────────────────────────────

    public OrderView purchase(UUID ownerUserId, PurchaseRequest req) {
        if (req == null) throw ApiException.badRequest("INVALID_REQUEST", "Request body is required");
        requireConfigured();
        if (!Boolean.TRUE.equals(req.acceptTerms())) {
            throw ApiException.badRequest("TERMS_NOT_ACCEPTED", "Please accept the terms to continue");
        }
        String channel = channel(req.channel());
        SettingsService.Settings s = settings.get();
        if (!s.channelEnabled(channel)) {
            throw ApiException.conflict("CHECKOUT_DISABLED", "Online checkout is not available on this app yet");
        }
        // Validate the new purchase first, so a bad request never cancels the old order.
        QuoteContext ctx = quotes.build(ownerUserId, req.planCode(), req.shopIds(), null);

        for (SubscriptionOrder pending : orders.findByOwnerUserIdAndStatusInOrderByCreatedAtDesc(ownerUserId, PENDING)) {
            if (!modeMatches(pending)) {
                // Created under the other RAZORPAY_MODE: this server cannot ask Razorpay about it.
                lifecycle.cancel(pending.getId(), "Created in " + pending.getPaymentMode()
                        + " mode; replaced by a new purchase", ownerUserId, "SHOP_OWNER");
                continue;
            }
            Reconciled r = reconcile(pending, true, "PURCHASE_RECHECK");
            SubscriptionOrder o = r.order();
            if (SubscriptionOrder.ACTIVATED.equals(o.getStatus()) || SubscriptionOrder.PAID.equals(o.getStatus())) {
                return views.of(o);
            }
            if (o.isPending()) {
                if (hasPaymentInFlight(r.payments())) {
                    throw ApiException.conflict("PAYMENT_IN_PROGRESS",
                            "A payment for order " + o.getOrderNo() + " is still in progress. "
                                    + "Please complete it or wait a few minutes and try again.");
                }
                lifecycle.cancel(o.getId(), "Replaced by a new purchase", ownerUserId, "SHOP_OWNER");
            }
        }

        try {
            SubscriptionOrder o = lifecycle.createOrder(ownerUserId, ctx, channel, OrderLifecycleService.SOURCE_RAZORPAY,
                    SubscriptionOrder.CREATED, ownerUserId, null,
                    clock.instant().plus(Duration.ofMinutes(s.quoteTtlMinutes())));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("orderNo", o.getOrderNo());
            d.put("planCode", o.getPlanCode());
            d.put("shopIds", ctx.quote().shopIds());
            d.put("totalPaise", o.getTotalPaise());
            d.put("channel", channel);
            audit.log(ownerUserId, "SHOP_OWNER", "ORDER_CREATED", "ORDER", o.getId().toString(), d);
            return views.of(o);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("PURCHASE_IN_PROGRESS", "Another purchase is already in progress");
        }
    }

    // ── Razorpay order for the checkout sheet ───────────────────────────────

    public PaymentOrderView paymentOrder(UUID ownerUserId, UUID orderId) {
        requireConfigured();
        SubscriptionOrder o = owned(ownerUserId, orderId);
        rejectClosed(o);
        if (!modeMatches(o)) {
            throw ApiException.conflict("ORDER_MODE_MISMATCH", "This order was created in " + o.getPaymentMode()
                    + " mode and cannot be paid now. Please start a new purchase.");
        }
        if (o.getExpiresAt() != null && o.getExpiresAt().isBefore(clock.instant())) {
            o = reconcile(o, false, "STATUS_POLL").order();
            rejectClosed(o);
            lifecycle.expire(o.getId());
            throw ApiException.conflict("ORDER_EXPIRED", "This quote has expired. Please start a new purchase.");
        }
        if (o.getRazorpayOrderId() == null) {
            Map<String, String> notes = notes(o);
            String rzpOrderId;
            try {
                rzpOrderId = razorpay.createOrder(o.getTotalPaise(), "INR", o.getOrderNo(), notes);
            } catch (RazorpayException e) {
                throw gateway(e);
            }
            o = lifecycle.attachRazorpayOrder(o.getId(), rzpOrderId);
        } else {
            o = lifecycle.markAwaiting(o.getId());
        }

        OwnerDirectory.OwnerInfo owner = directory.owner(ownerUserId).orElse(null);
        Prefill prefill = new Prefill(owner == null ? null : owner.name(), owner == null ? null : owner.phone(),
                owner == null ? null : owner.email());
        String description = o.getPlanName() + " · " + o.getDurationLabel() + " · " + o.getShopCount()
                + (o.getShopCount() == 1 ? " shop" : " shops");
        return new PaymentOrderView(o.getId(), o.getRazorpayOrderId(), props.keyId(), o.getTotalPaise(), "INR",
                "NearGFix", description, prefill, notes(o), upiOnlyCheckout());
    }

    // ── verify (checkout handler callback) ──────────────────────────────────

    public VerifyResult verify(UUID ownerUserId, VerifyRequest req) {
        if (req == null) throw ApiException.badRequest("INVALID_REQUEST", "Request body is required");
        requireConfigured();
        SubscriptionOrder o = owned(ownerUserId, req.orderId());
        if (!modeMatches(o)) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("orderNo", o.getOrderNo());
            d.put("orderPaymentMode", o.getPaymentMode());
            d.put("serverMode", props.serverMode());
            d.put("razorpayPaymentId", req.razorpayPaymentId());
            d.put("via", "VERIFY");
            audit.log(ownerUserId, "SHOP_OWNER", "PAYMENT_MODE_MISMATCH", "ORDER", o.getId().toString(), d);
            throw ApiException.conflict("PAYMENT_MODE_MISMATCH", "This order was created in " + o.getPaymentMode()
                + " mode but payments now run in " + props.serverMode() + " mode; it cannot be activated");
        }
        if (req.razorpayOrderId() == null || !req.razorpayOrderId().equals(o.getRazorpayOrderId())) {
            throw ApiException.badRequest("ORDER_MISMATCH", "Razorpay order does not match this order");
        }
        if (!RazorpaySignature.verifyPayment(o.getRazorpayOrderId(), req.razorpayPaymentId(),
                req.razorpaySignature(), props.keySecret())) {
            throw ApiException.badRequest("INVALID_SIGNATURE", "Payment signature is invalid");
        }
        boolean alreadyDone = SubscriptionOrder.ACTIVATED.equals(o.getStatus())
                && req.razorpayPaymentId().equals(o.getRazorpayPaymentId());
        if (!alreadyDone) {
            RazorpayPayment payment;
            try {
                payment = razorpay.fetchPayment(req.razorpayPaymentId());
            } catch (RazorpayException e) {
                throw gateway(e);
            }
            CapturedPayment captured = settle(o, payment);
            lifecycle.activatePaid(o.getId(), captured, "VERIFY");
        }
        SubscriptionOrder fresh = orders.findById(o.getId()).orElse(o);
        return new VerifyResult(views.of(fresh), mine.summary(mine.current(ownerUserId)));
    }

    // ── status poll ─────────────────────────────────────────────────────────

    public OrderResult status(UUID ownerUserId, UUID orderId) {
        SubscriptionOrder o = owned(ownerUserId, orderId);
        return new OrderResult(views.of(reconcile(o, false, "STATUS_POLL").order()));
    }

    // ── cancel ──────────────────────────────────────────────────────────────

    public OrderView cancel(UUID ownerUserId, CancelRequest req) {
        if (req == null) throw ApiException.badRequest("INVALID_REQUEST", "Request body is required");
        SubscriptionOrder o = owned(ownerUserId, req.orderId());
        if (SubscriptionOrder.ACTIVATED.equals(o.getStatus()) || SubscriptionOrder.PAID.equals(o.getStatus())) {
            throw ApiException.conflict("ORDER_ALREADY_PAID", "This order is already paid");
        }
        if (!o.isPending()) return views.of(o);
        if (o.getRazorpayOrderId() != null && modeMatches(o)) {
            Reconciled r = reconcile(o, true, "CANCEL_RECHECK");
            o = r.order();
            if (!o.isPending()) return views.of(o);
            if (hasPaymentInFlight(r.payments())) {
                throw ApiException.conflict("PAYMENT_IN_PROGRESS",
                        "A payment for this order is in progress, so it cannot be cancelled now");
            }
        }
        String reason = req.reason() == null || req.reason().isBlank() ? "Cancelled by owner" : req.reason().trim();
        return views.of(lifecycle.cancel(o.getId(), reason, ownerUserId, "SHOP_OWNER"));
    }

    // ── shared Razorpay plumbing ────────────────────────────────────────────

    /**
     * Ask Razorpay what happened to an order and act on it: a captured (or
     * capturable) payment of the right amount activates; failures are counted;
     * a quote past its TTL with nothing paid expires. {@code strict} = throw
     * when Razorpay cannot be asked (used where guessing would lose money).
     */
    public Reconciled reconcile(SubscriptionOrder o, boolean strict, String via) {
        boolean open = o.isPending() || SubscriptionOrder.EXPIRED.equals(o.getStatus())
                || SubscriptionOrder.FAILED.equals(o.getStatus());
        if (!open) return new Reconciled(o, List.of());
        if (o.getRazorpayOrderId() == null) {
            if (o.isPending() && o.getExpiresAt() != null && o.getExpiresAt().isBefore(clock.instant())) {
                o = lifecycle.expire(o.getId());
            }
            return new Reconciled(o, List.of());
        }
        if (!modeMatches(o)) {
            // Never reconcile across TEST/LIVE: the other mode's order id means nothing to these keys.
            return new Reconciled(o, List.of());
        }
        if (!props.isConfigured()) {
            if (strict) throw ApiException.paymentsNotConfigured(props.unavailableReason());
            return new Reconciled(o, List.of());
        }
        List<RazorpayPayment> list;
        try {
            list = razorpay.fetchOrderPayments(o.getRazorpayOrderId());
        } catch (RazorpayException e) {
            if (strict) throw gateway(e);
            log.warn("Reconcile of order {} skipped: {}", o.getOrderNo(), e.getMessage());
            return new Reconciled(o, List.of());
        }
        final long total = o.getTotalPaise();
        Optional<RazorpayPayment> paid = list.stream()
                .filter(p -> p.isCaptured() && p.amount() == total).findFirst();
        if (paid.isEmpty()) {
            Optional<RazorpayPayment> authorized = list.stream()
                    .filter(p -> p.isAuthorized() && p.amount() == total).findFirst();
            if (authorized.isPresent()) {
                RazorpayPayment captured = captureSafely(o, authorized.get());
                if (captured.isCaptured()) paid = Optional.of(captured);
            }
        }
        if (paid.isPresent()) {
            lifecycle.activatePaid(o.getId(), CapturedPayment.from(paid.get()), via);
            return new Reconciled(orders.findById(o.getId()).orElse(o), list);
        }
        long failures = list.stream().filter(RazorpayPayment::isFailed).count();
        if (failures > 0 && o.isPending()) {
            String reason = list.stream().filter(RazorpayPayment::isFailed)
                    .map(RazorpayPayment::errorDescription).filter(d -> d != null && !d.isBlank())
                    .reduce((a, b) -> b).orElse("Payment failed");
            o = lifecycle.recordFailures(o.getId(), failures, reason);
        }
        if (o.isPending() && o.getExpiresAt() != null && o.getExpiresAt().isBefore(clock.instant())
                && !hasPaymentInFlight(list)) {
            o = lifecycle.expire(o.getId());
        }
        return new Reconciled(o, list);
    }

    /** Validate a fetched payment against the order; capture it if only authorized. */
    public CapturedPayment settle(SubscriptionOrder o, RazorpayPayment p) {
        if (p == null || p.orderId() == null || !p.orderId().equals(o.getRazorpayOrderId())) {
            throw ApiException.badRequest("PAYMENT_ORDER_MISMATCH", "Payment does not belong to this order");
        }
        if (p.amount() != o.getTotalPaise()) {
            throw ApiException.conflict("AMOUNT_MISMATCH", "Paid amount does not match the order total");
        }
        if (p.isAuthorized()) p = captureSafely(o, p);
        if (p.isFailed()) {
            lifecycle.recordFailure(o.getId(), p.errorDescription());
            throw ApiException.conflict("PAYMENT_FAILED",
                    p.errorDescription() == null ? "Payment failed" : p.errorDescription());
        }
        if (!p.isCaptured()) {
            throw ApiException.conflict("PAYMENT_NOT_CAPTURED", "Payment is not complete yet");
        }
        return CapturedPayment.from(p);
    }

    /** Capture an authorized payment; if Razorpay already captured it, re-read it. */
    public RazorpayPayment captureSafely(SubscriptionOrder o, RazorpayPayment p) {
        try {
            return razorpay.capture(p.id(), o.getTotalPaise(), "INR");
        } catch (RazorpayException e) {
            try {
                return razorpay.fetchPayment(p.id());
            } catch (RazorpayException again) {
                throw gateway(again);
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private SubscriptionOrder owned(UUID ownerUserId, UUID orderId) {
        if (orderId == null) throw ApiException.badRequest("ORDER_REQUIRED", "orderId is required");
        return orders.findById(orderId)
                .filter(o -> o.getOwnerUserId().equals(ownerUserId))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
    }

    private static void rejectClosed(SubscriptionOrder o) {
        if (SubscriptionOrder.ACTIVATED.equals(o.getStatus()) || SubscriptionOrder.PAID.equals(o.getStatus())) {
            throw ApiException.conflict("ORDER_ALREADY_PAID", "This order is already paid");
        }
        if (!o.isPending()) {
            throw ApiException.conflict("ORDER_CLOSED", "This order can no longer be paid. Please start a new purchase.");
        }
    }

    /** A UPI payment sits in "created" until the customer approves it in their app. */
    private static boolean hasPaymentInFlight(List<RazorpayPayment> payments) {
        return payments.stream().anyMatch(p -> "created".equalsIgnoreCase(p.status()) || p.isAuthorized());
    }

    private void requireConfigured() {
        if (!props.isConfigured()) throw ApiException.paymentsNotConfigured(props.unavailableReason());
    }

    /** TEST/LIVE isolation: only orders stamped with the server's current mode talk to Razorpay. */
    private boolean modeMatches(SubscriptionOrder o) {
        return props.serverMode().equals(o.getPaymentMode());
    }

    private static String channel(String raw) {
        String c = raw == null ? "" : raw.trim().toUpperCase();
        if (!List.of("WEB", "ANDROID", "IOS").contains(c)) {
            throw ApiException.badRequest("INVALID_CHANNEL", "channel must be WEB, ANDROID or IOS");
        }
        return c;
    }

    private static Map<String, String> notes(SubscriptionOrder o) {
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("orderId", o.getId().toString());
        notes.put("orderNo", o.getOrderNo());
        notes.put("ownerUserId", o.getOwnerUserId().toString());
        notes.put("planCode", o.getPlanCode());
        notes.put("shopCount", String.valueOf(o.getShopCount()));
        return notes;
    }

    /** Checkout options that show only the UPI block (contract payment/order.checkout). */
    private JsonNode upiOnlyCheckout() {
        ObjectNode checkout = mapper.createObjectNode();
        ObjectNode method = checkout.putObject("method");
        method.put("upi", true);
        method.put("card", false);
        method.put("netbanking", false);
        method.put("wallet", false);
        method.put("emi", false);
        method.put("paylater", false);
        ObjectNode display = checkout.putObject("config").putObject("display");
        ObjectNode upi = display.putObject("blocks").putObject("upi");
        upi.put("name", "Pay via UPI");
        upi.putArray("instruments").addObject().put("method", "upi");
        display.putArray("sequence").add("block.upi");
        display.putObject("preferences").put("show_default_blocks", false);
        return checkout;
    }

    private static ApiException gateway(RazorpayException e) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "RAZORPAY_ERROR",
                "Payment provider error: " + (e.getMessage() == null ? "unavailable" : e.getMessage()));
    }
}
