package com.repairshop.saas.subscription.service;

import com.repairshop.saas.subscription.dto.v2.V2Requests.PurchaseRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.VerifyRequest;
import com.repairshop.saas.subscription.dto.v2.V2Views.ReceiptView;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.entity.SubscriptionPayment;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.razorpay.RazorpayPayment;
import com.repairshop.saas.subscription.razorpay.RazorpayProperties;
import com.repairshop.saas.subscription.razorpay.RazorpaySignature;
import com.repairshop.saas.subscription.service.OwnerDirectory.ShopInfo;
import com.repairshop.saas.subscription.service.QuoteService.QuoteContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
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

/** RAZORPAY_MODE (TEST | LIVE): key/mode matching, stamping, and no cross-mode activation. */
class ModeIsolationTest {

    // ── 1. key prefix must match RAZORPAY_MODE ──────────────────────────────

    @Test
    void keyPrefixMustMatchMode() {
        RazorpayProperties testOk = new RazorpayProperties("rzp_test_abc1", "s", "w", "TEST");
        assertTrue(testOk.isConfigured());
        assertEquals("TEST", testOk.checkoutMode());
        assertNull(testOk.unavailableReason());

        RazorpayProperties liveOk = new RazorpayProperties("rzp_live_abc1", "s", "w", "live");
        assertTrue(liveOk.isConfigured());
        assertEquals("LIVE", liveOk.serverMode());

        RazorpayProperties testKeyLiveMode = new RazorpayProperties("rzp_test_abc1", "s", "w", "LIVE");
        assertFalse(testKeyLiveMode.isConfigured());
        assertTrue(testKeyLiveMode.isModeMismatch());
        assertEquals(RazorpayProperties.MODE_MISMATCH_MESSAGE, testKeyLiveMode.unavailableReason());
        assertEquals("UNCONFIGURED", testKeyLiveMode.checkoutMode());
        assertEquals("LIVE", testKeyLiveMode.mode());
        assertFalse(testKeyLiveMode.toString().contains("rzp_test_abc1"));

        RazorpayProperties liveKeyDefaultMode = new RazorpayProperties("rzp_live_abc1", "s", "w", "");
        assertTrue(liveKeyDefaultMode.isModeMismatch());   // default mode is TEST

        RazorpayProperties none = new RazorpayProperties("", "", "", "TEST");
        assertFalse(none.isModeMismatch());
        assertEquals("UNCONFIGURED", none.mode());
        assertEquals(RazorpayProperties.NOT_CONFIGURED_MESSAGE, none.unavailableReason());
    }

    @Test
    void mismatchedServerRefusesPaymentsWithClearMessage() {
        TestStore t = new TestStore("rzp_test_key123", "LIVE");
        t.basic6m();
        UUID owner = UUID.randomUUID();
        List<UUID> ids = t.ownShops(owner, 1).stream().map(ShopInfo::id).toList();

        ApiException e = assertThrows(ApiException.class, () -> t.checkout.purchase(owner,
                new PurchaseRequest("BASIC_6M", ids, "WEB", true)));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("PAYMENTS_NOT_CONFIGURED", e.getCode());
        assertEquals("Razorpay key does not match RAZORPAY_MODE", e.getMessage());

        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        ApiException hook = assertThrows(ApiException.class,
                () -> t.webhooks.handle(body, RazorpaySignature.hmacSha256Hex(body, "whsec_test"), "evt_m"));
        assertEquals("Razorpay key does not match RAZORPAY_MODE", hook.getMessage());

        assertEquals("UNCONFIGURED", t.mine.catalog().checkout().mode());
        assertFalse(t.mine.catalog().checkout().configured());
        assertTrue(t.orders.isEmpty());
    }

    // ── 2. every order / payment is stamped; receipt + order JSON show it ───

    @Test
    void ordersAndPaymentsAreStampedAndReceiptSaysTestMode() {
        TestStore t = new TestStore();
        t.basic6m();
        UUID owner = UUID.randomUUID();
        SubscriptionOrder o = awaitingOrder(t, owner, "order_M1");
        assertEquals("TEST", o.getPaymentMode());
        assertEquals("TEST", t.mine.catalog().checkout().mode());

        when(t.razorpay.fetchPayment("pay_M1")).thenReturn(captured("pay_M1", "order_M1"));
        t.checkout.verify(owner, verifyReq(o, "pay_M1"));

        SubscriptionPayment p = t.paymentsFor(o.getId()).get(0);
        assertEquals("TEST", p.getPaymentMode());
        assertEquals("TEST", t.views.of(t.orders.get(o.getId())).paymentMode());
        ReceiptView r = t.mine.receipt(p.getId(), owner, false);
        assertEquals("TEST", r.paymentMode());
        assertEquals("NearGFix Subscription Receipt (TEST MODE)", r.documentTitle());
    }

