package com.repairshop.saas.subscription.invoice;

import com.repairshop.saas.subscription.billing.SubscriptionTerms;
import com.repairshop.saas.subscription.dto.v2.V2Views.GstBreakdown;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceBillTo;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoicePayment;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceSubscription;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceSupplier;
import com.repairshop.saas.subscription.dto.v2.V2Views.InvoiceView;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A4 invoice / receipt PDF (PDFBox 3): white page, NearGFix green #01AC77 and
 * navy #05366E, logo, From / Bill To, subscription table, GST table (only when
 * GST applies), big Total Paid, payment references, footer declaration.
 * DejaVu Sans is embedded (subset) for the ₹ glyph; if it cannot be loaded the
 * page falls back to Helvetica and "Rs.". Characters a font cannot draw become "?".
 */
@Slf4j
@Component
public class InvoicePdfRenderer {

    private static final float[] GREEN = rgb(0x01, 0xAC, 0x77);
    private static final float[] NAVY = rgb(0x05, 0x36, 0x6E);
    private static final float[] INK = rgb(0x1F, 0x29, 0x37);
    private static final float[] MUTED = rgb(0x6B, 0x72, 0x80);
    private static final float[] RULE = rgb(0xDD, 0xE1, 0xE6);
    private static final float[] TINT = rgb(0xE8, 0xF7, 0xF1);
    private static final float[] WHITE = rgb(0xFF, 0xFF, 0xFF);
    private static final float[] RED = rgb(0xC6, 0x28, 0x28);

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(SubscriptionTerms.IST);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a 'IST'", Locale.ENGLISH).withZone(SubscriptionTerms.IST);

    private static final byte[] REGULAR_TTF = resource("invoice/DejaVuSans.ttf");
    private static final byte[] BOLD_TTF = resource("invoice/DejaVuSans-Bold.ttf");
    private static final byte[] LOGO_PNG = resource("invoice/neargfix-logo.png");

    public byte[] render(InvoiceView v) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            Fonts f = fonts(doc);

            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle(f.safe(f.regular, nz(v.title()) + " " + nz(v.invoiceNo())));
            info.setSubject(f.safe(f.regular, nz(v.invoiceNo())));
            info.setAuthor("NearGFix");
            info.setCreator("NearGFix subscription-service");

            float w = page.getMediaBox().getWidth();
            float h = page.getMediaBox().getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                Canvas c = new Canvas(cs, f);
                draw(doc, c, v, w, h);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── layout ──────────────────────────────────────────────────────────────

    private void draw(PDDocument doc, Canvas c, InvoiceView v, float w, float h) throws IOException {
        Fonts f = c.f;
        float left = 40;
        float right = w - 40;
        GstBreakdown gst = v.gst();
        boolean taxInvoice = gst != null && gst.applicable();
        boolean test = v.title() != null && v.title().contains("TEST MODE");

        c.rect(0, h - 8, w, 8, GREEN);

        // Header: logo + brand (left), document label + number + date (right).
        float top = h - 40;
        float brandX = left;
        PDImageXObject logo = logo(doc);
        if (logo != null) {
            c.cs.drawImage(logo, left, top - 46, 46, 46);
            brandX = left + 56;
        }
        c.text(brandX, top - 20, f.bold, 20, NAVY, "NearGFix");
        c.text(brandX, top - 36, f.regular, 9, MUTED, "Repair shop subscription");
        c.textRight(right, top - 18, f.bold, 16, GREEN, taxInvoice ? "TAX INVOICE" : "PAYMENT RECEIPT");
        c.textRight(right, top - 34, f.regular, 9.5f, INK, (taxInvoice ? "Invoice No: " : "Receipt No: ") + nz(v.invoiceNo()));
        c.textRight(right, top - 48, f.regular, 9.5f, INK, "Date: " + date(v.issuedAt()));
        if (test) c.textRight(right, top - 62, f.bold, 9, RED, "TEST MODE - not a real payment");

        float y = top - 74;
        c.line(left, y, right, y, RULE, 0.8f);
        y -= 18;

        // From / Bill To.
        float gap = 30;
        float colW = (right - left - gap) / 2;
        float yl = block(c, left, y, colW, "FROM", supplierLines(v.supplier()));
        float yr = block(c, left + colW + gap, y, colW, "BILL TO", billToLines(v.billTo()));
        y = Math.min(yl, yr) - 14;

        // Subscription table.
        y = subscriptionTable(c, v.subscription(), gst, left, right, y);

        // GST table — only when GST applies.
        if (taxInvoice) y = gstTable(c, gst, left, right, y - 16);

        // Payment details (left) + totals with the big Total Paid (right).
        y -= 22;
        float boxW = 235;
        float boxX = right - boxW;
        paymentDetails(c, v, gst, left, boxX - 20, y);
        totals(c, gst, boxX, right, y);

        // Footer.
        c.line(left, 74, right, 74, RULE, 0.8f);
        c.textCenter(w / 2, 60, f.regular, 8.5f, MUTED, "This is a computer-generated document.");
        InvoiceSupplier s = v.supplier();
        List<String> contact = new ArrayList<>();
        contact.add("NearGFix");
        if (s != null && notBlank(s.name())) contact.add(s.name());
        if (s != null && notBlank(s.email())) contact.add(s.email());
        if (s != null && notBlank(s.phone())) contact.add(s.phone());
        c.textCenter(w / 2, 47, f.regular, 8, MUTED, String.join("  ·  ", contact));
        c.rect(0, 0, w, 4, GREEN);
    }

