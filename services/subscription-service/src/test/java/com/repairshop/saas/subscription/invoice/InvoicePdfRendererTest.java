package com.repairshop.saas.subscription.invoice;

import com.repairshop.saas.subscription.billing.GstCalculator;
import com.repairshop.saas.subscription.dto.v2.V2Views.GstBreakdown;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceBillTo;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoicePayment;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceSubscription;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceSupplier;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceView;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The rendered A4 PDF: valid file, invoice number, GST rows only when GST applies. */
class InvoicePdfRendererTest {

    private static final Instant NOW = Instant.parse("2026-11-02T06:30:00Z");
    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    private static InvoiceView view(GstBreakdown gst, String supplierGstin, String sac, String title) {
        return new InvoiceView(UUID.randomUUID(), "NGF/26-27/00042", gst.documentType(), title, NOW,
                new InvoiceSupplier("NearGFix", "Globogreen System & Technology Private Limited", supplierGstin == null
                        ? null : "1 Mount Road, Chennai", supplierGstin == null ? null : "Tamil Nadu",
                        supplierGstin == null ? null : "33", supplierGstin, sac, null, null),
                new InvoiceBillTo("Ravi Kumar", "9840012345", "Ravi Mobiles ரவி", "12 Anna Salai, Chennai", "Tamil Nadu",
                        "600002", null),
                new InvoiceSubscription("Premium", "12 Months", 2, List.of("Ravi Mobiles", "Ravi Mobiles Annex"),
                        NOW, NOW.plusSeconds(365L * 86400)),
                gst,
                new InvoicePayment("NGF-SUB-20261102-000042", "order_ABC", "pay_XYZ", "upi", "PAID", "INR", NOW),
                null);
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void taxInvoiceShowsCgstSgstSacAndPlaceOfSupply() throws Exception {
        GstBreakdown gst = GstCalculator.compute(120000, 120000, 0, new BigDecimal("18"), true, "33",
                GstCalculator.placeOfSupply(null, "Tamil Nadu"));
        byte[] pdf = renderer.render(view(gst, "33AABCU9603R1ZM", "998431", "NearGFix Subscription Tax Invoice"));

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        String text = text(pdf);
        assertTrue(text.contains("NGF/26-27/00042"));
        assertTrue(text.contains("TAX INVOICE"));
        assertTrue(text.contains("CGST @ 9%"));
        assertTrue(text.contains("SGST @ 9%"));
        assertFalse(text.contains("IGST"));
        assertTrue(text.contains("998431"));
        assertTrue(text.contains("Tamil Nadu (33)"));
        assertTrue(text.contains("33AABCU9603R1ZM"));
        assertTrue(text.contains("₹1,200.00"), "rupee glyph from the embedded font");
        assertTrue(text.contains("This is a computer-generated document."));
    }

    @Test
    void interStateTaxInvoiceShowsIgstOnly() throws Exception {
        GstBreakdown gst = GstCalculator.compute(120000, 120000, 0, new BigDecimal("18"), true, "33",
                GstCalculator.placeOfSupply(null, "Kerala"));
        String text = text(renderer.render(view(gst, "33AABCU9603R1ZM", null, "NearGFix Subscription Tax Invoice")));
        assertTrue(text.contains("IGST @ 18%"));
        assertFalse(text.contains("CGST"));
        assertFalse(text.contains("SAC"));
    }

    @Test
    void receiptHasNoTaxRows() throws Exception {
        GstBreakdown gst = GstCalculator.compute(190000, 200000, 10000, new BigDecimal("18"), false, null, null);
        String text = text(renderer.render(view(gst, null, null, "NearGFix Subscription Receipt (TEST MODE)")));
        assertTrue(text.contains("PAYMENT RECEIPT"));
        assertTrue(text.contains("TEST MODE"));
        assertTrue(text.contains("Prices include all charges."));
        assertTrue(text.contains("Discount"));
        assertFalse(text.contains("GST"));
        assertFalse(text.contains("Place of supply"));
    }
}
