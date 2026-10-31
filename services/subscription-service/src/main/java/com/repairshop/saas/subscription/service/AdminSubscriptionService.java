package com.repairshop.saas.subscription.service;

import com.repairshop.saas.subscription.billing.SubscriptionState;
import com.repairshop.saas.subscription.billing.SubscriptionTerms;
import com.repairshop.saas.subscription.dto.v2.V2Requests.ExtendRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.ManualActivateRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.RefundRequest;
import com.repairshop.saas.subscription.dto.v2.V2Views.AdminPaymentRow;
import com.repairshop.saas.subscription.dto.v2.V2Views.AdminSubscriptionDetail;
import com.repairshop.saas.subscription.dto.v2.V2Views.AdminSubscriptionRow;
import com.repairshop.saas.subscription.entity.Subscription;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.entity.SubscriptionPayment;
import com.repairshop.saas.subscription.entity.SubscriptionShop;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.razorpay.RazorpayClient;
import com.repairshop.saas.subscription.razorpay.RazorpayException;
import com.repairshop.saas.subscription.razorpay.RazorpayProperties;
import com.repairshop.saas.subscription.repository.SubscriptionOrderRepository;
import com.repairshop.saas.subscription.repository.SubscriptionPaymentRepository;
import com.repairshop.saas.subscription.repository.SubscriptionRepository;
import com.repairshop.saas.subscription.repository.SubscriptionShopRepository;
import com.repairshop.saas.subscription.security.CurrentUser.Actor;
import com.repairshop.saas.subscription.service.QuoteService.QuoteContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Management: subscriptions list/detail, extend, cancel, manual activation, payments, refunds. */
@Service
@RequiredArgsConstructor
public class AdminSubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final SubscriptionShopRepository subscriptionShops;
    private final SubscriptionOrderRepository orders;
    private final SubscriptionPaymentRepository payments;
    private final PlanCatalogService plans;
    private final SettingsService settings;
    private final OwnerDirectory directory;
    private final QuoteService quotes;
    private final OrderLifecycleService lifecycle;
    private final MySubscriptionService mine;
    private final OrderViews views;
    private final RazorpayClient razorpay;
    private final RazorpayProperties props;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    // ── subscriptions ──────────────────────────────────────────────────────

    public List<AdminSubscriptionRow> list(String status, String q) {
        String filter = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase();
        List<Subscription> rows = subscriptions.findAllByOrderByCreatedAtDesc();
        Set<UUID> owners = rows.stream().map(Subscription::getOwnerUserId).filter(id -> id != null)
                .collect(Collectors.toSet());
        Context ctx = context(owners);
        Map<UUID, String> modes = orderModes(rows);
        Instant now = clock.instant();
        int alertDays = settings.get().expiryAlertDays();
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);

        List<AdminSubscriptionRow> out = new ArrayList<>();
        for (Subscription s : rows) {
            AdminSubscriptionRow row = row(s, ctx, now, modes.get(s.getOrderId()));
            if (!matchesStatus(filter, row, ctx, now, alertDays)) continue;
            if (needle != null && !needle.isEmpty() && !matchesQuery(needle, row, ctx)) continue;
            out.add(row);
        }
        return out;
    }

    public AdminSubscriptionDetail detail(UUID id) {
        Subscription s = subscriptions.findById(id)
                .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription not found"));
        Context ctx = context(s.getOwnerUserId() == null ? Set.of() : Set.of(s.getOwnerUserId()));
        Instant now = clock.instant();
        AdminSubscriptionRow r = row(s, ctx, now, orderModes(List.of(s)).get(s.getOrderId()));
        OwnerDirectory.OwnerInfo owner = ctx.owners.get(s.getOwnerUserId());
        List<SubscriptionPayment> pays = s.getOwnerUserId() == null ? List.of()
                : payments.findByOwnerUserIdOrderByPaidAtDesc(s.getOwnerUserId());
        List<SubscriptionOrder> ords = s.getOwnerUserId() == null ? List.of()
                : orders.findByOwnerUserIdOrderByCreatedAtDesc(s.getOwnerUserId());
        Map<UUID, SubscriptionOrder> byId = ords.stream()
                .collect(Collectors.toMap(SubscriptionOrder::getId, Function.identity(), (a, b) -> a));
        PlanCatalogService.PlanInfo info = plans.describe(s);
        return new AdminSubscriptionDetail(
                r.id(), r.ownerUserId(), r.ownerName(), r.businessName(), r.ownerPhone(),
                owner == null ? null : owner.email(), r.planCode(), r.planName(), info.planType(), r.durationLabel(),
                r.shopCount(), r.employeeUsage(), r.startedAt(), r.endsAt(), r.daysRemaining(), r.amountPaidPaise(),
                r.paymentStatus(), r.status(), r.legacy(), s.getSource(), s.getCancelledAt(), s.getCancelReason(),
                mine.entitlements(s), mine.coveredShops(s.getOwnerUserId(), s),
                pays.stream().map(p -> paymentRow(p, byId.get(p.getOrderId()), ctx)).toList(),
                ords.stream().map(views::of).toList(), r.paymentMode());
    }

    @Transactional
    public AdminSubscriptionDetail extend(UUID id, ExtendRequest req, Actor actor) {
        int days = req == null || req.days() == null ? 0 : req.days();
        int months = req == null || req.months() == null ? 0 : req.months();
        String reason = requireReason(req == null ? null : req.reason());
        if (days < 0 || months < 0 || (days == 0 && months == 0) || days > 3650 || months > 120) {
            throw ApiException.badRequest("INVALID_EXTENSION", "Give a positive number of days and/or months");
        }
        Subscription s = subscriptions.lockById(id)
                .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription not found"));
        Instant now = clock.instant();
        Instant before = SubscriptionState.endsAt(s);
        Instant base = before != null && before.isAfter(now) ? before : now;
        Instant end = SubscriptionTerms.plus(base, days, months);

        s.setInactiveDate(end);
        s.setCurrentPeriodEnd(end);
        if (SubscriptionState.isTrial(s)) {
            s.setTrialEndDate(end);
            s.setStatus(SubscriptionState.DB_TRIAL_STATUS);
        } else {
            s.setSubscriptionEndDate(end);
            s.setStatus(SubscriptionState.ACTIVE);
        }
        s.setCancelledAt(null);
        s.setCancelReason(null);
        subscriptions.save(s);
        for (SubscriptionShop row : subscriptionShops.findBySubscriptionId(s.getId())) {
            row.setEndsAt(end);
            row.setStatus(SubscriptionState.isTrial(s) ? SubscriptionState.TRIAL : SubscriptionState.ACTIVE);
            subscriptionShops.save(row);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("days", days);
        d.put("months", months);
        d.put("from", before);
        d.put("to", end);
        d.put("reason", reason);
        audit.log(actor, "SUBSCRIPTION_EXTENDED", "SUBSCRIPTION", id.toString(), d);
        return detail(id);
    }

    @Transactional
    public AdminSubscriptionDetail cancel(UUID id, String reasonRaw, Actor actor) {
        String reason = requireReason(reasonRaw);
        Subscription s = subscriptions.lockById(id)
                .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription not found"));
        String before = s.getStatus();
        Instant now = clock.instant();
        s.setStatus(SubscriptionState.CANCELLED);
        s.setCancelledAt(now);
        s.setCancelReason(reason);
        subscriptions.save(s);
        for (SubscriptionShop row : subscriptionShops.findBySubscriptionId(s.getId())) {
            row.setStatus(SubscriptionState.CANCELLED);
            subscriptionShops.save(row);
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("fromStatus", before);
        d.put("reason", reason);
        audit.log(actor, "SUBSCRIPTION_CANCELLED", "SUBSCRIPTION", id.toString(), d);
        return detail(id);
    }

    /** Admin grants a plan with no payment row (source MANUAL); the owner's shops are still verified. */
    public AdminSubscriptionDetail manualActivate(ManualActivateRequest req, Actor actor) {
        if (req == null || req.ownerUserId() == null) {
            throw ApiException.badRequest("OWNER_REQUIRED", "ownerUserId is required");
        }
        String reason = requireReason(req.reason());
        OwnerDirectory.OwnerInfo owner = directory.owner(req.ownerUserId())
                .orElseThrow(() -> ApiException.notFound("OWNER_NOT_FOUND", "Shop owner not found"));
        if (owner.role() != null && !"SHOP_OWNER".equalsIgnoreCase(owner.role())) {
            throw ApiException.badRequest("NOT_A_SHOP_OWNER", "That user is not a shop owner");
        }
        var plan = plans.requireByCode(req.planCode());
        QuoteContext ctx;
        if (PlanCatalogService.TRIAL_TYPE.equals(plan.getPlanType())) {
            SettingsService.Settings s = settings.get();
            ctx = quotes.trial(plan, quotes.validateShops(req.ownerUserId(), req.shopIds(), s.trialMaxShops(), null), s);
        } else {
            var shops = quotes.validateShops(req.ownerUserId(), req.shopIds(), plan.getShopLimit(), null);
            var q = quotes.compute(plan, shops.stream().map(OwnerDirectory.ShopInfo::id).toList(), shops.size(),
                    quotes.current(req.ownerUserId()), null, settings.get());
            ctx = new QuoteContext(plan, q, shops);
        }
        SubscriptionOrder o = lifecycle.activateWithoutPayment(req.ownerUserId(), ctx, "ADMIN",
                OrderLifecycleService.SOURCE_MANUAL, actor, reason);
        return detail(o.getSubscriptionId());
    }

    // ── payments ───────────────────────────────────────────────────────────

    public List<AdminPaymentRow> payments(String status, String q) {
        String filter = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase();
        List<SubscriptionPayment> list = payments.findAllByOrderByPaidAtDesc();
        Map<UUID, SubscriptionOrder> byId = orders.findAllById(
                        list.stream().map(SubscriptionPayment::getOrderId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SubscriptionOrder::getId, Function.identity(), (a, b) -> a));
        // Unpaid Razorpay orders appear as PENDING / FAILED rows (paymentId null), so the
        // Razorpay-style filters (pending, failed) have something to show.
        List<SubscriptionOrder> unpaid = orders.findByStatusIn(List.of(SubscriptionOrder.CREATED,
                        SubscriptionOrder.AWAITING_PAYMENT, SubscriptionOrder.FAILED)).stream()
                .filter(o -> OrderLifecycleService.SOURCE_RAZORPAY.equals(o.getSource()))
                .sorted(Comparator.comparing(SubscriptionOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Set<UUID> owners = new HashSet<>();
        list.forEach(p -> owners.add(p.getOwnerUserId()));
        unpaid.forEach(o -> owners.add(o.getOwnerUserId()));
        Context ctx = context(owners);
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);

        List<AdminPaymentRow> rows = new ArrayList<>();
        list.forEach(p -> rows.add(paymentRow(p, byId.get(p.getOrderId()), ctx)));
        unpaid.forEach(o -> rows.add(unpaidRow(o, ctx)));
        return rows.stream()
                .filter(r -> matchesPaymentFilter(filter, r))
                .filter(r -> needle == null || needle.isEmpty() || contains(needle, r.ownerName(), r.businessName(),
                        r.planName(), r.receiptNo(), r.orderNo(), r.razorpayPaymentId(), r.razorpayOrderId()))
                .toList();
    }

    /**
     * Case-insensitive; accepts Razorpay-style values (captured | authorized |
     * failed | refunded | pending) as well as our own statuses. "refunded"
     * includes partial refunds; "authorized" is always empty because this
     * service captures authorized payments immediately.
     */
    private static boolean matchesPaymentFilter(String filter, AdminPaymentRow r) {
        return switch (filter) {
            case "ALL" -> true;
            case "REFUNDED" -> SubscriptionPayment.REFUNDED.equals(r.status())
                    || SubscriptionPayment.PARTIALLY_REFUNDED.equals(r.status());
            case "AUTHORIZED" -> false;
            default -> filter.equals(r.status()) || filter.equals(r.activationStatus());
        };
    }

    private AdminPaymentRow unpaidRow(SubscriptionOrder o, Context ctx) {
        OwnerDirectory.OwnerInfo owner = ctx.owners.get(o.getOwnerUserId());
        return new AdminPaymentRow(null, o.getRazorpayOrderId(), null, owner == null ? null : owner.name(),
                ctx.businessNames.get(o.getOwnerUserId()), o.getPlanName(),
                o.getTotalPaise() == null ? 0 : o.getTotalPaise(), null, null,
                SubscriptionOrder.FAILED.equals(o.getStatus()) ? "FAILED" : "PENDING", o.getStatus(), null,
                o.getOrderNo(), 0, o.getPaymentMode());
    }

    public AdminPaymentRow refund(UUID paymentId, RefundRequest req, Actor actor) {
        if (!props.isConfigured()) throw ApiException.paymentsNotConfigured(props.unavailableReason());
        String reason = requireReason(req == null ? null : req.reason());
        SubscriptionPayment p = payments.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "Payment not found"));
        if (!props.serverMode().equals(p.getPaymentMode())) {
            throw ApiException.conflict("PAYMENT_MODE_MISMATCH", "This payment was made in " + p.getPaymentMode()
                    + " mode; refund it from the Razorpay " + p.getPaymentMode() + " dashboard");
        }
        long refundable = p.getAmountPaise() - (p.getRefundedPaise() == null ? 0 : p.getRefundedPaise());
        long amount = req == null || req.amountPaise() == null ? refundable : req.amountPaise();
        if (amount <= 0 || amount > refundable) {
            throw ApiException.badRequest("INVALID_REFUND", "Refund must be between 1 and " + refundable + " paise");
        }
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("receiptNo", p.getReceiptNo());
        notes.put("reason", reason.length() > 250 ? reason.substring(0, 250) : reason);
        String refundId;
        try {
            refundId = razorpay.refund(p.getRazorpayPaymentId(), amount, notes);
        } catch (RazorpayException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RAZORPAY_ERROR", "Refund failed: " + e.getMessage());
        }
        final String rid = refundId;
        return tx.execute(status -> recordRefund(paymentId, amount, rid, reason, actor));
    }

    private AdminPaymentRow recordRefund(UUID paymentId, long amount, String refundId, String reason, Actor actor) {
        SubscriptionPayment p = payments.lockById(paymentId).orElseThrow();
        long refunded = (p.getRefundedPaise() == null ? 0 : p.getRefundedPaise()) + amount;
        p.setRefundedPaise(Math.min(refunded, p.getAmountPaise()));
        p.setStatus(p.getRefundedPaise() >= p.getAmountPaise()
                ? SubscriptionPayment.REFUNDED : SubscriptionPayment.PARTIALLY_REFUNDED);
        p.setLastRefundId(refundId);
        p.setRefundReason(reason);
        p.setRefundedAt(clock.instant());
        payments.save(p);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("amountPaise", amount);
        d.put("razorpayRefundId", refundId);
        d.put("receiptNo", p.getReceiptNo());
        d.put("reason", reason);
        audit.log(actor, "PAYMENT_REFUNDED", "PAYMENT", paymentId.toString(), d);
        SubscriptionOrder o = orders.findById(p.getOrderId()).orElse(null);
        return paymentRow(p, o, context(Set.of(p.getOwnerUserId())));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private record Context(Map<UUID, OwnerDirectory.OwnerInfo> owners, Map<UUID, String> businessNames,
                           Map<UUID, Long> employees, Set<UUID> pendingOwners,
                           Map<UUID, SubscriptionPayment> latestPayment) {}

    private Context context(Collection<UUID> ownerIds) {
        Set<UUID> pending = new HashSet<>();
        for (SubscriptionOrder o : orders.findByStatusIn(CheckoutService.PENDING)) pending.add(o.getOwnerUserId());
        Map<UUID, SubscriptionPayment> latest = new LinkedHashMap<>();
        for (SubscriptionPayment p : payments.findAllByOrderByPaidAtDesc()) latest.putIfAbsent(p.getOwnerUserId(), p);
        return new Context(directory.owners(ownerIds), directory.businessNames(ownerIds),
                directory.activeEmployeesByOwner(ownerIds), pending, latest);
    }

    /** TEST | LIVE of the order behind each v2 subscription (null for legacy rows). */
    private Map<UUID, String> orderModes(List<Subscription> rows) {
        Set<UUID> ids = rows.stream().map(Subscription::getOrderId).filter(id -> id != null).collect(Collectors.toSet());
        if (ids.isEmpty()) return new java.util.HashMap<>();   // HashMap: get(null) must work for legacy rows
        Map<UUID, String> out = new java.util.HashMap<>();
        for (SubscriptionOrder o : orders.findAllById(ids)) {
            if (o.getPaymentMode() != null) out.put(o.getId(), o.getPaymentMode());
        }
        return out;
    }

    private AdminSubscriptionRow row(Subscription s, Context ctx, Instant now, String paymentMode) {
        OwnerDirectory.OwnerInfo owner = ctx.owners.get(s.getOwnerUserId());
        PlanCatalogService.PlanInfo info = plans.describe(s);
        String status = SubscriptionState.status(s, now);
        Instant end = SubscriptionState.endsAt(s);
        return new AdminSubscriptionRow(
                s.getId(), s.getOwnerUserId(), owner == null ? null : owner.name(),
                ctx.businessNames.get(s.getOwnerUserId()), owner == null ? null : owner.phone(),
                s.getPlanCode(), info.name(), info.durationLabel(), s.getShopCount(),
                ctx.employees.getOrDefault(s.getOwnerUserId(), 0L), SubscriptionState.startedAt(s), end,
                SubscriptionState.isRunning(status) ? SubscriptionState.daysRemaining(end, now) : 0,
                SubscriptionTerms.paiseOf(s.getPriceAmount()), paymentStatus(s, ctx), status, s.getPlanId() == null,
                paymentMode);
    }

    /** PENDING (unpaid order open) › REFUNDED/PARTIALLY_REFUNDED › PAID › MANUAL / TRIAL / NONE. */
    private static String paymentStatus(Subscription s, Context ctx) {
        if (ctx.pendingOwners.contains(s.getOwnerUserId())) return "PENDING";
        SubscriptionPayment latest = ctx.latestPayment.get(s.getOwnerUserId());
        if (latest != null) {
            return SubscriptionPayment.CAPTURED.equals(latest.getStatus()) ? "PAID" : latest.getStatus();
        }
        if (OrderLifecycleService.SOURCE_MANUAL.equals(s.getSource())) return "MANUAL";
        if (SubscriptionState.isTrial(s)) return "TRIAL";
        return "NONE";
    }

    private AdminPaymentRow paymentRow(SubscriptionPayment p, SubscriptionOrder o, Context ctx) {
        OwnerDirectory.OwnerInfo owner = ctx.owners.get(p.getOwnerUserId());
        return new AdminPaymentRow(p.getId(), p.getRazorpayOrderId(), p.getRazorpayPaymentId(),
                owner == null ? null : owner.name(), ctx.businessNames.get(p.getOwnerUserId()),
                o == null ? null : o.getPlanName(), p.getAmountPaise(), p.getMethod(), p.getPaidAt(), p.getStatus(),
                p.getActivationStatus(), p.getReceiptNo(), o == null ? null : o.getOrderNo(),
                p.getRefundedPaise() == null ? 0 : p.getRefundedPaise(), p.getPaymentMode());
    }

    private static boolean matchesStatus(String filter, AdminSubscriptionRow row, Context ctx, Instant now,
                                         int alertDays) {
        return switch (filter) {
            case "ALL" -> true;
            case "PENDING" -> ctx.pendingOwners.contains(row.ownerUserId());
            case "EXPIRING" -> SubscriptionState.isRunning(row.status()) && row.endsAt() != null
                    && row.endsAt().isBefore(now.plusSeconds(alertDays * 86_400L));
            default -> filter.equals(row.status());
        };
    }

    private static boolean matchesQuery(String needle, AdminSubscriptionRow r, Context ctx) {
        OwnerDirectory.OwnerInfo owner = ctx.owners.get(r.ownerUserId());
        return contains(needle, r.ownerName(), r.businessName(), r.ownerPhone(), r.planCode(), r.planName(),
                owner == null ? null : owner.email(), r.id() == null ? null : r.id().toString(),
                r.ownerUserId() == null ? null : r.ownerUserId().toString());
    }

    private static boolean contains(String needle, String... values) {
        for (String v : values) {
            if (v != null && v.toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("REASON_REQUIRED", "A reason is required");
        return reason.trim();
    }
}