    // ── 3. an order from the other mode never activates ─────────────────────

    @Test
    void webhookForOtherModeOrderIsRecordedForReviewNotActivated() {
        TestStore t = new TestStore();
        t.basic6m();
        UUID owner = UUID.randomUUID();
        SubscriptionOrder o = awaitingOrder(t, owner, "order_M2");
        o.setPaymentMode("LIVE");   // created while the server ran LIVE; server is TEST now

        byte[] body = ("{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_M2\","
                + "\"amount\":100,\"currency\":\"INR\",\"status\":\"captured\",\"order_id\":\"order_M2\","
                + "\"method\":\"upi\"}}}}").getBytes(StandardCharsets.UTF_8);
        Map<String, Object> res = t.webhooks.handle(body, RazorpaySignature.hmacSha256Hex(body, "whsec_test"), "evt_m2");

        assertEquals("NEEDS_REVIEW", res.get("status"));
        assertTrue(t.subs.isEmpty());
        SubscriptionPayment p = t.paymentsFor(o.getId()).get(0);
        assertEquals(SubscriptionPayment.ACTIVATION_NEEDS_REVIEW, p.getActivationStatus());
        assertEquals("LIVE", p.getPaymentMode());
        assertEquals(SubscriptionOrder.PAID, t.orders.get(o.getId()).getStatus());
        verify(t.audit, times(1)).log(any(), any(), eq("PAYMENT_MODE_MISMATCH"), any(), any(), any());
        verify(t.audit, never()).log(any(), any(), eq("SUBSCRIPTION_ACTIVATED"), any(), any(), any());
    }

    @Test
    void verifyAndStatusPollRefuseOtherModeOrders() {
        TestStore t = new TestStore();
        t.basic6m();
        UUID owner = UUID.randomUUID();
        SubscriptionOrder o = awaitingOrder(t, owner, "order_M3");
        o.setPaymentMode("LIVE");

        ApiException e = assertThrows(ApiException.class, () -> t.checkout.verify(owner, verifyReq(o, "pay_M3")));
        assertEquals("PAYMENT_MODE_MISMATCH", e.getCode());
        verify(t.audit, times(1)).log(any(), any(), eq("PAYMENT_MODE_MISMATCH"), any(), any(), any());

        t.checkout.status(owner, o.getId());
        verify(t.razorpay, never()).fetchPayment(anyString());
        verify(t.razorpay, never()).fetchOrderPayments(anyString());
        assertTrue(t.subs.isEmpty());
        assertTrue(t.payments.isEmpty());
        assertEquals(SubscriptionOrder.AWAITING_PAYMENT, t.orders.get(o.getId()).getStatus());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static SubscriptionOrder awaitingOrder(TestStore t, UUID owner, String rzpOrderId) {
        List<UUID> ids = t.ownShops(owner, 1).stream().map(ShopInfo::id).toList();
        QuoteContext ctx = t.quotes.build(owner, "BASIC_6M", ids, null);
        SubscriptionOrder o = t.lifecycle.createOrder(owner, ctx, "WEB", OrderLifecycleService.SOURCE_RAZORPAY,
                SubscriptionOrder.CREATED, owner, null, NOW.plusSeconds(1800));
        return t.lifecycle.attachRazorpayOrder(o.getId(), rzpOrderId);
    }

    private static RazorpayPayment captured(String paymentId, String orderId) {
        return new RazorpayPayment(paymentId, orderId, 100, "INR", "captured", "upi", null, null, null, null, NOW);
    }

    private static VerifyRequest verifyReq(SubscriptionOrder o, String paymentId) {
        String sig = RazorpaySignature.hmacSha256Hex(
                (o.getRazorpayOrderId() + "|" + paymentId).getBytes(StandardCharsets.UTF_8), "test_key_secret");
        return new VerifyRequest(o.getId(), o.getRazorpayOrderId(), paymentId, sig);
    }
}
