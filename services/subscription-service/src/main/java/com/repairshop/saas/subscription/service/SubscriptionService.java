package com.repairshop.saas.subscription.service;

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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final BigDecimal PRICE_SINGLE = new BigDecimal("3000");
    private static final BigDecimal PRICE_MULTI = new BigDecimal("2500");

    private final SubscriptionRepository repository;

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
        int count = (shopCount != null && shopCount > 0) ? shopCount : 1;
        Instant now = Instant.now();
        Instant end = now.plus(365, ChronoUnit.DAYS);
        BigDecimal total = quote(count).getTotal();

        Subscription sub = repository.findByOwnerUserId(ownerUserId)
                .orElseGet(() -> Subscription.builder()
                        .ownerUserId(ownerUserId)
                        .build());

        sub.setSubscriptionType("BASIC");
        sub.setStatus("ACTIVE");
        sub.setPlanCode("BASIC");
        sub.setSubscriptionStartDate(now);
        sub.setSubscriptionEndDate(end);
        sub.setActiveDate(now);
        sub.setInactiveDate(end);
        sub.setShopLimit(null);          // unlimited
        sub.setEmployeeLimit(null);      // unlimited
        sub.setSellLimit(null);          // unlimited
        sub.setPickupServiceEnabled(true);
        sub.setBuyProductUnlimited(true);
        sub.setSellProductUnlimited(true);
        sub.setShopCount(count);
        sub.setPriceAmount(total);
        sub.setStartedAt(sub.getStartedAt() != null ? sub.getStartedAt() : now);
        sub.setCurrentPeriodEnd(end);

        return SubscriptionResponse.from(repository.save(sub));
    }

    /** Static plan catalog. */
    public List<PlanCatalog.Plan> plans() {
        return PlanCatalog.all();
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
