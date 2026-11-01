package com.repairshop.saas.subscription.dto.v2;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response bodies of the Subscription v2 contract (field names are binding). Money = integer paise. */
public final class V2Views {

    private V2Views() {}

    public record PlanView(
            UUID id, String code, String name, String planType, String description,
            long pricePaise, String currency, int durationValue, String durationUnit, String durationLabel,
            Integer employeeLimit, String employeeLimitScope, Integer shopLimit, Integer sellOrderLimit,
            Integer buyOrderLimit, boolean serviceBookingEnabled, boolean pickupEnabled, boolean smsEnabled,
            boolean whatsappEnabled, boolean reportsEnabled, boolean exportEnabled, boolean prioritySupportEnabled,
            BigDecimal additionalShopDiscountPct, String status, int displayOrder, String color, String icon,
            int version, List<String> features, boolean purchasable) {}

    public record CheckoutFlags(boolean webEnabled, boolean androidEnabled, boolean iosEnabled, boolean configured,
                                String mode) {}   // mode: TEST | LIVE | UNCONFIGURED (test banner)

    public record CatalogView(List<PlanView> plans, CheckoutFlags checkout, BigDecimal taxPercent, boolean taxInclusive) {}

    public record Line(String label, long amountPaise) {}

    public record QuoteView(
            String planCode, String planName, String durationLabel, String purpose, int shopCount, List<UUID> shopIds,
            long firstShopPaise, int additionalShopsCount, long additionalShopUnitPaise, long additionalShopsTotalPaise,
            BigDecimal additionalShopDiscountPct, long discountPaise, long subtotalPaise, BigDecimal taxPercent,
            boolean taxInclusive, long taxPaise, long totalPaise, String currency, List<Line> lines,
            Instant startsAt, Instant endsAt, long renewalPricePaise, boolean replacesCurrent, String notice,
            GstBreakdown gst) {}   // additive: GST split of totalPaise (invoice contract §2)

    /**
     * GST split of an inclusive amount (invoice contract §2). Not applicable (no supplier GSTIN or a 0 rate)
     * → documentType PAYMENT_RECEIPT, taxable = total, every tax 0.
     */
    public record GstBreakdown(
            boolean applicable, String documentType, BigDecimal ratePercent, boolean inclusive, long grossPaise,
            long discountPaise, long taxablePaise, boolean interState, BigDecimal cgstPercent, long cgstPaise,
            BigDecimal sgstPercent, long sgstPaise, BigDecimal igstPercent, long igstPaise, long taxPaise,
            long totalPaise, String placeOfSupply, String supplierStateCode) {}

    public record ShopRef(UUID shopId, String name) {}

    public record OrderView(
            UUID id, String orderNo, String status, String planCode, String planName, String durationLabel,
            String channel, List<UUID> shopIds, List<ShopRef> shops, JsonNode quote, long totalPaise,
            String razorpayOrderId, String razorpayPaymentId, String paymentMethod, Instant createdAt,
            Instant paidAt, Instant activatedAt, UUID subscriptionId, String failureReason,
            UUID paymentId, String paymentMode) {}   // additive: subscription_payments.id for /receipts/{paymentId}; TEST | LIVE

    public record SubscriptionSummary(
            UUID id, String planCode, String planName, String planType, String durationLabel, String status,
            Instant startedAt, Instant endsAt, long daysRemaining, long amountPaidPaise, Integer shopCount,
            boolean legacy) {}

    public record CoveredShop(UUID shopId, String name, Instant startsAt, Instant endsAt, String status) {}

    public record EntitlementsView(
            Integer employeeLimit, String employeeLimitScope, Integer shopLimit, Integer sellOrderLimit,
            Integer buyOrderLimit, boolean serviceBookingEnabled, boolean pickupEnabled, boolean smsEnabled,
            boolean whatsappEnabled, boolean reportsEnabled, boolean exportEnabled, boolean prioritySupportEnabled) {}

    public record UsageView(long shopsUsed, long employeesUsedMaxPerShop, long sellOrdersUsed) {}

    public record MeView(
            SubscriptionSummary subscription, List<CoveredShop> shops, EntitlementsView entitlements,
            UsageView usage, boolean trialAvailable, OrderView pendingOrder) {}

    public record Coverage(String status, String planName, Instant endsAt) {}

    public record EligibleShop(UUID shopId, String name, String shopCode, String location, Coverage coverage,
                               boolean selectable, String reason) {}

    public record PaymentOrderView(
            UUID orderId, String razorpayOrderId, String keyId, long amount, String currency, String name,
            String description, Prefill prefill, java.util.Map<String, String> notes, JsonNode checkout) {}

    public record Prefill(String name, String contact, String email) {}

