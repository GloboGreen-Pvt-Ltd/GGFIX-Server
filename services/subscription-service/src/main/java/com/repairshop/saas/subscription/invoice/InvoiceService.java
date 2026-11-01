package com.repairshop.saas.subscription.invoice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.subscription.billing.GstCalculator;
import com.repairshop.saas.subscription.billing.GstStateCodes;
import com.repairshop.saas.subscription.dto.v2.V2Views.GstBreakdown;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceBillTo;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoicePayment;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceSubscription;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceSupplier;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceView;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceWhatsApp;
import com.repairshop.saas.subscription.dto.v2.V2Views.PaymentHistoryItem;
import com.repairshop.saas.subscription.dto.v2.V2Views.QuoteView;
import com.repairshop.saas.subscription.dto.v2.V2Views.ShopRef;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.entity.SubscriptionPayment;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.invoice.InvoiceStore.InvoiceRecord;
import com.repairshop.saas.subscription.invoice.InvoiceStore.NewInvoice;
import com.repairshop.saas.subscription.razorpay.RazorpayProperties;
import com.repairshop.saas.subscription.repository.SubscriptionOrderRepository;
import com.repairshop.saas.subscription.repository.SubscriptionPaymentRepository;
import com.repairshop.saas.subscription.service.OrderViews;
import com.repairshop.saas.subscription.service.OwnerDirectory;
import com.repairshop.saas.subscription.service.OwnerDirectory.BillingShop;
import com.repairshop.saas.subscription.service.OwnerDirectory.OwnerInfo;
import com.repairshop.saas.subscription.service.SettingsService;
import com.repairshop.saas.subscription.service.SettingsService.Settings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Subscription invoices (invoice contract §3/§4): one per captured, activated
 * payment, idempotent. Issuance runs after the payment transaction has
 * committed and is failure-tolerant — nothing here can fail a checkout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    public static final String BRAND = "NearGFix";
    public static final String TITLE_RECEIPT = "NearGFix Subscription Receipt";
    public static final String TITLE_TAX_INVOICE = "NearGFix Subscription Tax Invoice";

    private final InvoiceStore store;
    private final SubscriptionPaymentRepository payments;
    private final SubscriptionOrderRepository orders;
    private final SettingsService settings;
    private final OwnerDirectory directory;
    private final OrderViews views;
    private final InvoicePdfRenderer renderer;
    private final InvoiceLinkSigner signer;
    private final InvoiceWhatsAppSender whatsapp;
    private final ObjectMapper mapper;
    private final TransactionOperations tx;
    private final Clock clock;

    public record PdfFile(String fileName, byte[] bytes) {}

    // ── issuance ────────────────────────────────────────────────────────────

    /**
     * Issue the invoice of a captured, ACTIVATED payment, or return the one that
     * already exists (by payment or order). Empty when the payment is not
     * invoiceable (duplicate / needs-review payments get no invoice).
     */
    public Optional<InvoiceRecord> issueForPayment(UUID paymentId) {
        Optional<InvoiceRecord> existing = store.findByPaymentId(paymentId);
        if (existing.isPresent()) return existing;
        SubscriptionPayment p = payments.findById(paymentId).orElse(null);
        if (p == null || !SubscriptionPayment.ACTIVATION_ACTIVATED.equals(p.getActivationStatus())) {
            return Optional.empty();
        }
        SubscriptionOrder o = orders.findById(p.getOrderId()).orElse(null);
        if (o == null) return Optional.empty();
        Optional<InvoiceRecord> byOrder = store.findByOrderId(o.getId());
        if (byOrder.isPresent()) return byOrder;

        // Everything that reads other tables happens before the short numbering transaction.
        Settings s = settings.get();
        Instant issuedAt = clock.instant();
        InvoiceView draft = buildView(p, o, s, issuedAt, null, null);
        boolean test = RazorpayProperties.TEST.equals(p.getPaymentMode());
        String fy = InvoiceNumbers.financialYear(issuedAt);
        String prefix = InvoiceNumbers.prefix(s.invoicePrefix());
        String series = InvoiceNumbers.series(prefix, fy, test);
        boolean sendOn = whatsapp.sendingOn(s);
        UUID id = UUID.randomUUID();

        InvoiceRecord rec;
        try {
            rec = tx.execute(status -> {
                store.lockPayment(paymentId);
                Optional<InvoiceRecord> again = store.findByPaymentId(paymentId);
                if (again.isEmpty()) again = store.findByOrderId(o.getId());
                if (again.isPresent()) return again.get();
                long seq = store.nextSeq(series);
                String invoiceNo = InvoiceNumbers.format(prefix, fy, seq, test);
                InvoiceView v = withIdentity(draft, id, invoiceNo);
                GstBreakdown g = v.gst();
                return store.insert(new NewInvoice(id, invoiceNo, fy, seq, paymentId, o.getId(), o.getOwnerUserId(),
                        g.documentType(), v.title(), p.getPaymentMode(), g.totalPaise(), g.taxablePaise(),
                        g.cgstPaise(), g.sgstPaise(), g.igstPaise(), g.taxPaise(), g.placeOfSupply(), write(v),
                        sendOn ? InvoiceStore.WA_PENDING : InvoiceStore.WA_SKIPPED,
                        sendOn ? null : InvoiceWhatsAppSender.OFF_REASON, issuedAt));
            });
        } catch (DuplicateKeyException race) {
            Optional<InvoiceRecord> winner = store.findByPaymentId(paymentId);
            return winner.isPresent() ? winner : store.findByOrderId(o.getId());
        }
        if (rec == null) return Optional.empty();
        if (rec.id().equals(id)) {
            log.info("Invoice {} issued for order {} ({})", rec.invoiceNo(), o.getOrderNo(), rec.documentType());
            try {
                ensurePdf(rec);
            } catch (Exception e) {
                log.warn("Invoice {} PDF not generated yet (served on demand): {}", rec.invoiceNo(), e.toString());
            }
        }
        return Optional.of(rec);
    }

    /** The invoice view from the payment + order snapshot + settings at issuance (id/no may be null for a draft). */
    InvoiceView buildView(SubscriptionPayment p, SubscriptionOrder o, Settings s, Instant issuedAt, UUID id,
                          String invoiceNo) {
        QuoteView q = views.quote(o.getQuoteSnapshot());
        List<ShopRef> shops = views.shops(o.getShopsSnapshot());
        BillingShop shop = shops.isEmpty() ? null : directory.billingShop(shops.get(0).shopId()).orElse(null);
        OwnerInfo owner = directory.owner(o.getOwnerUserId()).orElse(null);

        long total = p.getAmountPaise() == null ? (o.getTotalPaise() == null ? 0 : o.getTotalPaise()) : p.getAmountPaise();
        long discount = q == null ? 0 : Math.max(0, q.discountPaise());
        GstCalculator.PlaceOfSupply pos = shop == null ? null : GstCalculator.placeOfSupply(shop.gstNumber(), shop.state());
        GstBreakdown gst = GstCalculator.compute(total, total + discount, discount, s.taxPercent(), s.gstApplicable(),
                s.supplierStateCode(), pos);

        String supplierCode = s.supplierStateCode();
        String supplierState = s.receiptState() != null ? s.receiptState() : GstStateCodes.name(supplierCode);
        InvoiceSupplier supplier = new InvoiceSupplier(BRAND, s.receiptBusinessName(), s.receiptAddress(), supplierState,
                supplierCode, s.receiptGstin(), s.receiptSac(), s.receiptEmail(), s.receiptPhone());

        String shopState = shop == null ? null
                : notBlank(shop.state()) ? shop.state().trim() : pos == null ? null : GstStateCodes.name(pos.code());
        InvoiceBillTo billTo = new InvoiceBillTo(
                owner == null ? null : trim(owner.name()),
                firstNonBlank(owner == null ? null : owner.phone(), shop == null ? null : shop.mobile(),
                        shop == null ? null : shop.phone()),
                shop != null && notBlank(shop.name()) ? shop.name().trim() : shops.isEmpty() ? null : shops.get(0).name(),
                shop == null ? null : address(shop.address(), shop.city()),
                shopState,
                shop == null ? null : trim(shop.pincode()),
                shop == null ? null : GstStateCodes.validGstin(shop.gstNumber()));

        InvoiceSubscription subscription = new InvoiceSubscription(o.getPlanName(), o.getDurationLabel(),
                o.getShopCount() != null ? o.getShopCount() : shops.size(),
                shops.stream().map(ShopRef::name).filter(Objects::nonNull).toList(),
                o.getTermStartsAt(), o.getTermEndsAt());

        InvoicePayment payment = new InvoicePayment(o.getOrderNo(), p.getRazorpayOrderId(), p.getRazorpayPaymentId(),
                p.getMethod(), "PAID", p.getCurrency() == null ? "INR" : p.getCurrency(), p.getPaidAt());

        String title = (gst.applicable() ? TITLE_TAX_INVOICE : TITLE_RECEIPT)
                + (RazorpayProperties.TEST.equals(p.getPaymentMode()) ? " (TEST MODE)" : "");
        return new InvoiceView(id, invoiceNo, gst.documentType(), title, issuedAt, supplier, billTo, subscription, gst,
                payment, null);
    }

    // ── PDF ─────────────────────────────────────────────────────────────────

    /** Stored PDF, or render it now (and store it, best effort). */
    byte[] ensurePdf(InvoiceRecord r) throws IOException {
        if (r.hasPdf()) {
            Optional<byte[]> stored = store.pdf(r.id());
            if (stored.isPresent()) return stored.get();
        }
        byte[] pdf = renderer.render(parse(r));
        try {
            store.savePdf(r.id(), pdf, clock.instant());
        } catch (RuntimeException e) {
            log.warn("Invoice {} PDF could not be stored: {}", r.invoiceNo(), e.toString());
        }
        return pdf;
    }

    public PdfFile pdf(UUID id, UUID callerUserId, boolean admin) {
        InvoiceRecord r = owned(id, callerUserId, admin);
        return new PdfFile(InvoiceNumbers.fileName(r.invoiceNo()), pdfBytes(r));
    }

    /** No JWT: only a valid, unexpired HMAC link (the WhatsApp document header) opens it. */
    public PdfFile publicPdf(UUID id, long exp, String sig) {
        if (!signer.verify(id, exp, sig)) {
            throw ApiException.forbidden("INVALID_LINK", "This invoice link is invalid or has expired");
        }
        InvoiceRecord r = find(id).orElseThrow(() -> ApiException.notFound("INVOICE_NOT_FOUND", "Invoice not found"));
        return new PdfFile(InvoiceNumbers.fileName(r.invoiceNo()), pdfBytes(r));
    }

    private byte[] pdfBytes(InvoiceRecord r) {
        try {
            return ensurePdf(r);
        } catch (Exception e) {
            log.error("Invoice {} PDF could not be generated", r.invoiceNo(), e);
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "PDF_UNAVAILABLE",
                    "The invoice PDF is not available right now. Please try again shortly.");
        }
    }

    // ── reads ───────────────────────────────────────────────────────────────

    public List<InvoiceView> list(UUID ownerUserId) {
        List<InvoiceRecord> rows;
        try {
            rows = store.findByOwner(ownerUserId);
        } catch (RuntimeException e) {
            log.warn("Could not list invoices: {}", e.toString());
            return List.of();
        }
        boolean sendOn = whatsapp.sendingOn(settings.get());
        return rows.stream().map(r -> view(r, sendOn)).toList();
    }

    public InvoiceView get(UUID id, UUID callerUserId, boolean admin) {
        return view(owned(id, callerUserId, admin), whatsapp.sendingOn(settings.get()));
    }

    /** 404 until the invoice exists (the success screen polls this a few times). */
    public InvoiceView byOrder(UUID orderId, UUID callerUserId, boolean admin) {
        Optional<InvoiceRecord> r;
        try {
            r = store.findByOrderId(orderId);
        } catch (RuntimeException e) {
            log.warn("Could not read invoice of order {}: {}", orderId, e.toString());
            r = Optional.empty();
        }
        InvoiceRecord rec = r.filter(x -> admin || x.ownerUserId().equals(callerUserId))
                .orElseThrow(() -> ApiException.notFound("INVOICE_NOT_FOUND", "Invoice not issued yet"));
        return view(rec, whatsapp.sendingOn(settings.get()));
    }

    /** Payment history + invoiceId / invoiceNo / documentType / whatsappStatus (additive). */
    public List<PaymentHistoryItem> withInvoices(List<PaymentHistoryItem> items) {
        if (items == null || items.isEmpty()) return items;
        Map<UUID, InvoiceRecord> byPayment;
        try {
            byPayment = store.findByPaymentIds(items.stream().map(PaymentHistoryItem::paymentId).toList());
        } catch (RuntimeException e) {
            log.warn("Could not attach invoices to the payment history: {}", e.toString());
            return items;
        }
        List<PaymentHistoryItem> out = new ArrayList<>(items.size());
        for (PaymentHistoryItem i : items) {
            InvoiceRecord r = byPayment.get(i.paymentId());
            out.add(r == null ? i : new PaymentHistoryItem(i.paymentId(), i.orderId(), i.orderNo(), i.paidAt(),
                    i.planName(), i.durationLabel(), i.shopCount(), i.amountPaise(), i.method(), i.status(),
                    i.receiptNo(), r.id(), r.invoiceNo(), r.documentType(), r.whatsappStatus()));
        }
        return out;
    }

    // ── WhatsApp ────────────────────────────────────────────────────────────

    /** Automatic first send right after issuance (or from the sweep). Never resends. */
    public void deliverWhatsApp(UUID invoiceId) {
        InvoiceRecord r = store.findById(invoiceId).orElse(null);
        if (r == null || !InvoiceStore.WA_PENDING.equals(r.whatsappStatus()) || r.whatsappAttempts() > 0) return;
        Settings s = settings.get();
        if (!whatsapp.sendingOn(s)) {
            store.finishWhatsApp(r.id(), InvoiceStore.WA_SKIPPED, InvoiceWhatsAppSender.OFF_REASON, null, null, null);
            return;
        }
        if (!store.claimWhatsApp(r.id(), InvoiceStore.WA_PENDING, 0, clock.instant())) return;
        send(r, s);
    }

    /** Owner/admin retry: 409 unless the last outcome was FAILED/SKIPPED and sending is on. */
    public InvoiceView retryWhatsApp(UUID id, UUID callerUserId, boolean admin) {
        InvoiceRecord r = owned(id, callerUserId, admin);
        Settings s = settings.get();
        if (!whatsapp.sendingOn(s)) {
            throw ApiException.conflict("WHATSAPP_OFF", InvoiceWhatsAppSender.OFF_REASON);
        }
        if (!InvoiceStore.WA_FAILED.equals(r.whatsappStatus()) && !InvoiceStore.WA_SKIPPED.equals(r.whatsappStatus())) {
            throw ApiException.conflict("WHATSAPP_NOT_RETRYABLE", InvoiceStore.WA_SENT.equals(r.whatsappStatus())
                    ? "This invoice was already sent on WhatsApp" : "This invoice is being sent on WhatsApp");
        }
        if (!store.claimWhatsApp(r.id(), r.whatsappStatus(), r.whatsappAttempts(), clock.instant())) {
            throw ApiException.conflict("WHATSAPP_IN_PROGRESS", "This invoice is being sent on WhatsApp");
        }
        send(r, s);
        InvoiceRecord fresh = store.findById(id).orElse(r);
        return view(fresh, true);
    }

    private void send(InvoiceRecord r, Settings s) {
        InvoiceWhatsAppSender.Result res;
        try {
            ensurePdf(r);
            res = whatsapp.send(parse(r), s, signer.sign(r.id()).url());
        } catch (Exception e) {
            log.warn("Invoice {} WhatsApp send failed: {}", r.invoiceNo(), e.toString());
            res = new InvoiceWhatsAppSender.Result(InvoiceStore.WA_FAILED, "WhatsApp send failed", null, null);
        }
        store.finishWhatsApp(r.id(), res.status(), res.reason(), res.messageId(), res.recipientMasked(),
                InvoiceStore.WA_SENT.equals(res.status()) ? clock.instant() : null);
    }

    // ── recovery sweep helpers ──────────────────────────────────────────────

    /** Activated payments of the last {@code lookbackDays} with no invoice (older than 2 minutes). */
    public List<UUID> paymentsMissingInvoice(int lookbackDays) {
        Instant now = clock.instant();
        return store.paymentsMissingInvoice(now.minus(Duration.ofDays(Math.max(1, lookbackDays))),
                now.minus(Duration.ofMinutes(2)), 25);
    }

    /** First sends that never started, and claimed sends that never finished. */
    public void recoverWhatsApp() {
        Instant now = clock.instant();
        int stale = store.failStaleWhatsApp(now.minus(Duration.ofMinutes(15)), now);
        if (stale > 0) log.warn("{} invoice WhatsApp send(s) were interrupted and marked FAILED", stale);
        for (UUID id : store.unsentWhatsApp(now.minus(Duration.ofMinutes(5)), 25)) {
            try {
                deliverWhatsApp(id);
            } catch (Exception e) {
                log.warn("Invoice WhatsApp recovery for {} failed: {}", id, e.toString());
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Optional<InvoiceRecord> find(UUID id) {
        try {
            return store.findById(id);
        } catch (RuntimeException e) {
            log.warn("Could not read invoice {}: {}", id, e.toString());
            return Optional.empty();
        }
    }

    private InvoiceRecord owned(UUID id, UUID callerUserId, boolean admin) {
        return find(id).filter(r -> admin || r.ownerUserId().equals(callerUserId))
                .orElseThrow(() -> ApiException.notFound("INVOICE_NOT_FOUND", "Invoice not found"));
    }

    InvoiceView view(InvoiceRecord r, boolean sendOn) {
        InvoiceView v = parse(r);
        String status = r.whatsappStatus() == null ? InvoiceStore.WA_PENDING : r.whatsappStatus();
        boolean canRetry = sendOn && (InvoiceStore.WA_FAILED.equals(status) || InvoiceStore.WA_SKIPPED.equals(status));
        return new InvoiceView(r.id(), r.invoiceNo(), r.documentType(), v.title(), r.issuedAt(), v.supplier(),
                v.billTo(), v.subscription(), v.gst(), v.payment(),
                new InvoiceWhatsApp(status, r.whatsappReason(), r.whatsappSentAt(), canRetry));
    }

    private InvoiceView parse(InvoiceRecord r) {
        try {
            InvoiceView v = mapper.readValue(r.viewJson(), InvoiceView.class);
            return withIdentity(v, r.id(), r.invoiceNo());
        } catch (Exception e) {
            log.warn("Invoice {} view could not be read: {}", r.invoiceNo(), e.toString());
            return new InvoiceView(r.id(), r.invoiceNo(), r.documentType(),
                    GstCalculator.TAX_INVOICE.equals(r.documentType()) ? TITLE_TAX_INVOICE : TITLE_RECEIPT,
                    r.issuedAt(), null, null, null, null, null, null);
        }
    }

    private static InvoiceView withIdentity(InvoiceView v, UUID id, String invoiceNo) {
        return new InvoiceView(id, invoiceNo, v.documentType(), v.title(), v.issuedAt(), v.supplier(), v.billTo(),
                v.subscription(), v.gst(), v.payment(), null);
    }

    private String write(InvoiceView v) {
        try {
            return mapper.writeValueAsString(v);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise invoice", e);
        }
    }

    private static String address(String address, String city) {
        String a = trim(address);
        String c = trim(city);
        if (a == null) return c;
        if (c == null || a.toLowerCase(Locale.ROOT).contains(c.toLowerCase(Locale.ROOT))) return a;
        return a + ", " + c;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (notBlank(v)) return v.trim();
        return null;
    }

    private static String trim(String s) {
        return notBlank(s) ? s.trim() : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
