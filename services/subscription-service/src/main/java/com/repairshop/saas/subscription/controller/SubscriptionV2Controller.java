package com.repairshop.saas.subscription.controller;

import com.repairshop.saas.subscription.dto.v2.V2Requests.CancelRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.PaymentOrderRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.PreviewRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.PurchaseRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.QuoteRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.TrialRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.VerifyRequest;
import com.repairshop.saas.subscription.dto.v2.V2Views.CatalogView;
import com.repairshop.saas.subscription.dto.v2.V2Views.EligibleShop;
import com.repairshop.saas.subscription.dto.v2.V2Views.MeView;
import com.repairshop.saas.subscription.dto.v2.V2Views.OrderResult;
import com.repairshop.saas.subscription.dto.v2.V2Views.OrderView;
import com.repairshop.saas.subscription.dto.v2.V2Views.PaymentHistoryItem;
import com.repairshop.saas.subscription.dto.v2.V2Views.PaymentOrderView;
import com.repairshop.saas.subscription.dto.v2.V2Views.QuoteView;
import com.repairshop.saas.subscription.dto.v2.V2Views.ReceiptView;
import com.repairshop.saas.subscription.dto.v2.V2Views.VerifyResult;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.security.CurrentUser;
import com.repairshop.saas.subscription.service.CheckoutService;
import com.repairshop.saas.subscription.service.MySubscriptionService;
import com.repairshop.saas.subscription.service.QuoteService;
import com.repairshop.saas.subscription.service.TrialService;
import com.repairshop.saas.subscription.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Subscription v2 shop/owner routes (contract "Shop / owner routes"). The
 * owner is ALWAYS the JWT sub; no route takes an ownerUserId from the client.
 * Catalog and quote/preview are readable without a token; the Razorpay webhook
 * is authenticated by its signature instead of a JWT.
 */
@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionV2Controller {

    private final CurrentUser currentUser;
    private final MySubscriptionService mine;
    private final QuoteService quotes;
    private final CheckoutService checkout;
    private final TrialService trials;
    private final WebhookService webhooks;

    @GetMapping("/catalog")
    public CatalogView catalog() {
        return mine.catalog();
    }

    @GetMapping("/me")
    public MeView me() {
        return mine.me(currentUser.requireOwnerId());
    }

    @GetMapping("/eligible-shops")
    public List<EligibleShop> eligibleShops() {
        return mine.eligibleShops(currentUser.requireOwnerId());
    }

    @PostMapping("/quote")
    public QuoteView quote(@RequestBody QuoteRequest req) {
        return quotes.quote(currentUser.requireOwnerId(), body(req).planCode(), req.shopIds(), null);
    }

    @PostMapping("/quote/preview")
    public QuoteView preview(@RequestBody PreviewRequest req) {
        UUID owner = currentUser.current().map(CurrentUser.Actor::userId).orElse(null);
        return quotes.preview(owner, body(req).planCode(), req.shopCount());
    }

    @PostMapping("/renew/quote")
    public QuoteView renewQuote(@RequestBody QuoteRequest req) {
        return quotes.quote(currentUser.requireOwnerId(), body(req).planCode(), req.shopIds(), QuoteService.RENEW);
    }

    @PostMapping("/upgrade/quote")
    public QuoteView upgradeQuote(@RequestBody QuoteRequest req) {
        return quotes.quote(currentUser.requireOwnerId(), body(req).planCode(), req.shopIds(), QuoteService.UPGRADE);
    }

    @PostMapping("/purchase")
    public OrderView purchase(@RequestBody PurchaseRequest req) {
        return checkout.purchase(currentUser.requireOwnerId(), req);
    }

    @PostMapping("/payment/order")
    public PaymentOrderView paymentOrder(@RequestBody PaymentOrderRequest req) {
        return checkout.paymentOrder(currentUser.requireOwnerId(), body(req).orderId());
    }

    @PostMapping("/payment/verify")
    public VerifyResult verify(@RequestBody VerifyRequest req) {
        return checkout.verify(currentUser.requireOwnerId(), req);
    }

    @GetMapping("/payment/status/{orderId}")
    public OrderResult status(@PathVariable UUID orderId) {
        return checkout.status(currentUser.requireOwnerId(), orderId);
    }

    @PostMapping("/payment/cancel")
    public OrderView cancel(@RequestBody CancelRequest req) {
        return checkout.cancel(currentUser.requireOwnerId(), req);
    }

    @GetMapping("/payment/history")
    public List<PaymentHistoryItem> history() {
        return mine.history(currentUser.requireOwnerId());
    }

    /** The owner's own receipt; platform admins may open any receipt. */
    @GetMapping("/receipts/{paymentId}")
    public ReceiptView receipt(@PathVariable UUID paymentId) {
        CurrentUser.Actor actor = currentUser.requireUser();
        return mine.receipt(paymentId, actor.userId(), actor.isAdmin());
    }

    @PostMapping("/trial/activate")
    public MeView activateTrial(@RequestBody(required = false) TrialRequest req) {
        return trials.activate(currentUser.requireUser(), req);
    }

    /** Razorpay → us. No JWT: the X-Razorpay-Signature over the raw body is the authentication. */
    @PostMapping("/payment/webhook/razorpay")
    public Map<String, Object> razorpayWebhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        return webhooks.handle(rawBody, signature, eventId);
    }

    private static <T> T body(T req) {
        if (req == null) throw ApiException.badRequest("INVALID_REQUEST", "Request body is required");
        return req;
    }
}
