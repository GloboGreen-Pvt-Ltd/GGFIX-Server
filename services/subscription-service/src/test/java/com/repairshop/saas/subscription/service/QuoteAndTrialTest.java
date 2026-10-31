package com.repairshop.saas.subscription.service;

import com.repairshop.saas.subscription.dto.v2.V2Requests.TrialRequest;
import com.repairshop.saas.subscription.dto.v2.V2Views.MeView;
import com.repairshop.saas.subscription.dto.v2.V2Views.QuoteView;
import com.repairshop.saas.subscription.entity.Subscription;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.security.CurrentUser.Actor;
import com.repairshop.saas.subscription.service.OwnerDirectory.ShopInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.repairshop.saas.subscription.service.TestStore.NOW;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Ownership, purpose rules, the once-only trial, and legacy rows staying untouched. */
class QuoteAndTrialTest {

    private TestStore t;
    private UUID owner;

    @BeforeEach
    void setUp() {
        t = new TestStore();
        t.basic6m();
        t.freeTrial();
        owner = UUID.randomUUID();
    }

    // ── ownership ───────────────────────────────────────────────────────────

    @Test
    void shopsOfAnotherOwnerAreRejected() {
        List<ShopInfo> mine = t.ownShops(owner, 1);
        List<ShopInfo> theirs = t.ownShops(UUID.randomUUID(), 1);

        ApiException e = assertThrows(ApiException.class, () -> t.quotes.quote(owner, "BASIC_6M",
                List.of(mine.get(0).id(), theirs.get(0).id()), null));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("SHOP_NOT_OWNED", e.getCode());

        ApiException random = assertThrows(ApiException.class,
                () -> t.quotes.quote(owner, "BASIC_6M", List.of(UUID.randomUUID()), null));
        assertEquals("SHOP_NOT_OWNED", random.getCode());
    }

    @Test
    void duplicateAndEmptySelectionsAreRejected() {
        UUID shop = t.ownShops(owner, 1).get(0).id();
        assertEquals("DUPLICATE_SHOPS", assertThrows(ApiException.class,
                () -> t.quotes.quote(owner, "BASIC_6M", List.of(shop, shop), null)).getCode());
        assertEquals("SHOPS_REQUIRED", assertThrows(ApiException.class,
                () -> t.quotes.quote(owner, "BASIC_6M", List.of(), null)).getCode());
    }

    @Test
    void inactivePlanIsNotPurchasable() {
        UUID shop = t.ownShops(owner, 1).get(0).id();
        t.plansByCode.get("BASIC_6M").setStatus("INACTIVE");
        assertEquals("PLAN_NOT_PURCHASABLE", assertThrows(ApiException.class,
                () -> t.quotes.quote(owner, "BASIC_6M", List.of(shop), null)).getCode());
    }

    // ── purpose ─────────────────────────────────────────────────────────────

    @Test
    void newPurchaseStartsNowWithContractPrice() {
        List<UUID> ids = t.ownShops(owner, 3).stream().map(ShopInfo::id).toList();
        QuoteView q = t.quotes.quote(owner, "BASIC_6M", ids, null);

        assertEquals("NEW", q.purpose());
        assertEquals(290, q.totalPaise());
        assertEquals(95, q.additionalShopUnitPaise());
        assertEquals(10, q.discountPaise());
        assertEquals(300, q.subtotalPaise());
        assertEquals(NOW, q.startsAt());
        assertEquals(Instant.parse("2027-04-30T06:30:00Z"), q.endsAt());
        assertFalse(q.replacesCurrent());
        assertTrue(q.taxInclusive());
        assertEquals(2, q.lines().size());
    }

    @Test
    void activeLegacyBasicIsReplacedWithNoticeAndLeftUntouched() {
        Subscription legacy = legacyBasic();
        List<UUID> ids = t.ownShops(owner, 1).stream().map(ShopInfo::id).toList();

        QuoteView q = t.quotes.quote(owner, "BASIC_6M", ids, null);

        assertEquals("UPGRADE", q.purpose());
        assertTrue(q.replacesCurrent());
        assertNotNull(q.notice());
        assertEquals(NOW, q.startsAt());
        assertEquals("RENEW_NOT_AVAILABLE", assertThrows(ApiException.class,
                () -> t.quotes.quote(owner, "BASIC_6M", ids, QuoteService.RENEW)).getCode());
        verify(t.subRepo, never()).save(any());
        assertLegacyUnchanged(legacy);
    }

