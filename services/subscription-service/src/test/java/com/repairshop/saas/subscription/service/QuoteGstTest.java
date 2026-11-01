package com.repairshop.saas.subscription.service;

import com.repairshop.saas.subscription.dto.v2.V2Views.QuoteView;
import com.repairshop.saas.subscription.service.OwnerDirectory.BillingShop;
import com.repairshop.saas.subscription.service.OwnerDirectory.ShopInfo;
import com.repairshop.saas.subscription.service.SettingsService.Settings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Quotes carry the §2 `gst` object; the legacy tax figures follow the same "GSTIN AND rate > 0" rule. */
class QuoteGstTest {

    private TestStore t;
    private UUID owner;

    @BeforeEach
    void setUp() {
        t = new TestStore();
        t.plan("PREMIUM_12M", "PREMIUM", 120000, "10", 12, "MONTHS", 5, null, null);
        owner = UUID.randomUUID();
    }

    private static Settings settings(String gstin, String rate) {
        Settings d = Settings.defaults();
        return new Settings(new BigDecimal(rate), true, d.trialDays(), d.trialMaxShops(), d.trialOncePerOrg(),
                d.gracePeriodDays(), d.quoteTtlMinutes(), d.paymentRetryLimit(), d.renewalReminderDays(),
                d.expiryAlertDays(), d.webCheckoutEnabled(), d.androidCheckoutEnabled(), d.iosCheckoutEnabled(),
                d.receiptBusinessName(), null, gstin, null, null, null, null, null, "NGF", false,
                "neargfix_subscription_paid");
    }

    @Test
    void quoteHasGstObjectAndNoTaxWithoutGstin() {
        when(t.settings.get()).thenReturn(settings(null, "18"));   // rate set, but no supplier GSTIN
        List<UUID> ids = t.ownShops(owner, 2).stream().map(ShopInfo::id).toList();
        QuoteView q = t.quotes.quote(owner, "PREMIUM_12M", ids, null);

        assertNotNull(q.gst());
        assertFalse(q.gst().applicable());
        assertEquals("PAYMENT_RECEIPT", q.gst().documentType());
        assertEquals(228000, q.totalPaise());                     // 120000 + 108000
        assertEquals(q.totalPaise(), q.gst().totalPaise());
        assertEquals(q.totalPaise(), q.gst().taxablePaise());
        assertEquals(240000, q.gst().grossPaise());
        assertEquals(12000, q.gst().discountPaise());
        assertEquals(0, q.taxPaise(), "legacy taxPaise follows the GST rule");
        assertEquals(0, q.taxPercent().signum());
    }

    @Test
    void quoteSplitsGstFromTheBillingShop() {
        when(t.settings.get()).thenReturn(settings("33AABCU9603R1ZM", "18"));
        List<ShopInfo> shops = t.ownShops(owner, 1);
        when(t.directory.billingShop(shops.get(0).id())).thenReturn(Optional.of(new BillingShop(shops.get(0).id(),
                "Shop 1", null, null, null, null, "Karnataka", null, null)));
        QuoteView q = t.quotes.quote(owner, "PREMIUM_12M", List.of(shops.get(0).id()), null);

        assertTrue(q.gst().applicable());
        assertEquals("TAX_INVOICE", q.gst().documentType());
        assertTrue(q.gst().interState());
        assertEquals("Karnataka (29)", q.gst().placeOfSupply());
        assertEquals(101695, q.gst().taxablePaise());
        assertEquals(18305, q.gst().igstPaise());
        assertEquals(q.gst().taxPaise(), q.taxPaise());
        assertEquals(120000, q.totalPaise(), "the payable amount never changes");
    }

    @Test
    void previewAlsoCarriesGst() {
        when(t.settings.get()).thenReturn(settings("33AABCU9603R1ZM", "18"));
        QuoteView q = t.quotes.preview(null, "PREMIUM_12M", 1);
        assertNotNull(q.gst());
        assertTrue(q.gst().applicable());
        assertNull(q.gst().placeOfSupply());                       // no shop → unknown → IGST
        assertEquals(18305, q.gst().igstPaise());
    }
}
