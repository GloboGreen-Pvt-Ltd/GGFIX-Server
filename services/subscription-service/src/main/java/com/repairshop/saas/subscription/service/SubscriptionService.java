package com.repairshop.saas.subscription.service;

import com.repairshop.saas.common.subscription.Entitlements;
import com.repairshop.saas.common.subscription.LimitCheck;
import com.repairshop.saas.common.subscription.OwnerSubscription;
import com.repairshop.saas.common.subscription.SubscriptionFeature;
import com.repairshop.saas.common.subscription.SubscriptionLimitService;
import com.repairshop.saas.common.subscription.SubscriptionPlan;
import com.repairshop.saas.subscription.dto.PlanCatalog;
import com.repairshop.saas.subscription.dto.QuoteResponse;
import com.repairshop.saas.subscription.dto.SubscriptionResponse;
import com.repairshop.saas.subscription.entity.Subscription;
import com.repairshop.saas.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final BigDecimal PRICE_SINGLE = SubscriptionPlan.BASIC.getPrice();
    private static final BigDecimal PRICE_MULTI = SubscriptionPlan.BASIC.getMultiShopPrice();

    private final SubscriptionRepository repository;
    private final SubscriptionLimitService limits;

    /**
     * Return the owner's subscription. If found, active (not CANCELLED) and its
     * inactiveDate is already in the past, flip status to EXPIRED (persisted)
     * before mapping. Returns null when the owner has no subscription.
     */
    @Transactional
    public SubscriptionResponse getByOwner(UUID ownerUserId) {
        return repository.findByOwnerUserId(ownerUserId)
                .map(this::deriveExpiry)
                .map(SubscriptionResponse::from)
                .orElse(null);
    }

    /** All subscriptions (newest first) with the same expire-derivation applied. */
    @Transactional
    public List<SubscriptionResponse> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::deriveExpiry)
                .map(SubscriptionResponse::from)
                .toList();
    }

    /** Price quote for BASIC given a shop count. */
    public QuoteResponse quote(int shops) {
        if (shops <= 1) {
            return QuoteResponse.builder()
                    .shopCount(Math.max(shops, 1))
                    .pricePerShop(PRICE_SINGLE)
                    .total(PRICE_SINGLE)
                    .discountApplied(false)
                    .build();
        }
        return QuoteResponse.builder()
                .shopCount(shops)
                .pricePerShop(PRICE_MULTI)
                .total(PRICE_MULTI.multiply(BigDecimal.valueOf(shops)))
                .discountApplied(true)
                .build();
    }

    /**
     * Upsert the owner's subscription into an active BASIC plan (record-only,
     * no payment). Creates a row if the owner has none yet (shopId left null
     * when unknown — never fails on that).
     */
    @Transactional
    public SubscriptionResponse activateBasic(UUID ownerUserId, Integer shopCount) {
        SubscriptionPlan plan = SubscriptionPlan.BASIC;
        int count = (shopCount != null && shopCount > 0) ? shopCount : 1;
        Instant now = Instant.now();
        Instant end = now.plus(plan.getDurationDays(), ChronoUnit.DAYS);
        BigDecimal total = quote(count).getTotal();

        Subscription sub = repository.findByOwnerUserId(ownerUserId)
                .orElseGet(() -> Subscription.builder()
                        .ownerUserId(ownerUserId)
                        .build());

        sub.setSubscriptionType(plan.getCode());
        sub.setStatus("ACTIVE");
        sub.setPlanCode(plan.getCode());
        sub.setSubscriptionStartDate(now);
        sub.setSubscriptionEndDate(end);
        sub.setActiveDate(now);
        sub.setInactiveDate(end);
        // Mirrored from the plan for admin display/billing history. Enforcement
        // reads the plan, not these columns — see SubscriptionPlan's class note.
        // The shop ceiling is the exception that genuinely varies per row:
        // Basic is sold per shop, so the allowance is what was paid for.
        sub.setShopLimit(plan.limitFor(SubscriptionFeature.SHOPS, count));
        sub.setEmployeeLimit(plan.limitFor(SubscriptionFeature.EMPLOYEES));
        sub.setSellLimit(plan.limitFor(SubscriptionFeature.SELL_ORDERS));
        sub.setPickupServiceEnabled(plan.isPickupServiceEnabled());
        sub.setBuyProductUnlimited(true);
        sub.setSellProductUnlimited(true);
        sub.setShopCount(count);
        sub.setPriceAmount(total);
        sub.setStartedAt(sub.getStartedAt() != null ? sub.getStartedAt() : now);
        sub.setCurrentPeriodEnd(end);

        return SubscriptionResponse.from(repository.save(sub));
    }

    /** Static plan catalog, projected from the shared SubscriptionPlan enum. */
    public List<PlanCatalog.Plan> plans() {
        return PlanCatalog.all();
    }

    /**
     * The owner's full entitlement picture — plan, window, every metered
     * allowance with live usage, and the on/off features.
     *
     * <p>This is the single object the Subscription screen and every client-side
     * {@code canAddX()} read. It is assembled from the same engine calls the
     * create APIs enforce with, so a screen cannot advertise headroom the API
     * will refuse.
     *
     * @param shopId the shop whose per-shop usage to report (employees, sell
     *               orders). Omitting it still returns correct ceilings, but
     *               those two counters read zero — "3 of 3 used" has to know
     *               which shop is being asked about.
     */
    public Entitlements entitlements(UUID ownerUserId, UUID shopId) {
        return limits.entitlements(ownerUserId, shopId);
    }

    /**
     * Ceilings only, without touching the usage tables. Kept for callers that
     * just want to know what a plan permits (admin views, plan comparisons).
     */
    public Map<String, Object> limitsForOwner(UUID ownerUserId) {
        OwnerSubscription sub = limits.subscriptionOf(ownerUserId);

        Map<String, Object> features = new LinkedHashMap<>();
        for (SubscriptionFeature feature : SubscriptionFeature.values()) {
            LimitCheck check = limits.evaluate(sub, feature, 0);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("limit", check.limit());
            entry.put("unlimited", check.unlimited());
            entry.put("scope", feature.getScope().name());
            features.put(feature.name(), entry);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan", sub.planCode());
        body.put("planName", sub.planName());
        body.put("status", sub.status());
        body.put("expired", sub.expired());
        body.put("endsAt", sub.endsAt());
        body.put("hasSubscription", sub.present());
        body.put("purchasedShopCount", sub.purchasedShopCount());
        body.put("features", features);
        return body;
    }

    /**
     * If a subscription is past its inactiveDate and not CANCELLED, flip it to
     * EXPIRED and persist the change. Returns the (possibly updated) entity.
     */
    private Subscription deriveExpiry(Subscription s) {
        Instant inactive = s.getInactiveDate();
        boolean cancelled = "CANCELLED".equalsIgnoreCase(s.getStatus());
        boolean alreadyExpired = "EXPIRED".equalsIgnoreCase(s.getStatus());
        if (!cancelled && !alreadyExpired && inactive != null && inactive.isBefore(Instant.now())) {
            s.setStatus("EXPIRED");
            return repository.save(s);
        }
        return s;
    }
}
