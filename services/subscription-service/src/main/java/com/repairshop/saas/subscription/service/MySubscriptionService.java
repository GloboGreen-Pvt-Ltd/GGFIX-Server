package com.repairshop.saas.subscription.service;

import com.repairshop.saas.common.subscription.SubscriptionCapability;
import com.repairshop.saas.common.subscription.SubscriptionFeature;
import com.repairshop.saas.common.subscription.SubscriptionPlan;
import com.repairshop.saas.subscription.billing.SubscriptionState;
import com.repairshop.saas.subscription.billing.SubscriptionTerms;
import com.repairshop.saas.subscription.dto.v2.V2Views.CatalogView;
import com.repairshop.saas.subscription.dto.v2.V2Views.CheckoutFlags;
import com.repairshop.saas.subscription.dto.v2.V2Views.Coverage;
import com.repairshop.saas.subscription.dto.v2.V2Views.CoveredShop;
import com.repairshop.saas.subscription.dto.v2.V2Views.EligibleShop;
import com.repairshop.saas.subscription.dto.v2.V2Views.EntitlementsView;
import com.repairshop.saas.subscription.dto.v2.V2Views.IssuedBy;
import com.repairshop.saas.subscription.dto.v2.V2Views.MeView;
import com.repairshop.saas.subscription.dto.v2.V2Views.OrderView;
import com.repairshop.saas.subscription.dto.v2.V2Views.PaymentHistoryItem;
import com.repairshop.saas.subscription.dto.v2.V2Views.PlanView;
import com.repairshop.saas.subscription.dto.v2.V2Views.QuoteView;
import com.repairshop.saas.subscription.dto.v2.V2Views.ReceiptOwner;
import com.repairshop.saas.subscription.dto.v2.V2Views.ReceiptView;
import com.repairshop.saas.subscription.dto.v2.V2Views.ShopRef;
import com.repairshop.saas.subscription.dto.v2.V2Views.SubscriptionSummary;
import com.repairshop.saas.subscription.dto.v2.V2Views.UsageView;
import com.repairshop.saas.subscription.entity.Subscription;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.entity.SubscriptionPayment;
import com.repairshop.saas.subscription.entity.SubscriptionShop;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.razorpay.RazorpayProperties;
import com.repairshop.saas.subscription.repository.SubscriptionOrderRepository;
import com.repairshop.saas.subscription.repository.SubscriptionPaymentRepository;
import com.repairshop.saas.subscription.repository.SubscriptionRepository;
import com.repairshop.saas.subscription.repository.SubscriptionShopRepository;
import com.repairshop.saas.subscription.service.OwnerDirectory.ShopInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read side for shop owners: catalog, /me, eligible shops, payment history, receipts. Never writes. */
@Service
@RequiredArgsConstructor
public class MySubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final SubscriptionShopRepository subscriptionShops;
    private final SubscriptionOrderRepository orders;
    private final SubscriptionPaymentRepository payments;
    private final PlanCatalogService plans;
    private final SettingsService settings;
    private final OwnerDirectory directory;
    private final TrialPolicy trialPolicy;
    private final OrderViews views;
    private final RazorpayProperties razorpay;
    private final Clock clock;

    public CatalogView catalog() {
        SettingsService.Settings s = settings.get();
        return new CatalogView(plans.shopPlans(),
                new CheckoutFlags(s.webCheckoutEnabled(), s.androidCheckoutEnabled(), s.iosCheckoutEnabled(),
                        razorpay.isConfigured(), razorpay.checkoutMode()),
                s.effectiveTaxPercent(), true);
    }

    public Subscription current(UUID ownerUserId) {
        List<Subscription> rows = subscriptions.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public MeView me(UUID ownerUserId) {
        Subscription sub = current(ownerUserId);
        OrderView pending = orders.findByOwnerUserIdAndStatusInOrderByCreatedAtDesc(ownerUserId, CheckoutService.PENDING)
                .stream().findFirst().map(views::of).orElse(null);
        return new MeView(summary(sub), coveredShops(ownerUserId, sub), entitlements(sub), usage(ownerUserId),
                trialPolicy.isAvailable(ownerUserId), pending);
    }

    public SubscriptionSummary summary(Subscription s) {
        if (s == null) {
            return new SubscriptionSummary(null, null, null, null, null, SubscriptionState.NONE, null, null, 0, 0,
                    null, false);
        }
        Instant now = clock.instant();
        PlanCatalogService.PlanInfo info = plans.describe(s);
        String status = SubscriptionState.status(s, now);
        Instant end = SubscriptionState.endsAt(s);
        return new SubscriptionSummary(s.getId(), s.getPlanCode(), info.name(), info.planType(), info.durationLabel(),
                status, SubscriptionState.startedAt(s), end,
                SubscriptionState.isRunning(status) ? SubscriptionState.daysRemaining(end, now) : 0,
                SubscriptionTerms.paiseOf(s.getPriceAmount()), s.getShopCount(), s.getPlanId() == null);
    }

    /** v2 rows list subscription_shops; legacy rows cover the owner's active shops (up to shop_limit). */
    public List<CoveredShop> coveredShops(UUID ownerUserId, Subscription s) {
        if (s == null) return List.of();
        Instant now = clock.instant();
        String status = SubscriptionState.status(s, now);
        Map<UUID, ShopInfo> owned = directory.shopsOf(ownerUserId).stream()
                .collect(Collectors.toMap(ShopInfo::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        if (s.getPlanId() != null) {
            return subscriptionShops.findBySubscriptionId(s.getId()).stream()
                    .map(row -> new CoveredShop(row.getShopId(), nameOf(owned, row.getShopId()), row.getStartsAt(),
                            row.getEndsAt(), shopStatus(row, status, now)))
                    .toList();
        }
        List<CoveredShop> out = new ArrayList<>();
        Integer cap = s.getShopLimit();
        for (ShopInfo shop : owned.values().stream().filter(ShopInfo::active).toList()) {
            if (cap != null && out.size() >= cap) break;
            out.add(new CoveredShop(shop.id(), shop.name(), SubscriptionState.startedAt(s),
                    SubscriptionState.endsAt(s), status));
        }
        return out;
    }

    public EntitlementsView entitlements(Subscription s) {
        if (s == null) {
            return new EntitlementsView(null, "PER_SHOP", null, null, null,
                    false, false, false, false, false, false, false);
        }
        PlanView snap = plans.readSnapshot(s.getPlanSnapshot());
        if (snap != null) {
            return new EntitlementsView(s.getEmployeeLimit(), "PER_SHOP", s.getShopLimit(), s.getSellLimit(),
                    snap.buyOrderLimit(), snap.serviceBookingEnabled(), Boolean.TRUE.equals(s.getPickupServiceEnabled()),
                    snap.smsEnabled(), snap.whatsappEnabled(), snap.reportsEnabled(), snap.exportEnabled(),
                    snap.prioritySupportEnabled());
        }
        SubscriptionPlan legacy = SubscriptionPlan.from(s.getSubscriptionType());
        if (legacy == null) legacy = SubscriptionPlan.from(s.getPlanCode());
        if (legacy != null) {
            Integer count = s.getShopCount();
            return new EntitlementsView(
                    legacy.limitFor(SubscriptionFeature.EMPLOYEES, count), "PER_SHOP",
                    legacy.limitFor(SubscriptionFeature.SHOPS, count),
                    legacy.limitFor(SubscriptionFeature.SELL_ORDERS, count),
                    legacy.limitFor(SubscriptionFeature.BUY_PRODUCTS, count),
                    legacy.grants(SubscriptionCapability.NEW_SERVICE_BOOKING, count),
                    legacy.isPickupServiceEnabled(), false, false, false, false, false);
        }
        return new EntitlementsView(s.getEmployeeLimit(), "PER_SHOP", s.getShopLimit(), s.getSellLimit(), null,
                true, Boolean.TRUE.equals(s.getPickupServiceEnabled()), false, false, false, false, false);
    }

    /** Usage in the same units the limits are enforced in (employees and sell orders are per shop). */
    public UsageView usage(UUID ownerUserId) {
        long shops = directory.shopsOf(ownerUserId).stream().filter(ShopInfo::active).count();
        long employees = max(directory.activeEmployeesByShop(ownerUserId).values());
        long sells = max(directory.sellOrdersByShop(ownerUserId).values());
        return new UsageView(shops, employees, sells);
    }

    public List<EligibleShop> eligibleShops(UUID ownerUserId) {
        Subscription s = current(ownerUserId);
        Instant now = clock.instant();
        String status = SubscriptionState.status(s, now);
        String planName = s == null ? null : plans.describe(s).name();
        Map<UUID, SubscriptionShop> covered = s != null && s.getPlanId() != null
                ? subscriptionShops.findBySubscriptionId(s.getId()).stream()
                        .collect(Collectors.toMap(SubscriptionShop::getShopId, Function.identity(), (a, b) -> a))
                : null;

        List<EligibleShop> out = new ArrayList<>();
        for (ShopInfo shop : directory.shopsOf(ownerUserId)) {
            Coverage coverage;
            if (covered != null) {
                SubscriptionShop row = covered.get(shop.id());
                coverage = row == null ? new Coverage(SubscriptionState.NONE, null, null)
                        : new Coverage(coverageStatus(shopStatus(row, status, now)), planName, row.getEndsAt());
            } else if (s != null && !SubscriptionState.NONE.equals(status)) {
                coverage = new Coverage(coverageStatus(status), planName, SubscriptionState.endsAt(s));
            } else {
                coverage = new Coverage(SubscriptionState.NONE, null, null);
            }
            out.add(new EligibleShop(shop.id(), shop.name(), shop.slug(), shop.location(), coverage,
                    shop.active(), shop.active() ? null : "This shop is inactive"));
        }
        return out;
    }

    public List<PaymentHistoryItem> history(UUID ownerUserId) {
        List<SubscriptionPayment> list = payments.findByOwnerUserIdOrderByPaidAtDesc(ownerUserId);
        Map<UUID, SubscriptionOrder> byId = ordersById(list.stream().map(SubscriptionPayment::getOrderId).toList());
        return list.stream().map(p -> {
            SubscriptionOrder o = byId.get(p.getOrderId());
            return new PaymentHistoryItem(p.getId(), p.getOrderId(), o == null ? null : o.getOrderNo(), p.getPaidAt(),
                    o == null ? null : o.getPlanName(), o == null ? null : o.getDurationLabel(),
                    o == null || o.getShopCount() == null ? 0 : o.getShopCount(), p.getAmountPaise(), p.getMethod(),
                    p.getStatus(), p.getReceiptNo(), null, null, null, null);   // invoice fields: InvoiceService.withInvoices
        }).toList();
    }

    /** Receipt for the owner's own payment (or any payment when {@code admin}). */
    public ReceiptView receipt(UUID paymentId, UUID callerUserId, boolean admin) {
        SubscriptionPayment p = payments.findById(paymentId)
                .filter(x -> admin || x.getOwnerUserId().equals(callerUserId))
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "Payment not found"));
        SubscriptionOrder o = orders.findById(p.getOrderId())
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
        QuoteView q = views.quote(o.getQuoteSnapshot());
        SettingsService.Settings s = settings.get();
        OwnerDirectory.OwnerInfo owner = directory.owner(o.getOwnerUserId()).orElse(null);
        String businessName = directory.businessNames(List.of(o.getOwnerUserId())).get(o.getOwnerUserId());

        BigDecimal taxPercent = q == null ? BigDecimal.ZERO : q.taxPercent();
        boolean taxInvoice = s.receiptGstin() != null && taxPercent != null && taxPercent.signum() > 0;
        return new ReceiptView(
                p.getReceiptNo(), p.getSubscriptionId(), o.getOrderNo(),
                new IssuedBy(s.receiptBusinessName(), s.receiptAddress(), s.receiptGstin(), s.receiptEmail(),
                        s.receiptPhone()),
                new ReceiptOwner(owner == null ? null : owner.name(), businessName,
                        owner == null ? null : owner.phone(), owner == null ? null : owner.email()),
                o.getPlanName(), o.getDurationLabel(), o.getTermStartsAt(), o.getTermEndsAt(),
                views.shops(o.getShopsSnapshot()).stream().map(ShopRef::name).toList(),
                q == null ? List.of() : q.lines(), q == null ? 0 : q.discountPaise(), taxPercent,
                q == null ? 0 : q.taxPaise(), p.getAmountPaise(), p.getCurrency(), p.getRazorpayPaymentId(),
                p.getMethod(), p.getPaidAt(), p.getStatus(),
                (taxInvoice ? "NearGFix Subscription Tax Invoice" : "NearGFix Subscription Receipt")
                        + (RazorpayProperties.TEST.equals(p.getPaymentMode()) ? " (TEST MODE)" : ""),
                taxInvoice, p.getPaymentMode());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Map<UUID, SubscriptionOrder> ordersById(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return orders.findAllById(ids).stream()
                .collect(Collectors.toMap(SubscriptionOrder::getId, Function.identity(), (a, b) -> a));
    }

    private static String shopStatus(SubscriptionShop row, String subscriptionStatus, Instant now) {
        if (SubscriptionState.CANCELLED.equals(subscriptionStatus)) return SubscriptionState.CANCELLED;
        if (SubscriptionState.EXPIRED.equals(subscriptionStatus)
                || (row.getEndsAt() != null && !row.getEndsAt().isAfter(now))) {
            return SubscriptionState.EXPIRED;
        }
        return row.getStatus();
    }

    /** Coverage enum is ACTIVE | TRIAL | EXPIRED | NONE — a cancelled term reads as EXPIRED. */
    private static String coverageStatus(String status) {
        if (SubscriptionState.CANCELLED.equals(status)) return SubscriptionState.EXPIRED;
        return status;
    }

    private static String nameOf(Map<UUID, ShopInfo> owned, UUID shopId) {
        ShopInfo s = owned.get(shopId);
        return s == null ? null : s.name();
    }

    private static long max(Collection<Long> values) {
        return values.stream().mapToLong(Long::longValue).max().orElse(0L);
    }
}
