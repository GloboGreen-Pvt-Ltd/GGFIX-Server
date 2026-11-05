package com.repairshop.saas.subscription.service;

import com.repairshop.saas.subscription.billing.SubscriptionTerms;
import com.repairshop.saas.subscription.dto.v2.V2Requests.VerifyRequest;
import com.repairshop.saas.subscription.entity.Subscription;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.razorpay.RazorpayPayment;
import com.repairshop.saas.subscription.razorpay.RazorpaySignature;
import com.repairshop.saas.subscription.service.OrderLifecycleService.CapturedPayment;
import com.repairshop.saas.subscription.service.OrderLifecycleService.Outcome;
import com.repairshop.saas.subscription.service.OwnerDirectory.ShopInfo;
import com.repairshop.saas.subscription.service.QuoteService.QuoteContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.repairshop.saas.subscription.service.TestStore.NOW;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verify, status polling and the webhook all funnel into one idempotent
 * activation: one activation, one payment row, a renewal extended once.
 */
class ActivationIdempotencyTest {

    private TestStore t;
    private UUID owner;
    private List<ShopInfo> shops;

    @BeforeEach
    void setUp() {
        t = new TestStore();
        t.basic6m();
        owner = UUID.randomUUID();
        shops = t.ownShops(owner, 2);
    }

    @Test
    void verifyTwiceActivatesOnceWithOnePaymentRow() {
        SubscriptionOrder o = awaitingOrder("order_T1");
        assertEquals(195L, o.getTotalPaise());
        when(t.razorpay.fetchPayment("pay_T1")).thenReturn(captured("pay_T1", "order_T1", 195));

        t.checkout.verify(owner, verifyReq(o, "pay_T1"));
        t.checkout.verify(owner, verifyReq(o, "pay_T1"));

        assertEquals(1, t.paymentsFor(o.getId()).size());
        assertEquals(SubscriptionOrder.ACTIVATED, t.orders.get(o.getId()).getStatus());
        verify(t.razorpay, times(1)).fetchPayment("pay_T1");
        verify(t.audit, times(1)).log(any(), any(), eq("SUBSCRIPTION_ACTIVATED"), any(), any(), any());
        // Exactly one invoice trigger (handled after commit by InvoiceIssuer), carrying the payment row id.
        List<Object> activated = t.publishedEvents.stream()
                .filter(e -> e instanceof com.repairshop.saas.subscription.invoice.PaymentActivatedEvent).toList();
        assertEquals(1, activated.size());
        assertEquals(t.paymentsFor(o.getId()).get(0).getId(),
                ((com.repairshop.saas.subscription.invoice.PaymentActivatedEvent) activated.get(0)).paymentId());

        Subscription sub = onlySubscription();
        assertEquals("BASIC_6M", sub.getPlanCode());
        assertEquals("BASIC_6M", sub.getSubscriptionType());
        assertEquals("ACTIVE", sub.getStatus());
        // 31 Oct 2026 12:00 IST + 6 calendar months = 30 Apr 2027 12:00 IST.
        assertEquals(Instant.parse("2027-04-30T06:30:00Z"), sub.getInactiveDate());
        assertEquals(sub.getInactiveDate(), sub.getSubscriptionEndDate());
        assertEquals(2, sub.getShopCount());
        assertEquals(2, sub.getShopLimit());
        assertEquals(4, sub.getEmployeeLimit());
        assertNull(sub.getSellLimit());
        assertEquals(new BigDecimal("1.95"), sub.getPriceAmount());
        assertEquals(2, t.subShops.size());
        assertNotNull(sub.getPlanSnapshot());
    }

    @Test
    void webhookAfterVerifyDoesNotActivateAgain() {
        SubscriptionOrder o = awaitingOrder("order_T1");
        when(t.razorpay.fetchPayment("pay_T1")).thenReturn(captured("pay_T1", "order_T1", 195));
        t.checkout.verify(owner, verifyReq(o, "pay_T1"));
        Instant endAfterVerify = onlySubscription().getInactiveDate();

        byte[] body = webhookBody("payment.captured", "pay_T1", "order_T1", 195);
        String sig = RazorpaySignature.hmacSha256Hex(body, "whsec_test");
        Map<String, Object> first = t.webhooks.handle(body, sig, "evt_1");
        Map<String, Object> replay = t.webhooks.handle(body, sig, "evt_1");

        assertEquals(Outcome.ALREADY_PROCESSED.name(), first.get("status"));
        assertEquals(1, t.publishedEvents.size(), "a replayed webhook never triggers a second invoice");
        assertEquals("duplicate", replay.get("status"));
        assertEquals(1, t.paymentsFor(o.getId()).size());
        assertEquals(endAfterVerify, onlySubscription().getInactiveDate());
        verify(t.audit, times(1)).log(any(), any(), eq("SUBSCRIPTION_ACTIVATED"), any(), any(), any());
    }

    @Test
    void webhookFirstThenVerifyIsIdempotentToo() {
        SubscriptionOrder o = awaitingOrder("order_T2");
        byte[] body = webhookBody("order.paid", "pay_T2", "order_T2", 195);

        Map<String, Object> res = t.webhooks.handle(body, RazorpaySignature.hmacSha256Hex(body, "whsec_test"), "evt_2");
        assertEquals(Outcome.ACTIVATED.name(), res.get("status"));

        t.checkout.verify(owner, verifyReq(o, "pay_T2"));
        verify(t.razorpay, never()).fetchPayment(anyString());
        assertEquals(1, t.paymentsFor(o.getId()).size());
        assertEquals(SubscriptionOrder.ACTIVATED, t.orders.get(o.getId()).getStatus());
    }

