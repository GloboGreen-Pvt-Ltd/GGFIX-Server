package com.repairshop.saas.subscription.service;

import com.repairshop.saas.subscription.billing.SubscriptionState;
import com.repairshop.saas.subscription.billing.SubscriptionTerms;
import com.repairshop.saas.subscription.dto.v2.V2Views.PlanView;
import com.repairshop.saas.subscription.dto.v2.V2Views.ShopRef;
import com.repairshop.saas.subscription.entity.Subscription;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.entity.SubscriptionPayment;
import com.repairshop.saas.subscription.entity.SubscriptionShop;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.razorpay.RazorpayPayment;
import com.repairshop.saas.subscription.razorpay.RazorpayProperties;
import com.repairshop.saas.subscription.repository.SubscriptionOrderRepository;
import com.repairshop.saas.subscription.repository.SubscriptionPaymentRepository;
import com.repairshop.saas.subscription.repository.SubscriptionRepository;
import com.repairshop.saas.subscription.repository.SubscriptionShopRepository;
import com.repairshop.saas.subscription.security.CurrentUser.Actor;
import com.repairshop.saas.subscription.service.QuoteService.QuoteContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every state change of a subscription order, each in its own transaction with
 * the order row locked (SELECT … FOR UPDATE).
 *
 * <p>{@link #activatePaid} is THE activation path: checkout verify, status
 * polling and the Razorpay webhook all funnel into it. It is idempotent — the
 * order lock serialises concurrent callers and the unique
 * razorpay_payment_id makes a second call for the same payment a no-op, so a
 * renewal can never be extended twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    public static final String SOURCE_RAZORPAY = "RAZORPAY";
    public static final String SOURCE_TRIAL = "TRIAL";
    public static final String SOURCE_MANUAL = "MANUAL";

    private final SubscriptionOrderRepository orders;
    private final SubscriptionPaymentRepository payments;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionShopRepository subscriptionShops;
    private final PlanCatalogService plans;
    private final SettingsService settings;
    private final NumberSequence numbers;
    private final AuditService audit;
    private final OrderViews views;
    private final Clock clock;
    private final RazorpayProperties razorpay;

    /** A Razorpay payment reduced to what activation checks. */
    public record CapturedPayment(String paymentId, String razorpayOrderId, long amountPaise, String currency,
                                  String status, String method, String email, String contact, String vpa) {
        public static CapturedPayment from(RazorpayPayment p) {
            return new CapturedPayment(p.id(), p.orderId(), p.amount(), p.currency(), p.status(), p.method(),
                    p.email(), p.contact(), p.vpa());
        }
    }

    public enum Outcome { ACTIVATED, ALREADY_PROCESSED, DUPLICATE_PAYMENT, NEEDS_REVIEW }

    public record ActivationResult(Outcome outcome, UUID orderId, UUID paymentId, UUID subscriptionId) {}

    // ── Order creation ──────────────────────────────────────────────────────

    /** Insert a new order with its immutable quote snapshot. */
    @Transactional
    public SubscriptionOrder createOrder(UUID ownerUserId, QuoteContext ctx, String channel, String source,
                                         String status, UUID createdBy, String note, Instant expiresAt) {
        List<ShopRef> shopRefs = ctx.shops().stream().map(s -> new ShopRef(s.id(), s.name())).toList();
        SubscriptionOrder o = SubscriptionOrder.builder()
                .orderNo(numbers.nextOrderNo())
                .ownerUserId(ownerUserId)
                .planId(ctx.plan().getId())
                .planCode(ctx.plan().getCode())
                .planName(ctx.plan().getName())
                .durationLabel(ctx.quote().durationLabel())
                .purpose(ctx.quote().purpose())
                .channel(channel)
                .source(source)
                .shopCount(ctx.quote().shopCount())
                .shopsSnapshot(views.write(shopRefs))
                .quoteSnapshot(views.write(ctx.quote()))
                .planSnapshot(plans.snapshot(ctx.plan()))
                .totalPaise(ctx.quote().totalPaise())
                .currency("INR")
                .paymentMode(razorpay.serverMode())
                .status(status)
                .paymentAttempts(0)
                .createdBy(createdBy)
                .note(note)
                .expiresAt(expiresAt)
                .build();
        return orders.saveAndFlush(o);
    }

    // ── Paid activation ─────────────────────────────────────────────────────

    @Transactional
    public ActivationResult activatePaid(UUID orderId, CapturedPayment p, String via) {
        SubscriptionOrder o = orders.lockById(orderId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));

        // Every check happens before the first write, so a refusal leaves nothing half-done.
        if (p == null || !"captured".equalsIgnoreCase(p.status())) {
            throw ApiException.conflict("PAYMENT_NOT_CAPTURED", "Payment is not complete yet");
        }
        if (o.getRazorpayOrderId() == null || !o.getRazorpayOrderId().equals(p.razorpayOrderId())) {
            throw ApiException.conflict("PAYMENT_ORDER_MISMATCH", "Payment does not belong to this order");
        }
        if (o.getTotalPaise() == null || p.amountPaise() != o.getTotalPaise()) {
            throw ApiException.conflict("AMOUNT_MISMATCH", "Paid amount does not match the order total");
        }
        if (p.currency() != null && !"INR".equalsIgnoreCase(p.currency())) {
            throw ApiException.conflict("CURRENCY_MISMATCH", "Payment currency must be INR");
        }

        Optional<SubscriptionPayment> existing = payments.findByRazorpayPaymentId(p.paymentId());
        if (existing.isPresent()) {
            return new ActivationResult(Outcome.ALREADY_PROCESSED, o.getId(), existing.get().getId(),
                    o.getSubscriptionId());
        }

        Instant now = clock.instant();
        UUID actorId = "WEBHOOK".equals(via) ? null : o.getOwnerUserId();
        String actorRole = "WEBHOOK".equals(via) ? AuditService.SYSTEM_ROLE : "SHOP_OWNER";

        if (!razorpay.serverMode().equals(o.getPaymentMode())) {
            // TEST/LIVE isolation: a payment for an order created in the other mode is
            // recorded for review but never activates anything on this server.
            SubscriptionPayment review = payments.save(paymentRow(o, p, now, null,
                    SubscriptionPayment.ACTIVATION_NEEDS_REVIEW));
            if (o.isPending() || SubscriptionOrder.FAILED.equals(o.getStatus())
                    || SubscriptionOrder.EXPIRED.equals(o.getStatus())) {
                o.setStatus(SubscriptionOrder.PAID);
                o.setRazorpayPaymentId(p.paymentId());
                o.setPaymentMethod(p.method());
                o.setPaidAt(now);
            }
            o.setFailureReason("Payment for a " + o.getPaymentMode() + " mode order received while the server runs in "
                    + razorpay.serverMode() + " mode — not activated, needs review");
            orders.save(o);
            Map<String, Object> d = details(o, p, via);
            d.put("orderPaymentMode", o.getPaymentMode());
            d.put("serverMode", razorpay.serverMode());
            audit.log(actorId, actorRole, "PAYMENT_MODE_MISMATCH", "ORDER", o.getId().toString(), d);
            return new ActivationResult(Outcome.NEEDS_REVIEW, o.getId(), review.getId(), null);
        }

        if (SubscriptionOrder.ACTIVATED.equals(o.getStatus())) {
            // A second, different payment on an already-activated order: keep the
            // money on record for a refund, never activate twice.
            SubscriptionPayment dup = payments.save(paymentRow(o, p, now, o.getSubscriptionId(),
                    SubscriptionPayment.ACTIVATION_DUPLICATE));
            audit.log(actorId, actorRole, "DUPLICATE_PAYMENT", "ORDER", o.getId().toString(),
                    details(o, p, via));
            return new ActivationResult(Outcome.DUPLICATE_PAYMENT, o.getId(), dup.getId(), o.getSubscriptionId());
        }

        if (SubscriptionOrder.CANCELLED.equals(o.getStatus()) || SubscriptionOrder.PAID.equals(o.getStatus())) {
            // Paid after the owner (or a newer purchase) cancelled it — the owner may
            // already be on another plan, so an admin decides: refund or manual-activate.
            SubscriptionPayment review = payments.save(paymentRow(o, p, now, null,
                    SubscriptionPayment.ACTIVATION_NEEDS_REVIEW));
            if (SubscriptionOrder.CANCELLED.equals(o.getStatus())) {
                o.setStatus(SubscriptionOrder.PAID);
                o.setRazorpayPaymentId(p.paymentId());
                o.setPaymentMethod(p.method());
                o.setPaidAt(now);
                o.setFailureReason("Payment received after the order was cancelled — refund it or activate manually");
                orders.save(o);
            }
            audit.log(actorId, actorRole, "PAYMENT_NEEDS_REVIEW", "ORDER", o.getId().toString(), details(o, p, via));
            return new ActivationResult(Outcome.NEEDS_REVIEW, o.getId(), review.getId(), null);
        }

        // CREATED / AWAITING_PAYMENT / FAILED / EXPIRED: money is captured, so activate.
        Subscription sub = applyTerm(o, now, SOURCE_RAZORPAY, o.getTotalPaise());
        SubscriptionPayment payment = payments.save(paymentRow(o, p, now, sub.getId(),
                SubscriptionPayment.ACTIVATION_ACTIVATED));

        o.setStatus(SubscriptionOrder.ACTIVATED);
        o.setRazorpayPaymentId(p.paymentId());
        o.setPaymentMethod(p.method());
        o.setPaidAt(now);
        o.setActivatedAt(now);
        o.setSubscriptionId(sub.getId());
        o.setFailureReason(null);
        orders.save(o);

        audit.log(actorId, actorRole, "SUBSCRIPTION_ACTIVATED", "SUBSCRIPTION", sub.getId().toString(),
                details(o, p, via));
        return new ActivationResult(Outcome.ACTIVATED, o.getId(), payment.getId(), sub.getId());
    }

    // ── Activation without payment (trial / admin manual) ───────────────────

    @Transactional
    public SubscriptionOrder activateWithoutPayment(UUID ownerUserId, QuoteContext ctx, String channel,
                                                    String source, Actor actor, String reason) {
        Instant now = clock.instant();
        SubscriptionOrder o = createOrder(ownerUserId, ctx, channel, source, SubscriptionOrder.ACTIVATED,
                actor != null ? actor.userId() : null, reason, null);
        Subscription sub = applyTerm(o, now, source, 0L);
        o.setStatus(SubscriptionOrder.ACTIVATED);
        o.setActivatedAt(now);
        o.setSubscriptionId(sub.getId());
        o = orders.save(o);

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("orderNo", o.getOrderNo());
        d.put("ownerUserId", ownerUserId);
        d.put("planCode", o.getPlanCode());
        d.put("shopIds", ctx.quote().shopIds());
        d.put("termEndsAt", o.getTermEndsAt());
        if (reason != null) d.put("reason", reason);
        audit.log(actor, SOURCE_TRIAL.equals(source) ? "TRIAL_ACTIVATED" : "MANUAL_ACTIVATED",
                "SUBSCRIPTION", sub.getId().toString(), d);
        return o;
    }

    // ── Other transitions ──────────────────────────────────────────────────

    @Transactional
    public SubscriptionOrder attachRazorpayOrder(UUID orderId, String razorpayOrderId) {
        SubscriptionOrder o = lock(orderId);
        if (o.isPending()) {
            if (o.getRazorpayOrderId() == null) o.setRazorpayOrderId(razorpayOrderId);
            o.setStatus(SubscriptionOrder.AWAITING_PAYMENT);
            o = orders.save(o);
        }
        return o;
    }

    @Transactional
    public SubscriptionOrder markAwaiting(UUID orderId) {
        SubscriptionOrder o = lock(orderId);
        if (SubscriptionOrder.CREATED.equals(o.getStatus())) {
            o.setStatus(SubscriptionOrder.AWAITING_PAYMENT);
            o = orders.save(o);
        }
        return o;
    }

    /** A failed attempt; the order turns FAILED once paymentRetryLimit attempts have failed. */
    @Transactional
    public SubscriptionOrder recordFailures(UUID orderId, long failedAttempts, String reason) {
        SubscriptionOrder o = lock(orderId);
        if (!o.isPending()) return o;
        int attempts = (int) Math.max(o.getPaymentAttempts() == null ? 0 : o.getPaymentAttempts(), failedAttempts);
        if (attempts == (o.getPaymentAttempts() == null ? 0 : o.getPaymentAttempts())) return o;
        o.setPaymentAttempts(attempts);
        o.setFailureReason(reason == null ? "Payment failed" : reason);
        if (attempts >= settings.get().paymentRetryLimit()) o.setStatus(SubscriptionOrder.FAILED);
        o = orders.save(o);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("orderNo", o.getOrderNo());
        d.put("attempts", attempts);
        d.put("reason", o.getFailureReason());
        audit.log(null, AuditService.SYSTEM_ROLE, "PAYMENT_FAILED", "ORDER", orderId.toString(), d);
        return o;
    }

    @Transactional
    public SubscriptionOrder recordFailure(UUID orderId, String reason) {
        SubscriptionOrder o = lock(orderId);
        long next = (o.getPaymentAttempts() == null ? 0 : o.getPaymentAttempts()) + 1L;
        return recordFailures(orderId, next, reason);
    }

    @Transactional
    public SubscriptionOrder cancel(UUID orderId, String reason, UUID actorUserId, String actorRole) {
        SubscriptionOrder o = lock(orderId);
        if (!o.isPending()) return o;
        o.setStatus(SubscriptionOrder.CANCELLED);
        o.setCancelledAt(clock.instant());
        o.setNote(reason);
        o = orders.save(o);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("orderNo", o.getOrderNo());
        d.put("reason", reason);
        audit.log(actorUserId, actorRole, "ORDER_CANCELLED", "ORDER", orderId.toString(), d);
        return o;
    }

    @Transactional
    public SubscriptionOrder expire(UUID orderId) {
        SubscriptionOrder o = lock(orderId);
        if (!o.isPending()) return o;
        o.setStatus(SubscriptionOrder.EXPIRED);
        o.setFailureReason("Quote expired before payment");
        return orders.save(o);
    }

    // ── The term itself ─────────────────────────────────────────────────────

    /**
     * Point the owner's `subscriptions` row at the order's plan and replace its
     * covered shops. RENEW (same plan code, still ACTIVE) extends from the
     * current end; anything else starts now. Terms are calendar months/days in
     * Asia/Kolkata. Writes the same columns the legacy endpoints and the limit
     * engine read (status, inactive_date, subscription_end_date, limits).
     */
    Subscription applyTerm(SubscriptionOrder o, Instant now, String source, long paidPaise) {
        PlanView plan = plans.readSnapshot(o.getPlanSnapshot());
        if (plan == null) throw new IllegalStateException("Order " + o.getOrderNo() + " has no plan snapshot");
        List<ShopRef> shops = views.shops(o.getShopsSnapshot());
        SettingsService.Settings s = settings.get();

        List<Subscription> rows = subscriptions.lockByOwner(o.getOwnerUserId());
        Subscription sub = rows.isEmpty()
                ? Subscription.builder().ownerUserId(o.getOwnerUserId()).build()
                : rows.get(0);
        String state = rows.isEmpty() ? SubscriptionState.NONE : SubscriptionState.status(sub, now);
        boolean trialPlan = PlanCatalogService.TRIAL_TYPE.equals(plan.planType());
        boolean extend = !trialPlan && SubscriptionState.ACTIVE.equals(state)
                && plan.code().equalsIgnoreCase(sub.getPlanCode());

        Instant termStart = extend ? SubscriptionState.endsAt(sub) : now;
        Instant end = trialPlan
                ? SubscriptionTerms.termEnd(now, s.trialDays(), SubscriptionTerms.DAYS)
                : SubscriptionTerms.termEnd(termStart, plan.durationValue(), plan.durationUnit());
        Instant periodStart = extend && sub.getSubscriptionStartDate() != null ? sub.getSubscriptionStartDate() : now;

        sub.setPlanCode(plan.code());
        sub.setSubscriptionType(plan.code());
        sub.setPlanId(plan.id());
        sub.setPlanSnapshot(o.getPlanSnapshot());
        sub.setOrderId(o.getId());
        sub.setSource(source);
        sub.setStatus(trialPlan ? SubscriptionState.DB_TRIAL_STATUS : SubscriptionState.ACTIVE);
        if (trialPlan) {
            sub.setTrialStartDate(now);
            sub.setTrialEndDate(end);
            sub.setSubscriptionStartDate(null);
            sub.setSubscriptionEndDate(null);
        } else {
            sub.setSubscriptionStartDate(periodStart);
            sub.setSubscriptionEndDate(end);
        }
        sub.setActiveDate(periodStart);
        sub.setInactiveDate(end);
        sub.setCurrentPeriodEnd(end);
        if (sub.getStartedAt() == null) sub.setStartedAt(now);
        sub.setShopCount(shops.size());
        sub.setShopLimit(trialPlan ? s.trialMaxShops() : shops.size());
        sub.setEmployeeLimit(plan.employeeLimit());
        sub.setSellLimit(plan.sellOrderLimit());
        sub.setPickupServiceEnabled(plan.pickupEnabled());
        sub.setBuyProductUnlimited(plan.buyOrderLimit() == null);
        sub.setSellProductUnlimited(plan.sellOrderLimit() == null);
        sub.setPriceAmount(SubscriptionTerms.rupeesOf(paidPaise));
        sub.setCancelledAt(null);
        sub.setCancelReason(null);
        sub = subscriptions.save(sub);

        Map<UUID, SubscriptionShop> previous = subscriptionShops.findBySubscriptionId(sub.getId()).stream()
                .collect(Collectors.toMap(SubscriptionShop::getShopId, Function.identity(), (a, b) -> a));
        subscriptionShops.deleteAllForSubscription(sub.getId());
        List<SubscriptionShop> covered = new ArrayList<>();
        for (ShopRef shop : shops) {
            SubscriptionShop before = previous.get(shop.shopId());
            covered.add(SubscriptionShop.builder()
                    .subscriptionId(sub.getId())
                    .shopId(shop.shopId())
                    .orderId(o.getId())
                    .startsAt(extend && before != null && before.getStartsAt() != null ? before.getStartsAt() : now)
                    .endsAt(end)
                    .status(trialPlan ? SubscriptionState.TRIAL : SubscriptionState.ACTIVE)
                    .build());
        }
        subscriptionShops.saveAll(covered);

        o.setTermStartsAt(termStart);
        o.setTermEndsAt(end);
        o.setSubscriptionId(sub.getId());
        return sub;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private SubscriptionOrder lock(UUID orderId) {
        return orders.lockById(orderId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
    }

    private SubscriptionPayment paymentRow(SubscriptionOrder o, CapturedPayment p, Instant now, UUID subscriptionId,
                                           String activationStatus) {
        return SubscriptionPayment.builder()
                .orderId(o.getId())
                .ownerUserId(o.getOwnerUserId())
                .subscriptionId(subscriptionId)
                .razorpayOrderId(p.razorpayOrderId())
                .razorpayPaymentId(p.paymentId())
                .receiptNo(numbers.nextReceiptNo())
                .amountPaise(p.amountPaise())
                .currency("INR")
                .paymentMode(o.getPaymentMode() != null ? o.getPaymentMode() : razorpay.serverMode())
                .method(p.method())
                .vpa(p.vpa())
                .payerEmail(p.email())
                .payerContact(p.contact())
                .status(SubscriptionPayment.CAPTURED)
                .activationStatus(activationStatus)
                .refundedPaise(0L)
                .paidAt(now)
                .build();
    }

    private static Map<String, Object> details(SubscriptionOrder o, CapturedPayment p, String via) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("orderNo", o.getOrderNo());
        d.put("planCode", o.getPlanCode());
        d.put("shopCount", o.getShopCount());
        d.put("amountPaise", p.amountPaise());
        d.put("razorpayOrderId", p.razorpayOrderId());
        d.put("razorpayPaymentId", p.paymentId());
        d.put("via", via);
        return d;
    }
}