    private record TextLine(String text, boolean bold, float size, float[] color) {}

    private static List<TextLine> supplierLines(InvoiceSupplier s) {
        List<TextLine> out = new ArrayList<>();
        if (s == null) return out;
        out.add(new TextLine(notBlank(s.name()) ? s.name() : "NearGFix", true, 10.5f, INK));
        if (notBlank(s.address())) out.add(new TextLine(s.address(), false, 9, INK));
        String state = joinState(s.state(), s.stateCode());
        if (state != null) out.add(new TextLine("State: " + state, false, 9, INK));
        if (notBlank(s.gstin())) out.add(new TextLine("GSTIN: " + s.gstin(), true, 9, INK));
        if (notBlank(s.email())) out.add(new TextLine("Email: " + s.email(), false, 9, MUTED));
        if (notBlank(s.phone())) out.add(new TextLine("Phone: " + s.phone(), false, 9, MUTED));
        return out;
    }

    private static List<TextLine> billToLines(InvoiceBillTo b) {
        List<TextLine> out = new ArrayList<>();
        if (b == null) return out;
        if (notBlank(b.shopName())) out.add(new TextLine(b.shopName(), true, 10.5f, INK));
        if (notBlank(b.ownerName())) out.add(new TextLine(b.ownerName(), !notBlank(b.shopName()), 9.5f, INK));
        if (notBlank(b.mobile())) out.add(new TextLine("Mobile: " + b.mobile(), false, 9, INK));
        if (notBlank(b.address())) out.add(new TextLine(b.address(), false, 9, INK));
        String region = String.join(" - ", nonBlank(b.state(), b.pincode()));
        if (!region.isEmpty()) out.add(new TextLine(region, false, 9, INK));
        if (notBlank(b.gstin())) out.add(new TextLine("GSTIN: " + b.gstin(), true, 9, INK));
        return out;
    }

    /** Label + wrapped lines; returns the y below the block. */
    private static float block(Canvas c, float x, float y, float width, String label, List<TextLine> lines)
            throws IOException {
        c.text(x, y, c.f.bold, 8.5f, GREEN, label);
        y -= 15;
        for (TextLine l : lines) {
            PDFont font = l.bold() ? c.f.bold : c.f.regular;
            List<String> wrapped = c.wrap(l.text(), font, l.size(), width);
            for (int i = 0; i < wrapped.size() && i < 3; i++) {
                c.text(x, y, font, l.size(), l.color(), wrapped.get(i));
                y -= l.size() + 4;
            }
        }
        return y;
    }