    public record VerifyResult(OrderView order, SubscriptionSummary subscription) {}

    public record OrderResult(OrderView order) {}

    public record PaymentHistoryItem(
            UUID paymentId, UUID orderId, String orderNo, Instant paidAt, String planName, String durationLabel,
            int shopCount, long amountPaise, String method, String status, String receiptNo,
            UUID invoiceId, String invoiceNo, String documentType, String whatsappStatus) {}   // additive (invoice §4)

    // ── Invoices (invoice contract §3) ──────────────────────────────────────

    public record InvoiceSupplier(String brand, String name, String address, String state, String stateCode,
                                  String gstin, String sac, String email, String phone) {}

    public record InvoiceBillTo(String ownerName, String mobile, String shopName, String address, String state,
                                String pincode, String gstin) {}

    public record InvoiceSubscription(String planName, String durationLabel, int shopCount, List<String> shopNames,
                                      Instant startsAt, Instant endsAt) {}

    public record InvoicePayment(String orderNo, String razorpayOrderId, String razorpayPaymentId, String method,
                                 String status, String currency, Instant paidAt) {}

    /** status: PENDING | SENT | FAILED | SKIPPED. */
    public record InvoiceWhatsApp(String status, String reason, Instant sentAt, boolean canRetry) {}

    public record InvoiceView(
            UUID id, String invoiceNo, String documentType, String title, Instant issuedAt, InvoiceSupplier supplier,
            InvoiceBillTo billTo, InvoiceSubscription subscription, GstBreakdown gst, InvoicePayment payment,
            InvoiceWhatsApp whatsapp) {}

    public record IssuedBy(String name, String address, String gstin, String email, String phone) {}

    public record ReceiptOwner(String name, String businessName, String phone, String email) {}

    public record ReceiptView(
            String receiptNo, UUID subscriptionId, String orderNo, IssuedBy issuedBy, ReceiptOwner owner,
            String planName, String durationLabel, Instant startsAt, Instant endsAt, List<String> shops,
            List<Line> lines, long discountPaise, BigDecimal taxPercent, long taxPaise, long totalPaise,
            String currency, String razorpayPaymentId, String paymentMethod, Instant paidAt, String status,
            String documentTitle, boolean isTaxInvoice, String paymentMode) {}

    // ── Management ──────────────────────────────────────────────────────────

    public record AdminSubscriptionRow(
            UUID id, UUID ownerUserId, String ownerName, String businessName, String ownerPhone, String planCode,
            String planName, String durationLabel, Integer shopCount, long employeeUsage, Instant startedAt,
            Instant endsAt, long daysRemaining, long amountPaidPaise, String paymentStatus, String status,
            boolean legacy, String paymentMode) {}

    public record AdminPaymentRow(
            UUID paymentId, String razorpayOrderId, String razorpayPaymentId, String ownerName,
            String businessName, String planName, long amountPaise, String method, Instant paidAt, String status,
            String activationStatus, String receiptNo, String orderNo, long refundedPaise, String paymentMode) {}

    public record AdminSubscriptionDetail(
            UUID id, UUID ownerUserId, String ownerName, String businessName, String ownerPhone, String ownerEmail,
            String planCode, String planName, String planType, String durationLabel, Integer shopCount,
            long employeeUsage, Instant startedAt, Instant endsAt, long daysRemaining, long amountPaidPaise,
            String paymentStatus, String status, boolean legacy, String source, Instant cancelledAt,
            String cancelReason, EntitlementsView entitlements, List<CoveredShop> shops,
            List<AdminPaymentRow> payments, List<OrderView> orders, String paymentMode) {}

    public record AdminSettingsView(
            String razorpayMode, boolean razorpayConfigured, boolean webhookConfigured, String keyIdMasked,
            BigDecimal taxPercent, boolean taxInclusive, int trialDays, int trialMaxShops, boolean trialOncePerOrg,
            int renewalReminderDays, int expiryAlertDays, int paymentRetryLimit, int gracePeriodDays,
            int quoteTtlMinutes, boolean webCheckoutEnabled, boolean androidCheckoutEnabled,
            boolean iosCheckoutEnabled, String receiptBusinessName, String receiptAddress, String receiptGstin,
            String receiptEmail, String receiptPhone, boolean razorpayModeMismatch,
            String razorpayKeyMode, String razorpayStatusMessage,
            // additive (invoice contract §1)
            String receiptState, String receiptStateCode, String receiptSac, String invoicePrefix,
            boolean invoiceWhatsappEnabled, String invoiceWhatsappTemplate) {}

    public record AuditLogView(UUID id, Instant at, UUID actorUserId, String actorRole, String action,
                               String entityType, String entityId, JsonNode details) {}
}