    @Test
    void meOnLegacyBasicRowReadsItWithoutWriting() {
        Subscription legacy = legacyBasic();
        t.ownShops(owner, 2);

        MeView me = t.mine.me(owner);

        assertTrue(me.subscription().legacy());
        assertEquals("ACTIVE", me.subscription().status());
        assertEquals("Basic", me.subscription().planName());
        assertEquals("BASIC", me.subscription().planType());
        assertEquals(300000L, me.subscription().amountPaidPaise());
        assertNull(me.entitlements().employeeLimit());     // legacy Basic: unlimited employees
        assertEquals(2, me.entitlements().shopLimit());    // = shops purchased
        assertEquals(2, me.shops().size());
        assertFalse(me.trialAvailable());
        verify(t.subRepo, never()).save(any());
        assertLegacyUnchanged(legacy);
    }

    // ── trial ───────────────────────────────────────────────────────────────

    @Test
    void trialActivatesOnceOnly() {
        List<UUID> ids = t.ownShops(owner, 2).stream().map(ShopInfo::id).toList();
        Actor actor = new Actor(owner, List.of("SHOP_OWNER"));

        assertTrue(t.trialPolicy.isAvailable(owner));
        t.trials.activate(actor, new TrialRequest(ids, null));

        Subscription sub = t.subs.values().iterator().next();
        assertEquals("FREE_TRIAL", sub.getPlanCode());
        assertEquals("FREE_TRIAL", sub.getStatus());
        assertEquals(NOW, sub.getTrialStartDate());
        assertEquals(NOW.plus(15, ChronoUnit.DAYS), sub.getTrialEndDate());
        assertEquals(sub.getTrialEndDate(), sub.getInactiveDate());
        assertEquals(2, sub.getShopLimit());
        assertEquals(3, sub.getEmployeeLimit());
        assertEquals(BigDecimal.ZERO.setScale(2), sub.getPriceAmount());
        assertEquals(2, t.subShops.size());
        assertTrue(t.orders.values().stream().anyMatch(o -> "TRIAL".equals(o.getPurpose())
                && SubscriptionOrder.ACTIVATED.equals(o.getStatus()) && o.getTotalPaise() == 0));

        assertFalse(t.trialPolicy.isAvailable(owner));
        ApiException again = assertThrows(ApiException.class, () -> t.trials.activate(actor, new TrialRequest(ids, null)));
        assertEquals(HttpStatus.CONFLICT, again.getStatus());
        assertEquals("TRIAL_ALREADY_USED", again.getCode());
        assertEquals(1, t.subs.size());
    }

    @Test
    void registrationTrialRowCountsAsUsed() {
        t.putSubscription(Subscription.builder().ownerUserId(owner).planCode("FREE_TRIAL")
                .subscriptionType("FREE_TRIAL").status("FREE_TRIAL").trialStartDate(NOW.minus(20, ChronoUnit.DAYS))
                .trialEndDate(NOW.minus(5, ChronoUnit.DAYS)).inactiveDate(NOW.minus(5, ChronoUnit.DAYS)).build());
        List<UUID> ids = t.ownShops(owner, 1).stream().map(ShopInfo::id).toList();

        ApiException e = assertThrows(ApiException.class,
                () -> t.trials.activate(new Actor(owner, List.of("SHOP_OWNER")), new TrialRequest(ids, null)));
        assertEquals("TRIAL_ALREADY_USED", e.getCode());
    }

    @Test
    void trialIsCappedAtTrialMaxShops() {
        List<UUID> ids = t.ownShops(owner, 3).stream().map(ShopInfo::id).toList();
        ApiException e = assertThrows(ApiException.class,
                () -> t.trials.activate(new Actor(owner, List.of("SHOP_OWNER")), new TrialRequest(ids, null)));
        assertEquals("SHOP_LIMIT_EXCEEDED", e.getCode());
        assertTrue(t.subs.isEmpty());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Subscription legacyBasic() {
        Instant end = NOW.plus(100, ChronoUnit.DAYS);
        return t.putSubscription(Subscription.builder()
                .ownerUserId(owner).planCode("BASIC").subscriptionType("BASIC").status("ACTIVE")
                .subscriptionStartDate(NOW.minus(265, ChronoUnit.DAYS)).subscriptionEndDate(end)
                .activeDate(NOW.minus(265, ChronoUnit.DAYS)).inactiveDate(end)
                .shopCount(2).shopLimit(2).priceAmount(new BigDecimal("3000.00"))
                .pickupServiceEnabled(true).buyProductUnlimited(true).sellProductUnlimited(true)
                .build());
    }

    private static void assertLegacyUnchanged(Subscription s) {
        assertEquals("BASIC", s.getPlanCode());
        assertEquals("ACTIVE", s.getStatus());
        assertEquals(new BigDecimal("3000.00"), s.getPriceAmount());
        assertEquals(2, s.getShopCount());
        assertNull(s.getPlanId());
        assertNull(s.getPlanSnapshot());
    }
}