    private float subscriptionTable(Canvas c, InvoiceSubscription sub, GstBreakdown gst, float left, float right,
                                    float y) throws IOException {
        Fonts f = c.f;
        float descX = left + 8;
        float descW = 262;
        float shopsCenter = left + 300;
        float periodX = left + 330;
        float amountRight = right - 8;

        c.rect(left, y - 20, right - left, 20, NAVY);
        float hy = y - 13.5f;
        c.text(descX, hy, f.bold, 9, WHITE, "Description");
        c.textCenter(shopsCenter, hy, f.bold, 9, WHITE, "Shops");
        c.text(periodX, hy, f.bold, 9, WHITE, "Period");
        c.textRight(amountRight, hy, f.bold, 9, WHITE, "Amount");
        y -= 20;

        long gross = gst == null ? 0 : gst.grossPaise();
        long discount = gst == null ? 0 : gst.discountPaise();
        String plan = sub == null ? "Subscription" : String.join(" · ", nonBlank(sub.planName(), sub.durationLabel()));
        if (plan.isEmpty()) plan = "Subscription";
        float rowTop = y - 15;
        c.text(descX, rowTop, f.bold, 10, INK, "NearGFix " + plan + " subscription");
        float descY = rowTop - 13;
        if (sub != null && sub.shopNames() != null && !sub.shopNames().isEmpty()) {
            List<String> lines = c.wrap("Shops: " + String.join(", ", sub.shopNames()), f.regular, 8.5f, descW);
            for (int i = 0; i < lines.size() && i < 4; i++) {
                String text = i == 3 && lines.size() > 4 ? lines.get(i) + " …" : lines.get(i);
                c.text(descX, descY, f.regular, 8.5f, MUTED, text);
                descY -= 11.5f;
            }
        }
        c.textCenter(shopsCenter, rowTop, f.regular, 10, INK, sub == null ? "-" : String.valueOf(sub.shopCount()));
        if (sub != null && (sub.startsAt() != null || sub.endsAt() != null)) {
            c.text(periodX, rowTop, f.regular, 9, INK, date(sub.startsAt()));
            c.text(periodX, rowTop - 12, f.regular, 9, INK, "to " + date(sub.endsAt()));
        }
        c.textRight(amountRight, rowTop, f.regular, 10, INK, c.money(gross));
        y = Math.min(descY, rowTop - 24) - 4;

        if (discount > 0) {
            c.line(left, y, right, y, RULE, 0.6f);
            float dy = y - 14;
            c.text(descX, dy, f.regular, 9.5f, INK, "Discount (additional shops)");
            c.textRight(amountRight, dy, f.regular, 10, INK, "- " + c.money(discount));
            y = dy - 8;
        }
        c.line(left, y, right, y, NAVY, 0.8f);
        return y;
    }

    private float gstTable(Canvas c, GstBreakdown g, float left, float right, float y) throws IOException {
        Fonts f = c.f;
        c.text(left, y, f.bold, 8.5f, GREEN, "GST SUMMARY");
        y -= 8;
        List<String> heads = new ArrayList<>();
        List<String> values = new ArrayList<>();
        heads.add("Taxable value");
        values.add(c.money(g.taxablePaise()));
        if (g.interState()) {
            heads.add("IGST @ " + pct(g.igstPercent()) + "%");
            values.add(c.money(g.igstPaise()));
        } else {
            heads.add("CGST @ " + pct(g.cgstPercent()) + "%");
            values.add(c.money(g.cgstPaise()));
            heads.add("SGST @ " + pct(g.sgstPercent()) + "%");
            values.add(c.money(g.sgstPaise()));
        }
        heads.add("Total tax");
        values.add(c.money(g.taxPaise()));

        float colW = (right - left) / heads.size();
        c.rect(left, y - 18, right - left, 18, TINT);
        for (int i = 0; i < heads.size(); i++) {
            c.textCenter(left + colW * i + colW / 2, y - 12.5f, f.bold, 8.5f, NAVY, heads.get(i));
        }
        y -= 18;
        for (int i = 0; i < values.size(); i++) {
            c.textCenter(left + colW * i + colW / 2, y - 14, f.regular, 9.5f, INK, values.get(i));
        }
        y -= 22;
        c.line(left, y, right, y, RULE, 0.6f);
        return y;
    }

    private float paymentDetails(Canvas c, InvoiceView v, GstBreakdown gst, float x, float maxX, float y)
            throws IOException {
        Fonts f = c.f;
        InvoicePayment p = v.payment();
        c.text(x, y, f.bold, 8.5f, GREEN, "PAYMENT DETAILS");
        y -= 15;
        List<String[]> rows = new ArrayList<>();
        if (p != null) {
            if (notBlank(p.orderNo())) rows.add(new String[]{"Order No", p.orderNo()});
            if (notBlank(p.razorpayOrderId())) rows.add(new String[]{"Razorpay Order ID", p.razorpayOrderId()});
            if (notBlank(p.razorpayPaymentId())) rows.add(new String[]{"Payment ID", p.razorpayPaymentId()});
            if (notBlank(p.method())) rows.add(new String[]{"Method", p.method().toUpperCase(Locale.ROOT)});
            if (notBlank(p.status())) rows.add(new String[]{"Status", p.status()});
            if (p.paidAt() != null) rows.add(new String[]{"Paid on", DATE_TIME.format(p.paidAt())});
        }
        InvoiceSupplier s = v.supplier();
        if (s != null && notBlank(s.sac())) rows.add(new String[]{"SAC", s.sac()});
        if (gst != null && gst.applicable() && notBlank(gst.placeOfSupply())) {
            rows.add(new String[]{"Place of supply", gst.placeOfSupply()});
        }
        float valueX = x + 100;
        for (String[] r : rows) {
            c.text(x, y, f.regular, 9, MUTED, r[0]);
            List<String> lines = c.wrap(r[1], f.regular, 9, Math.max(60, maxX - valueX));
            for (int i = 0; i < lines.size() && i < 2; i++) {
                c.text(valueX, y, f.regular, 9, INK, lines.get(i));
                if (i < lines.size() - 1 && i < 1) y -= 12;
            }
            y -= 14;
        }
        return y;
    }

