package com.repairshop.saas.subscription.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceView;
import com.repairshop.saas.subscription.dto.v2.V2Views.PaymentHistoryItem;
import com.repairshop.saas.subscription.dto.v2.V2Views.ShopRef;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.entity.SubscriptionPayment;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.invoice.InvoiceStore.InvoiceRecord;
import com.repairshop.saas.subscription.repository.SubscriptionOrderRepository;
import com.repairshop.saas.subscription.repository.SubscriptionPaymentRepository;
import com.repairshop.saas.subscription.service.OrderViews;
import com.repairshop.saas.subscription.service.OwnerDirectory;
import com.repairshop.saas.subscription.service.OwnerDirectory.BillingShop;
import com.repairshop.saas.subscription.service.OwnerDirectory.OwnerInfo;
import com.repairshop.saas.subscription.service.SettingsService;
import com.repairshop.saas.subscription.service.SettingsService.Settings;
import com.repairshop.saas.subscription.whatsapp.BotBeeClient;
import com.repairshop.saas.subscription.whatsapp.WhatsAppProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Issuance idempotency, numbering, GST view, PDF and WhatsApp delivery of subscription invoices. */
class InvoiceServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T06:30:00Z");
    private static final String SECRET = "test-secret-for-invoice-links-0123456789";

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryInvoiceStore store = new InMemoryInvoiceStore();
    private final SubscriptionPaymentRepository paymentRepo = mock(SubscriptionPaymentRepository.class);
    private final SubscriptionOrderRepository orderRepo = mock(SubscriptionOrderRepository.class);
    private final SettingsService settings = mock(SettingsService.class);
    private final OwnerDirectory directory = mock(OwnerDirectory.class);
    private final BotBeeClient botbee = mock(BotBeeClient.class);

    private UUID owner;
    private UUID shopId;
    private SubscriptionOrder order;
    private SubscriptionPayment payment;

    @BeforeEach
    void setUp() {
        owner = UUID.randomUUID();
        shopId = UUID.randomUUID();
        when(settings.get()).thenReturn(Settings.defaults());
        when(directory.owner(owner)).thenReturn(Optional.of(
                new OwnerInfo(owner, "Ravi Kumar", "ravi@example.com", "98400 12345", "SHOP_OWNER")));
        when(directory.billingShop(shopId)).thenReturn(Optional.of(new BillingShop(shopId, "Ravi Mobiles", null,
                "9840012345", "12 Anna Salai", "Chennai", "Tamil Nadu", "600002", null)));
        order = order("LIVE");
        payment = payment(order, "pay_1", "ACTIVATED", "LIVE");
    }

    private InvoiceService service(boolean whatsappEnv) {
        WhatsAppProperties props = new WhatsAppProperties(whatsappEnv, "https://nexa.botbee.io",
                whatsappEnv ? "bb_key" : "", "", "en", 15);
        return new InvoiceService(store, paymentRepo, orderRepo, settings, directory, new OrderViews(mapper, paymentRepo),
                new InvoicePdfRenderer(), new InvoiceLinkSigner(SECRET, "https://api.ggfix.in/subscription", 60, clock),
                new InvoiceWhatsAppSender(props, botbee), mapper, TransactionOperations.withoutTransaction(), clock);
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private SubscriptionOrder order(String mode) {
        SubscriptionOrder o = SubscriptionOrder.builder()
                .id(UUID.randomUUID()).orderNo("NGF-SUB-20261102-000001").ownerUserId(owner)
                .planCode("BASIC_6M").planName("Basic").durationLabel("6 Months").shopCount(2)
                .shopsSnapshot(write(List.of(new ShopRef(shopId, "Ravi Mobiles"), new ShopRef(UUID.randomUUID(), "Ravi Mobiles 2"))))
                .quoteSnapshot("{\"subtotalPaise\":200000,\"discountPaise\":10000,\"totalPaise\":190000}")
                .totalPaise(190000L).currency("INR").paymentMode(mode).status(SubscriptionOrder.ACTIVATED)
                .razorpayOrderId("order_" + UUID.randomUUID().toString().substring(0, 8))
                .termStartsAt(NOW).termEndsAt(NOW.plus(Duration.ofDays(181))).paidAt(NOW).activatedAt(NOW)
                .build();
        when(orderRepo.findById(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    private SubscriptionPayment payment(SubscriptionOrder o, String rzpPaymentId, String activation, String mode) {
        SubscriptionPayment p = SubscriptionPayment.builder()
                .id(UUID.randomUUID()).orderId(o.getId()).ownerUserId(owner).razorpayOrderId(o.getRazorpayOrderId())
                .razorpayPaymentId(rzpPaymentId).receiptNo("NGF-RCPT-1").amountPaise(190000L).currency("INR")
                .paymentMode(mode).method("upi").status(SubscriptionPayment.CAPTURED).activationStatus(activation)
                .refundedPaise(0L).paidAt(NOW).build();
        when(paymentRepo.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private static Settings settings(String gstin, String rate, boolean whatsappOn) {
        Settings d = Settings.defaults();
        return new Settings(new BigDecimal(rate), true, d.trialDays(), d.trialMaxShops(), d.trialOncePerOrg(),
                d.gracePeriodDays(), d.quoteTtlMinutes(), d.paymentRetryLimit(), d.renewalReminderDays(),
                d.expiryAlertDays(), d.webCheckoutEnabled(), d.androidCheckoutEnabled(), d.iosCheckoutEnabled(),
                d.receiptBusinessName(), "1 Mount Road, Chennai", gstin, "billing@neargfix.in", null, null, null,
                null, "NGF", whatsappOn, "neargfix_subscription_paid");
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    // ── issuance ────────────────────────────────────────────────────────────

    @Test
    void repeatedIssuanceReturnsTheSameInvoice() {
        InvoiceService s = service(false);

        InvoiceRecord first = s.issueForPayment(payment.getId()).orElseThrow();
        InvoiceRecord second = s.issueForPayment(payment.getId()).orElseThrow();

        assertEquals(first.id(), second.id());
        assertEquals("NGF/26-27/00001", first.invoiceNo());
        assertEquals(1, store.rows.size());
        assertEquals(1, store.nextSeqCalls);
        assertEquals("PAYMENT_RECEIPT", first.documentType());
        assertEquals(InvoiceStore.WA_SKIPPED, first.whatsappStatus());
        assertEquals(InvoiceWhatsAppSender.OFF_REASON, first.whatsappReason());
        assertTrue(store.pdfs.containsKey(first.id()), "PDF is rendered and stored at issuance");
    }

    @Test
    void nextPaymentGetsTheNextNumber() {
        InvoiceService s = service(false);
        s.issueForPayment(payment.getId()).orElseThrow();
        SubscriptionOrder o2 = order("LIVE");
        SubscriptionPayment p2 = payment(o2, "pay_2", "ACTIVATED", "LIVE");

        assertEquals("NGF/26-27/00002", s.issueForPayment(p2.getId()).orElseThrow().invoiceNo());
    }

    @Test
    void onlyActivatedPaymentsAreInvoiced() {
        SubscriptionOrder o2 = order("LIVE");
        SubscriptionPayment dup = payment(o2, "pay_dup", SubscriptionPayment.ACTIVATION_DUPLICATE, "LIVE");

        assertTrue(service(false).issueForPayment(dup.getId()).isEmpty());
        assertTrue(store.rows.isEmpty());
        assertEquals(0, store.nextSeqCalls);
    }

    @Test
    void receiptViewWhenGstIsNotConfigured() {
        InvoiceService s = service(false);
        InvoiceRecord r = s.issueForPayment(payment.getId()).orElseThrow();
        InvoiceView v = s.get(r.id(), owner, false);

        assertEquals("NearGFix Subscription Receipt", v.title());
        assertFalse(v.gst().applicable());
        assertEquals(200000, v.gst().grossPaise());
        assertEquals(10000, v.gst().discountPaise());
        assertEquals(190000, v.gst().taxablePaise());
        assertEquals(0, v.gst().taxPaise());
        assertEquals("NearGFix", v.supplier().brand());
        assertEquals(SettingsService.DEFAULT_BUSINESS_NAME, v.supplier().name());
        assertNull(v.supplier().gstin());
        assertNull(v.supplier().sac());
        assertNull(v.supplier().address());
        assertNull(v.supplier().state());
        assertEquals("Ravi Kumar", v.billTo().ownerName());
        assertEquals("98400 12345", v.billTo().mobile());
        assertEquals("Ravi Mobiles", v.billTo().shopName());
        assertEquals("12 Anna Salai, Chennai", v.billTo().address());
        assertEquals("600002", v.billTo().pincode());
        assertEquals(2, v.subscription().shopCount());
        assertEquals(List.of("Ravi Mobiles", "Ravi Mobiles 2"), v.subscription().shopNames());
        assertEquals("PAID", v.payment().status());
        assertEquals("pay_1", v.payment().razorpayPaymentId());
        assertEquals(InvoiceStore.WA_SKIPPED, v.whatsapp().status());
        assertFalse(v.whatsapp().canRetry());
    }

    @Test
    void taxInvoiceSplitsCgstAndSgstForSameState() {
        when(settings.get()).thenReturn(settings("33AABCU9603R1ZM", "18", false));
        InvoiceService s = service(false);
        InvoiceView v = s.get(s.issueForPayment(payment.getId()).orElseThrow().id(), owner, false);

        assertEquals("TAX_INVOICE", v.documentType());
        assertEquals("NearGFix Subscription Tax Invoice", v.title());
        assertTrue(v.gst().applicable());
        assertFalse(v.gst().interState());
        assertEquals(161017, v.gst().taxablePaise());   // round(190000 × 100 / 118)
        assertEquals(28983, v.gst().taxPaise());
        assertEquals(14492, v.gst().cgstPaise());
        assertEquals(14491, v.gst().sgstPaise());
        assertEquals("Tamil Nadu (33)", v.gst().placeOfSupply());
        assertEquals("33", v.supplier().stateCode());
        assertEquals("Tamil Nadu", v.supplier().state());
        assertEquals("33AABCU9603R1ZM", v.supplier().gstin());
    }

    @Test
    void testModePaymentsUseTheTestSeriesAndTitle() {
        SubscriptionOrder o2 = order("TEST");
        SubscriptionPayment p2 = payment(o2, "pay_test", "ACTIVATED", "TEST");
        InvoiceService s = service(false);
        InvoiceRecord r = s.issueForPayment(p2.getId()).orElseThrow();

        assertEquals("NGF/26-27/T0001", r.invoiceNo());
        assertEquals("NearGFix Subscription Receipt (TEST MODE)", s.get(r.id(), owner, false).title());
    }

    // ── reads / access ──────────────────────────────────────────────────────

    @Test
    void byOrderIsNotFoundUntilIssuedAndOnlyForTheOwner() {
        InvoiceService s = service(false);
        ApiException before = assertThrows(ApiException.class, () -> s.byOrder(order.getId(), owner, false));
        assertEquals(HttpStatus.NOT_FOUND, before.getStatus());

        InvoiceRecord r = s.issueForPayment(payment.getId()).orElseThrow();
        assertEquals(r.id(), s.byOrder(order.getId(), owner, false).id());
        assertThrows(ApiException.class, () -> s.byOrder(order.getId(), UUID.randomUUID(), false));
        assertThrows(ApiException.class, () -> s.get(r.id(), UUID.randomUUID(), false));
        assertEquals(r.id(), s.get(r.id(), UUID.randomUUID(), true).id());   // admin
        assertEquals(1, s.list(owner).size());
        assertTrue(s.list(UUID.randomUUID()).isEmpty());
    }

    @Test
    void pdfIsAPdfWithTheInvoiceNumber() throws Exception {
        InvoiceService s = service(false);
        InvoiceRecord r = s.issueForPayment(payment.getId()).orElseThrow();
        InvoiceService.PdfFile file = s.pdf(r.id(), owner, false);

        assertEquals("NGF-26-27-00001.pdf", file.fileName());
        assertEquals("%PDF", new String(file.bytes(), 0, 4, StandardCharsets.US_ASCII));
        String text = text(file.bytes());
        assertTrue(text.contains("NGF/26-27/00001"), text);
        assertTrue(text.contains("Total Paid"));
    }

    @Test
    void publicPdfNeedsAValidSignature() {
        InvoiceService s = service(false);
        InvoiceRecord r = s.issueForPayment(payment.getId()).orElseThrow();
        InvoiceLinkSigner.SignedLink link = new InvoiceLinkSigner(SECRET, "", 60, clock).sign(r.id());

        assertTrue(s.publicPdf(r.id(), link.exp(), link.sig()).bytes().length > 0);
        ApiException bad = assertThrows(ApiException.class, () -> s.publicPdf(r.id(), link.exp(), "forged"));
        assertEquals(HttpStatus.FORBIDDEN, bad.getStatus());
    }

    @Test
    void historyItemsGainInvoiceFields() {
        InvoiceService s = service(false);
        InvoiceRecord r = s.issueForPayment(payment.getId()).orElseThrow();
        PaymentHistoryItem item = new PaymentHistoryItem(payment.getId(), order.getId(), order.getOrderNo(), NOW,
                "Basic", "6 Months", 2, 190000, "upi", "CAPTURED", "NGF-RCPT-1", null, null, null, null);
        PaymentHistoryItem other = new PaymentHistoryItem(UUID.randomUUID(), UUID.randomUUID(), "X", NOW, "Basic",
                "6 Months", 1, 100, "upi", "CAPTURED", "R2", null, null, null, null);

        List<PaymentHistoryItem> out = s.withInvoices(List.of(item, other));
        assertEquals(r.id(), out.get(0).invoiceId());
        assertEquals("NGF/26-27/00001", out.get(0).invoiceNo());
        assertEquals("PAYMENT_RECEIPT", out.get(0).documentType());
        assertEquals("SKIPPED", out.get(0).whatsappStatus());
        assertNull(out.get(1).invoiceId());
    }

    // ── WhatsApp ────────────────────────────────────────────────────────────

    @Test
    void retryIsRefusedWhileWhatsAppIsOff() {
        InvoiceService s = service(false);
        InvoiceRecord r = s.issueForPayment(payment.getId()).orElseThrow();
        ApiException e = assertThrows(ApiException.class, () -> s.retryWhatsApp(r.id(), owner, false));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(botbee, never()).sendDocumentTemplate(anyString(), anyString(), anyList(), anyString());
    }

    @Test
    void envOnButAdminSettingOffSkips() {
        InvoiceService s = service(true);   // BOTBEE env on, settings.invoiceWhatsappEnabled = false
        InvoiceRecord r = s.issueForPayment(payment.getId()).orElseThrow();
        s.deliverWhatsApp(r.id());
        assertEquals(InvoiceStore.WA_SKIPPED, store.rows.get(r.id()).whatsappStatus());
        verify(botbee, never()).sendDocumentTemplate(anyString(), anyString(), anyList(), anyString());
    }

    @Test
    void sendsTheDocumentTemplateWhenEnabledAndNeverTwice() {
        when(settings.get()).thenReturn(settings(null, "0", true));
        when(botbee.sendDocumentTemplate(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(new BotBeeClient.SendResult(true, 200, "wamid.1", null));
        InvoiceService s = service(true);
        InvoiceRecord r = s.issueForPayment(payment.getId()).orElseThrow();
        assertEquals(InvoiceStore.WA_PENDING, r.whatsappStatus());

        s.deliverWhatsApp(r.id());
        s.deliverWhatsApp(r.id());   // repeated callback / sweep: no second message

        verify(botbee, org.mockito.Mockito.times(1)).sendDocumentTemplate(eq("+919840012345"),
                eq("neargfix_subscription_paid"),
                eq(List.of("Ravi Kumar", "Ravi Mobiles", "Basic", "6 Months", "2", "1,900.00", "NGF/26-27/00001")),
                org.mockito.ArgumentMatchers.contains("/subscriptions/invoices/public/" + r.id() + ".pdf?exp="));
        InvoiceView v = s.get(r.id(), owner, false);
        assertEquals("SENT", v.whatsapp().status());
        assertEquals(NOW, v.whatsapp().sentAt());
        assertFalse(v.whatsapp().canRetry());
        assertThrows(ApiException.class, () -> s.retryWhatsApp(r.id(), owner, false));
    }

    @Test
    void failedSendCanBeRetried() {
        when(settings.get()).thenReturn(settings(null, "0", true));
        when(botbee.sendDocumentTemplate(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(new BotBeeClient.SendResult(false, 400, null, "WhatsApp provider returned 400: template not approved"))
                .thenReturn(new BotBeeClient.SendResult(true, 200, "wamid.2", null));
        InvoiceService s = service(true);
        InvoiceRecord r = s.issueForPayment(payment.getId()).orElseThrow();
        s.deliverWhatsApp(r.id());

        InvoiceView failed = s.get(r.id(), owner, false);
        assertEquals("FAILED", failed.whatsapp().status());
        assertTrue(failed.whatsapp().reason().contains("template not approved"));
        assertTrue(failed.whatsapp().canRetry());

        InvoiceView retried = s.retryWhatsApp(r.id(), owner, false);
        assertEquals("SENT", retried.whatsapp().status());
        assertEquals("wamid.2", store.rows.get(r.id()).whatsappMessageId());
    }

    @Test
    void noValidNumberSkipsWithAReason() {
        when(directory.owner(owner)).thenReturn(Optional.of(new OwnerInfo(owner, "Ravi", null, "12345", "SHOP_OWNER")));
        when(directory.billingShop(shopId)).thenReturn(Optional.empty());
        when(settings.get()).thenReturn(settings(null, "0", true));
        InvoiceService s = service(true);
        InvoiceRecord r = s.issueForPayment(payment.getId()).orElseThrow();
        s.deliverWhatsApp(r.id());

        assertEquals(InvoiceStore.WA_SKIPPED, store.rows.get(r.id()).whatsappStatus());
        assertEquals(InvoiceWhatsAppSender.NO_NUMBER_REASON, store.rows.get(r.id()).whatsappReason());
        verify(botbee, never()).sendDocumentTemplate(anyString(), anyString(), anyList(), any());
    }
}