    @Test
    void statusPollActivatesThroughTheSamePath() {
        SubscriptionOrder o = awaitingOrder("order_T5");
        when(t.razorpay.fetchOrderPayments("order_T5")).thenReturn(List.of(captured("pay_T5", "order_T5", 195)));

        t.checkout.status(owner, o.getId());
        t.checkout.status(owner, o.getId());

        assertEquals(1, t.paymentsFor(o.getId()).size());
        assertEquals(SubscriptionOrder.ACTIVATED, t.orders.get(o.getId()).getStatus());
        verify(t.audit, times(1)).log(any(), any(), eq("SUBSCRIPTION_ACTIVATED"), any(), any(), any());
    }

    @Test
    void renewalExtendsFromCurrentEndExactlyOnce() {
        Instant currentEnd = NOW.plus(30, ChronoUnit.DAYS);
        t.putSubscription(Subscription.builder()
                .ownerUserId(owner).planCode("BASIC_6M").subscriptionType("BASIC_6M").status("ACTIVE")
                .planId(t.plansByCode.get("BASIC_6M").getId())
                .subscriptionStartDate(NOW.minus(150, ChronoUnit.DAYS)).subscriptionEndDate(currentEnd)
                .inactiveDate(currentEnd).shopCount(2).build());

        SubscriptionOrder o = awaitingOrder("order_T3");
        assertEquals(QuoteService.RENEW, o.getPurpose());

        CapturedPayment cp = CapturedPayment.from(captured("pay_T3", "order_T3", 195));
        assertEquals(Outcome.ACTIVATED, t.lifecycle.activatePaid(o.getId(), cp, "VERIFY").outcome());
        assertEquals(Outcome.ALREADY_PROCESSED, t.lifecycle.activatePaid(o.getId(), cp, "WEBHOOK").outcome());

        Instant expected = SubscriptionTerms.termEnd(currentEnd, 6, "MONTHS");
        assertEquals(expected, onlySubscription().getInactiveDate());
        assertEquals(NOW.minus(150, ChronoUnit.DAYS), onlySubscription().getSubscriptionStartDate());
        assertEquals(1, t.paymentsFor(o.getId()).size());
    }

    @Test
    void wrongAmountIsRefusedBeforeAnyWrite() {
        SubscriptionOrder o = awaitingOrder("order_T4");
        CapturedPayment cp = CapturedPayment.from(captured("pay_T4", "order_T4", 100));

        ApiException e = assertThrows(ApiException.class, () -> t.lifecycle.activatePaid(o.getId(), cp, "VERIFY"));
        assertEquals("AMOUNT_MISMATCH", e.getCode());
        assertTrue(t.payments.isEmpty());
        assertTrue(t.subs.isEmpty());
        assertEquals(SubscriptionOrder.AWAITING_PAYMENT, t.orders.get(o.getId()).getStatus());
    }

    @Test
    void badCheckoutSignatureNeverReachesRazorpay() {
        SubscriptionOrder o = awaitingOrder("order_T6");
        VerifyRequest forged = new VerifyRequest(o.getId(), "order_T6", "pay_T6", "00".repeat(32));

        ApiException e = assertThrows(ApiException.class, () -> t.checkout.verify(owner, forged));
        assertEquals("INVALID_SIGNATURE", e.getCode());
        verify(t.razorpay, never()).fetchPayment(anyString());
        assertTrue(t.payments.isEmpty());
    }

    @Test
    void anotherOwnerCannotVerifyMyOrder() {
        SubscriptionOrder o = awaitingOrder("order_T7");
        ApiException e = assertThrows(ApiException.class,
                () -> t.checkout.verify(UUID.randomUUID(), verifyReq(o, "pay_T7")));
        assertEquals("ORDER_NOT_FOUND", e.getCode());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private SubscriptionOrder awaitingOrder(String razorpayOrderId) {
        QuoteContext ctx = t.quotes.build(owner, "BASIC_6M", shops.stream().map(ShopInfo::id).toList(), null);
        SubscriptionOrder o = t.lifecycle.createOrder(owner, ctx, "WEB", OrderLifecycleService.SOURCE_RAZORPAY,
                SubscriptionOrder.CREATED, owner, null, NOW.plusSeconds(1800));
        return t.lifecycle.attachRazorpayOrder(o.getId(), razorpayOrderId);
    }

    private static RazorpayPayment captured(String paymentId, String orderId, long amount) {
        return new RazorpayPayment(paymentId, orderId, amount, "INR", "captured", "upi", "owner@example.com",
                "+919840000010", "owner@okicici", null, NOW);
    }

    private static VerifyRequest verifyReq(SubscriptionOrder o, String paymentId) {
        String sig = RazorpaySignature.hmacSha256Hex(
                (o.getRazorpayOrderId() + "|" + paymentId).getBytes(StandardCharsets.UTF_8), "test_key_secret");
        return new VerifyRequest(o.getId(), o.getRazorpayOrderId(), paymentId, sig);
    }

    private static byte[] webhookBody(String event, String paymentId, String orderId, long amount) {
        return ("{\"entity\":\"event\",\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"id\":\"" + paymentId + "\",\"entity\":\"payment\",\"amount\":" + amount
                + ",\"currency\":\"INR\",\"status\":\"captured\",\"order_id\":\"" + orderId
                + "\",\"method\":\"upi\",\"vpa\":\"owner@okicici\"}}}}").getBytes(StandardCharsets.UTF_8);
    }

    private Subscription onlySubscription() {
        assertEquals(1, t.subs.size());
        return t.subs.values().iterator().next();
    }
}