    private float totals(Canvas c, GstBreakdown g, float x, float right, float y) throws IOException {
        Fonts f = c.f;
        float labelX = x + 10;
        float valueRight = right - 10;
        List<String[]> rows = new ArrayList<>();
        if (g != null) {
            rows.add(new String[]{"Plan price", c.money(g.grossPaise())});
            if (g.discountPaise() > 0) rows.add(new String[]{"Discount", "- " + c.money(g.discountPaise())});
            if (g.applicable()) {
                rows.add(new String[]{"Taxable amount", c.money(g.taxablePaise())});
                if (g.interState()) {
                    rows.add(new String[]{"IGST @ " + pct(g.igstPercent()) + "%", c.money(g.igstPaise())});
                } else {
                    rows.add(new String[]{"CGST @ " + pct(g.cgstPercent()) + "%", c.money(g.cgstPaise())});
                    rows.add(new String[]{"SGST @ " + pct(g.sgstPercent()) + "%", c.money(g.sgstPaise())});
                }
            }
        }
        float rowsTop = y + 6;
        c.rect(x, rowsTop - rows.size() * 16 - 6, right - x, rows.size() * 16 + 6, rgb(0xF7, 0xF9, 0xFA));
        y -= 8;
        for (String[] r : rows) {
            c.text(labelX, y, f.regular, 9.5f, INK, r[0]);
            c.textRight(valueRight, y, f.regular, 9.5f, INK, r[1]);
            y -= 16;
        }
        float barH = 34;
        float barTop = y + 4;
        c.rect(x, barTop - barH, right - x, barH, GREEN);
        c.text(labelX, barTop - 21, f.bold, 11, WHITE, "Total Paid");
        c.textRight(valueRight, barTop - 23, f.bold, 17, WHITE, c.money(g == null ? 0 : g.totalPaise()));
        y = barTop - barH - 14;
        if (g == null || !g.applicable()) {
            c.textRight(right, y, f.regular, 8.5f, MUTED, "Prices include all charges.");
            y -= 12;
        } else {
            c.textRight(right, y, f.regular, 8.5f, MUTED, "Amount includes GST of " + c.money(g.taxPaise()) + ".");
            y -= 12;
        }
        return y;
    }

    // ── fonts / resources ───────────────────────────────────────────────────

    private static final class Fonts {
        final PDFont regular;
        final PDFont bold;
        final String rupee;
        private final Map<PDFont, Map<Integer, Boolean>> drawable = new HashMap<>();

        Fonts(PDFont regular, PDFont bold, String rupee) {
            this.regular = regular;
            this.bold = bold;
            this.rupee = rupee;
        }

        /** Replace what the font cannot draw (Tamil, emoji, control chars…) with "?". */
        String safe(PDFont font, String s) {
            if (s == null) return "";
            Map<Integer, Boolean> cache = drawable.computeIfAbsent(font, k -> new HashMap<>());
            StringBuilder sb = new StringBuilder(s.length());
            s.codePoints().forEach(cp -> {
                if (Character.isISOControl(cp) || Character.isWhitespace(cp)) {
                    sb.append(' ');
                    return;
                }
                boolean ok = cache.computeIfAbsent(cp, k -> canEncode(font, k));
                if (ok) sb.appendCodePoint(cp); else sb.append('?');
            });
            return sb.toString();
        }

        private static boolean canEncode(PDFont font, int cp) {
            try {
                font.encode(new String(Character.toChars(cp)));
                return true;
            } catch (IOException | IllegalArgumentException e) {
                return false;
            }
        }
    }

