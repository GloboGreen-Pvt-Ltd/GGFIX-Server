package com.repairshop.saas.subscription.invoice;

import com.repairshop.saas.subscription.billing.SubscriptionTerms;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceView;
import com.repairshop.saas.subscription.service.SettingsService.Settings;
import com.repairshop.saas.subscription.whatsapp.BotBeeClient;
import com.repairshop.saas.subscription.whatsapp.PhoneNumbers;
import com.repairshop.saas.subscription.whatsapp.WhatsAppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The paid-subscription WhatsApp message (template neargfix_subscription_paid,
 * DOCUMENT header = the signed invoice PDF link). Body vars: {{1}} owner name,
 * {{2}} shop name, {{3}} plan, {{4}} duration, {{5}} shop count, {{6}} amount
 * (no ₹), {{7}} invoice no. Sent only when BOTBEE_WHATSAPP_ENABLED, an API key
 * and settings.invoiceWhatsappEnabled are all on. Phone numbers are masked in logs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceWhatsAppSender {

    public static final String OFF_REASON = "WhatsApp invoices are turned off";
    public static final String NO_NUMBER_REASON = "No valid WhatsApp number on file";

    private final WhatsAppProperties props;
    private final BotBeeClient client;

    /** status: SENT | FAILED | SKIPPED. */
    public record Result(String status, String reason, String messageId, String recipientMasked) {}

    public boolean sendingOn(Settings s) {
        return props.isConfigured() && s != null && s.invoiceWhatsappEnabled();
    }

    public Result send(InvoiceView v, Settings s, String documentUrl) {
        String to = PhoneNumbers.toE164(v.billTo() == null ? null : v.billTo().mobile(), "91");
        if (to == null) return new Result(InvoiceStore.WA_SKIPPED, NO_NUMBER_REASON, null, null);
        String masked = PhoneNumbers.mask(to);
        BotBeeClient.SendResult r = client.sendDocumentTemplate(to, s.invoiceWhatsappTemplate(), params(v), documentUrl);
        if (r.ok()) {
            log.info("Invoice {} sent on WhatsApp to {} (message {})", v.invoiceNo(), masked, r.messageId());
            return new Result(InvoiceStore.WA_SENT, null, r.messageId(), masked);
        }
        log.warn("Invoice {} WhatsApp to {} failed: {}", v.invoiceNo(), masked, r.error());
        return new Result(InvoiceStore.WA_FAILED, r.error() == null ? "WhatsApp send failed" : r.error(), null, masked);
    }

    /** Template body params {{1}}..{{7}}; never blank (Meta rejects empty parameters). */
    static List<String> params(InvoiceView v) {
        String owner = v.billTo() == null ? null : v.billTo().ownerName();
        String shop = v.billTo() == null ? null : v.billTo().shopName();
        String plan = v.subscription() == null ? null : v.subscription().planName();
        String duration = v.subscription() == null ? null : v.subscription().durationLabel();
        int shops = v.subscription() == null ? 1 : Math.max(1, v.subscription().shopCount());
        long total = v.gst() == null ? 0 : v.gst().totalPaise();
        return List.of(
                or(owner, "Partner"),
                or(shop, "your shop"),
                or(plan, "NearGFix"),
                or(duration, "-"),
                String.valueOf(shops),
                SubscriptionTerms.rupees(total).replace("₹", ""),
                or(v.invoiceNo(), "-"));
    }

    private static String or(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }
}