    private static Fonts fonts(PDDocument doc) {
        if (REGULAR_TTF != null && BOLD_TTF != null) {
            try {
                PDFont regular = PDType0Font.load(doc, new ByteArrayInputStream(REGULAR_TTF), true);
                PDFont bold = PDType0Font.load(doc, new ByteArrayInputStream(BOLD_TTF), true);
                boolean rupeeOk = Fonts.canEncode(regular, 0x20B9) && Fonts.canEncode(bold, 0x20B9);
                return new Fonts(regular, bold, rupeeOk ? "₹" : "Rs. ");
            } catch (IOException | RuntimeException e) {
                log.warn("Invoice font could not be loaded, falling back to Helvetica: {}", e.toString());
            }
        }
        return new Fonts(new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), "Rs. ");
    }

    private static PDImageXObject logo(PDDocument doc) {
        if (LOGO_PNG == null) return null;
        try {
            return PDImageXObject.createFromByteArray(doc, LOGO_PNG, "neargfix-logo.png");
        } catch (IOException | RuntimeException e) {
            log.warn("Invoice logo could not be loaded: {}", e.toString());
            return null;
        }
    }

    private static byte[] resource(String path) {
        try (InputStream in = InvoicePdfRenderer.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    // ── drawing primitives ──────────────────────────────────────────────────

    private static final class Canvas {
        final PDPageContentStream cs;
        final Fonts f;

        Canvas(PDPageContentStream cs, Fonts f) {
            this.cs = cs;
            this.f = f;
        }

        void text(float x, float y, PDFont font, float size, float[] color, String s) throws IOException {
            String t = f.safe(font, s);
            if (t.isEmpty()) return;
            cs.beginText();
            cs.setFont(font, size);
            cs.setNonStrokingColor(color[0], color[1], color[2]);
            cs.newLineAtOffset(x, y);
            cs.showText(t);
            cs.endText();
        }

        void textRight(float right, float y, PDFont font, float size, float[] color, String s) throws IOException {
            text(right - width(font, size, s), y, font, size, color, s);
        }

        void textCenter(float center, float y, PDFont font, float size, float[] color, String s) throws IOException {
            text(center - width(font, size, s) / 2, y, font, size, color, s);
        }

        float width(PDFont font, float size, String s) throws IOException {
            return font.getStringWidth(f.safe(font, s)) / 1000f * size;
        }

        void rect(float x, float y, float w, float h, float[] color) throws IOException {
            cs.setNonStrokingColor(color[0], color[1], color[2]);
            cs.addRect(x, y, w, h);
            cs.fill();
        }

        void line(float x1, float y1, float x2, float y2, float[] color, float width) throws IOException {
            cs.setStrokingColor(color[0], color[1], color[2]);
            cs.setLineWidth(width);
            cs.moveTo(x1, y1);
            cs.lineTo(x2, y2);
            cs.stroke();
        }

        String money(long paise) {
            return SubscriptionTerms.rupees(paise).replace("₹", f.rupee);
        }

        /** Greedy word wrap; a single over-long word is hard-split. */
        List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
            List<String> out = new ArrayList<>();
            if (text == null || text.isBlank()) return out;
            StringBuilder line = new StringBuilder();
            for (String word : f.safe(font, text).trim().split(" +")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (width(font, size, candidate) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) out.add(line.toString());
                line.setLength(0);
                String rest = word;
                while (width(font, size, rest) > maxWidth && rest.length() > 1) {
                    int cut = rest.length() - 1;
                    while (cut > 1 && width(font, size, rest.substring(0, cut)) > maxWidth) cut--;
                    out.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                line.append(rest);
            }
            if (!line.isEmpty()) out.add(line.toString());
            return out;
        }
    }

    // ── formatting ──────────────────────────────────────────────────────────

    private static String date(Instant at) {
        return at == null ? "-" : DATE.format(at);
    }

    private static String pct(BigDecimal p) {
        return p == null ? "0" : p.stripTrailingZeros().toPlainString();
    }

    private static String joinState(String state, String code) {
        if (notBlank(state) && notBlank(code)) return state + " (" + code + ")";
        if (notBlank(state)) return state;
        return notBlank(code) ? "Code " + code : null;
    }

    private static List<String> nonBlank(String... parts) {
        List<String> out = new ArrayList<>();
        for (String p : parts) if (notBlank(p)) out.add(p.trim());
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255f, g / 255f, b / 255f};
    }
}
