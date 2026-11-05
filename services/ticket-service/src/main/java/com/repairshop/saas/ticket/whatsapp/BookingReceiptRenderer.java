package com.repairshop.saas.ticket.whatsapp;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The booking receipt image sent as the WhatsApp template's image header — the
 * same "Service Booking Confirmed" card the Partner App draws
 * (service-booking-shop/BookingConfirmCard.js): white background, navy text,
 * green accents, rounded cards, "Powered by NearGFix". Laid out at the app's
 * 360 pt width and drawn at 3x (1080 px); the height grows with the booking
 * and long names wrap instead of clipping. Java2D only — needs fonts on the
 * host (fontconfig + DejaVu, installed by install-ec2.sh). Shop logo and device
 * photos come from an injected loader and are optional.
 */
public final class BookingReceiptRenderer {

    private static final float W = 360f;
    private static final float PAD = 14f;
    private static final float SCALE = 3f;
    private static final float BAND_H = 39f;

    private static final Color GREEN = new Color(0x01AC77);
    private static final Color GREEN_DARK = new Color(0x03714F);
    private static final Color NAVY = new Color(0x05366E);
    private static final Color MINT = new Color(0xE6F7F1);
    private static final Color MUTED = new Color(0x6B7280);
    private static final Color HAIR = new Color(0xEEF1F0);
    private static final Color AMBER = new Color(0xB45309);
    private static final Color BODY = new Color(0x374151);
    private static final Color SOFT = new Color(0xF6FBF9);
    private static final Color WHITE = Color.WHITE;
    private static final Color WHITE_90 = new Color(255, 255, 255, 230);

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final BufferedImage BRAND_LOGO = loadBrandLogo();

    private final Function<String, BufferedImage> imageLoader;

    /** @param imageLoader shop logo / device photo by URL; returns null when unavailable (never throws). */
    public BookingReceiptRenderer(Function<String, BufferedImage> imageLoader) {
        this.imageLoader = imageLoader == null ? u -> null : imageLoader;
    }

    private record Ctx(BookingSnapshot b, ZoneId zone, BufferedImage shopLogo, List<BufferedImage> photos, String rupee) {}

    private record Run(String text, Font font, Color color) {}

    public byte[] renderPng(BookingSnapshot b, ZoneId zone) throws IOException {
        List<BufferedImage> photos = new ArrayList<>();
        for (BookingSnapshot.Device d : b.devices()) photos.add(load(d.imageUrl()));
        Ctx c = new Ctx(b, zone, load(b.shopLogoUrl()), photos, font(11, true).canDisplay('₹') ? "₹" : "Rs.");

        float h = draw(null, c);
        BufferedImage img = new BufferedImage(Math.round(W * SCALE), (int) Math.ceil(h * SCALE), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.scale(SCALE, SCALE);
            draw(g, c);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Draws when g != null; always returns the total height (pt). */
    private float draw(Graphics2D g, Ctx c) {
        float y = PAD;
        y += header(g, c, y) + 10;
        y += bookingCard(g, c, y) + 10;
        y += greeting(g, c, y);
        y += contacts(g, c, y);
        for (int i = 0; i < c.b().devices().size(); i++) {
            y += 10;
            y += deviceCard(g, c, i, y);
        }
        y += 10;
        y += paymentCard(g, c, y);
        y += 10;
        y += shopCard(g, c, y);
        y += 10;
        y += footer(g, c, y);
        return y + PAD;
    }

    // ── Sections ───────────────────────────────────────────────────────────────

    /** Shop logo + name · "Service Booking Confirmed" pill. */
    private float header(Graphics2D g, Ctx c, float y) {
        String shop = shopName(c);
        Font small = font(8, false), big = font(12, true), nf = font(15, true);
        float pillInner = Math.max(width("Service Booking", small), width("Confirmed", big));
        float pillW = 8 + 22 + 6 + pillInner + 8, pillH = 34;
        float nameX = PAD + 30 + 7, nameW = W - PAD - pillW - 8 - nameX;
        List<String> nameLines = wrap(shop, nf, nameW, 2);
        float nameH = nameLines.size() * 19;
        float rowH = Math.max(pillH, Math.max(30, nameH));

        logo(g, c.shopLogo(), shop, PAD, y + (rowH - 30) / 2, 30);
        float ny = y + (rowH - nameH) / 2;
        for (int i = 0; i < nameLines.size(); i++) text(g, nameLines.get(i), nf, NAVY, nameX, ny + i * 19 + 1, nameW);

        float px = W - PAD - pillW, py = y + (rowH - pillH) / 2;
        fill(g, rr(px, py, pillW, pillH, 12), MINT);
        float cx = px + 8 + 11, cy = py + pillH / 2;
        fill(g, circle(cx, cy, 11), GREEN);
        check(g, cx, cy, 5.5f, WHITE);
        text(g, "Service Booking", small, NAVY, px + 36, py + 6, pillInner + 2);
        text(g, "Confirmed", big, NAVY, px + 36, py + 15, pillInner + 2);
        return rowH;
    }

    /** Booking ID + booked-on · status pill. */
    private float bookingCard(Graphics2D g, Ctx c, float y) {
        float x = PAD, w = W - 2 * PAD;
        String booked = c.b().bookedAt() == null ? null : BookingTemplateVariables.when(c.b().bookedAt(), c.zone());
        float h = 7 + 10 + 15 + (booked != null ? 11 : 0) + 7;
        fill(g, rr(x, y, w, h, 12), SOFT);
        stroke(g, rr(x, y, w, h, 12), HAIR, 1);
        // Ticket glyph.
        stroke(g, rr(x + 10, y + h / 2 - 5.5f, 15, 11, 2.5f), GREEN, 1.6f);
        line(g, x + 17.5f, y + h / 2 - 3, x + 17.5f, y + h / 2 + 3, GREEN, 1.2f);

        String status = c.b().statusLabel();
        Font sf = font(9, true);
        float pw = width(status, sf) + 18, ph = 16;
        float px = x + w - 10 - pw, py = y + (h - ph) / 2;
        fill(g, rr(px, py, pw, ph, ph / 2), MINT);
        text(g, status, sf, GREEN_DARK, px + 9, py + 3, pw);

        float tx = x + 10 + 15 + 8, tw = px - 8 - tx;
        text(g, "Booking ID", font(8, false), MUTED, tx, y + 7, tw);
        text(g, BookingTemplateVariables.clean(c.b().bookingNumber(), "-"), font(12, true), NAVY, tx, y + 17, tw);
        if (booked != null) text(g, "Booked on " + booked, font(8.5f, false), MUTED, tx, y + 32, tw);
        return h;
    }

    /** "Dear <name>," + registered-with line. */
    private float greeting(Graphics2D g, Ctx c, float y) {
        float w = W - 2 * PAD;
        String name = BookingTemplateVariables.clean(c.b().customerName(), "Customer");
        float h = rich(g, List.of(
                new Run("Dear ", font(12, false), NAVY), new Run(name, font(12, true), NAVY), new Run(",", font(12, false), NAVY)),
                PAD, y, w, 15);
        h += 2;
        h += rich(g, List.of(
                new Run("Your device repair booking has been successfully registered with ", font(9.5f, false), BODY),
                new Run(shopName(c), font(9.5f, true), GREEN),
                new Run(".", font(9.5f, false), BODY)), PAD, y + h, w, 13);
        return h;
    }

    /** Mobile / Alt. Mobile chips (the image goes to the customer themself). */
    private float contacts(Graphics2D g, Ctx c, float y) {
        List<String[]> chips = new ArrayList<>();
        if (notBlank(c.b().customerPhone())) chips.add(new String[]{"Mobile", c.b().customerPhone().trim()});
        if (notBlank(c.b().customerAlternatePhone())) chips.add(new String[]{"Alt. Mobile", c.b().customerAlternatePhone().trim()});
        if (chips.isEmpty()) return 0;
        Font lf = font(8.5f, false), vf = font(9.5f, true);
        float rowH = 16, cx = PAD, top = y + 5 + 3;
        for (String[] chip : chips) {
            float cw = 7 + 9 + 4 + width(chip[0], lf) + 4 + width(chip[1], vf) + 7;
            if (cx + cw > W - PAD && cx > PAD) { cx = PAD; top += rowH + 3; }
            fill(g, rr(cx, top, cw, rowH, rowH / 2), SOFT);
            stroke(g, rr(cx, top, cw, rowH, rowH / 2), HAIR, 1);
            phoneGlyph(g, cx + 7 + 4.5f, top + rowH / 2, 3.6f, GREEN);
            float lx = cx + 7 + 9 + 4;
            text(g, chip[0], lf, MUTED, lx, top + 3.5f, cw);
            text(g, chip[1], vf, NAVY, lx + width(chip[0], lf) + 4, top + 2.6f, cw);
            cx += cw + 6;
        }
        return top + rowH - y;
    }

    /** One device: photo + name/variant/IMEI, repair services, details, delivery band. */
    private float deviceCard(Graphics2D g, Ctx c, int i, float y) {
        if (g == null) return deviceContent(null, c, i, y);
        float h = deviceContent(null, c, i, y);
        Shape card = rr(PAD, y, W - 2 * PAD, h, 12);
        Shape clip = g.getClip();
        g.clip(card);
        fill(g, new Rectangle2D.Float(PAD, y + h - BAND_H, W - 2 * PAD, BAND_H), MINT);
        g.setClip(clip);
        deviceContent(g, c, i, y);
        stroke(g, card, HAIR, 1);
        return h;
    }

    private float deviceContent(Graphics2D g, Ctx c, int i, float y) {
        BookingSnapshot.Device d = c.b().devices().get(i);
        int n = c.b().devices().size();
        float x = PAD, w = W - 2 * PAD, cy = y;
        if (n > 1) {
            text(g, "DEVICE " + (i + 1) + " OF " + n, font(8.5f, true), GREEN_DARK, x + 10, cy + 7, w - 20);
            cy += 7 + 11;
        }
        float rowTop = cy + (n > 1 ? 4 : 10);
        float px = x + 10;
        BufferedImage photo = c.photos().get(i);
        fill(g, rr(px, rowTop, 58, 70, 10), photo != null ? WHITE : MINT);
        stroke(g, rr(px, rowTop, 58, 70, 10), HAIR, 1);
        if (photo != null) drawContain(g, photo, px + 2, rowTop + 2, 54, 66);
        else deviceGlyph(g, px + 29, rowTop + 35, GREEN);

        BookingSnapshot.DeviceName dn = BookingSnapshot.DeviceName.parse(d.name());
        float tx = px + 58 + 10, tw = x + w - 10 - tx, ty = rowTop;
        text(g, "DEVICE", font(8, true), MUTED, tx, ty, tw);
        ty += 10;
        ty += para(g, dn.name() == null ? "Device" : dn.name(), font(13, true), NAVY, tx, ty + 1, tw, 16, 3) + 1;
        Font df = font(10, true);
        String variant = joinNonBlank(" · ", dn.ramStorage(), d.color());
        if (variant != null) {
            ty += 3;
            dot(g, tx + 3, ty + 6.5f, GREEN);
            ty += para(g, variant, df, BODY, tx + 11, ty, tw - 11, 13, 2);
        }
        if (dn.modelNumber() != null) {
            ty += 2;
            dot(g, tx + 3, ty + 6.5f, GREEN);
            ty += para(g, "Model No: " + dn.modelNumber(), df, BODY, tx + 11, ty, tw - 11, 13, 2);
        }
        String id = d.imei() != null ? "IMEI " + d.imei() : d.serialNumber() != null ? "S/N " + d.serialNumber() : null;
        if (id != null) {
            ty += 2;
            dot(g, tx + 3, ty + 6.5f, GREEN);
            ty += para(g, id, df, BODY, tx + 11, ty, tw - 11, 13, 1);
        }
        cy = rowTop + Math.max(70, ty - rowTop) + 10;

        // Repair services — every line; names wrap instead of truncating.
        line(g, x, cy, x + w, cy, HAIR, 1);
        float sy = cy + 8;
        sectionTitle(g, "REPAIR SERVICES", x + 10, sy);
        sy += 11 + 6;
        Font nf = font(11, true);
        List<BookingSnapshot.PriceItem> repairs = d.repairItems();
        if (repairs.isEmpty() && notBlank(d.servicesSummary())) {
            repairs = List.of(new BookingSnapshot.PriceItem(d.servicesSummary(), null, null, false));
        }
        for (int j = 0; j < repairs.size(); j++) {
            BookingSnapshot.PriceItem it = repairs.get(j);
            if (j > 0) sy += 6;
            String amt = it.amount() == null ? null : money(c, it.amount());
            float aw = amt == null ? 0 : width(amt, nf);
            fill(g, rr(x + 10, sy + 1, 16, 16, 5), MINT);
            centered(g, String.valueOf(j + 1), font(8.5f, true), GREEN_DARK, x + 18, sy + 3.5f);
            float nx = x + 10 + 16 + 7, nw = x + w - 10 - nx - (aw > 0 ? aw + 8 : 0);
            float ih = para(g, BookingTemplateVariables.clean(it.label(), "Service"), nf, NAVY, nx, sy, nw, 14, 4);
            if (amt != null) rightText(g, amt, nf, NAVY, x + w - 10, sy);
            if (notBlank(it.warranty())) {
                text(g, "Warranty: " + it.warranty().trim(), font(8.5f, false), MUTED, nx, sy + ih + 1, nw);
                ih += 12;
            }
            sy += ih;
        }
        if (d.serviceCharge().signum() > 0) {
            sy += 6;
            text(g, "Service Charge", font(10.5f, true), MUTED, x + 10 + 23, sy, 160);
            rightText(g, money(c, d.serviceCharge()), nf, NAVY, x + w - 10, sy);
            sy += 14;
        }
        if (notBlank(d.complaint())) sy += detail(g, "Issue Description", d.complaint(), x, w, sy);
        sy += detail(g, "Accessories", notBlank(d.accessories()) ? d.accessories() : "None", x, w, sy);
        sy += detail(g, "Missing / Damaged Parts", notBlank(d.missingParts()) ? d.missingParts() : "None", x, w, sy);
        sy += 8;

        // Estimated delivery band (mint background is filled by deviceCard).
        calendarGlyph(g, x + 10 + 8, sy + BAND_H / 2, GREEN_DARK);
        text(g, "ESTIMATED DELIVERY DATE & TIME", font(8, true), GREEN_DARK, x + 34, sy + 7, w - 44);
        text(g, d.deliveryAt() == null ? "To Be Confirmed" : BookingTemplateVariables.when(d.deliveryAt(), c.zone()),
                font(12, true), NAVY, x + 34, sy + 17, w - 44);
        return sy + BAND_H - y;
    }

    private float detail(Graphics2D g, String label, String value, float x, float w, float sy) {
        float top = sy + 7;
        dot(g, x + 10 + 4, top + 6, GREEN);
        float lh = para(g, label, font(9.5f, false), MUTED, x + 10 + 12, top, 88, 12, 2);
        float vx = x + 10 + 12 + 92;
        float vh = para(g, value.replaceAll("\\s+", " ").trim(), font(10, true), NAVY, vx, top, x + w - 10 - vx, 13, 6);
        return 7 + Math.max(lh, vh);
    }

    /** Estimated Repair Total · Amount Paid · Balance Amount. */
    private float paymentCard(Graphics2D g, Ctx c, float y) {
        float x = PAD, w = W - 2 * PAD;
        float h = 8 + 17 + 17 + 17 + 11 + 19 + 8;
        stroke(g, rr(x, y, w, h, 12), HAIR, 1);
        float ry = y + 8;
        sectionTitle(g, "PAYMENT SUMMARY", x + 10, ry);
        ry += 17;
        BigDecimal paid = c.b().paid();
        BigDecimal balance = c.b().balance();
        money(g, "Estimated Repair Total", money(c, c.b().total()), x, w, ry, false, NAVY);
        ry += 17;
        money(g, "Amount Paid", money(c, paid), x, w, ry, false, NAVY);
        ry += 17 + 5;
        line(g, x + 10, ry, x + w - 10, ry, HAIR, 1);
        ry += 6;
        money(g, "Balance Amount", money(c, balance), x, w, ry, true, balance.signum() > 0 ? AMBER : GREEN_DARK);
        return h;
    }

    private void money(Graphics2D g, String label, String value, float x, float w, float y, boolean bold, Color color) {
        text(g, label, font(bold ? 11 : 10.5f, true), bold ? NAVY : MUTED, x + 10, y + 3, w - 120);
        rightText(g, value, font(bold ? 13 : 11, true), color, x + w - 10, y + (bold ? 1.5f : 3));
    }

    /** Shop logo + name · phone. */
    private float shopCard(Graphics2D g, Ctx c, float y) {
        float x = PAD, w = W - 2 * PAD, h = 44;
        stroke(g, rr(x, y, w, h, 12), HAIR, 1);
        String shop = shopName(c);
        logo(g, c.shopLogo(), shop, x + 10, y + 8, 28);
        String phone = BookingTemplateVariables.clean(c.b().shopPhone(), "Contact the shop");
        Font pf = font(12, true);
        float groupW = 22 + 6 + width(phone, pf);
        float gx = x + w - 10 - groupW;
        fill(g, circle(gx + 11, y + h / 2, 11), GREEN);
        phoneGlyph(g, gx + 11, y + h / 2, 5, WHITE);
        text(g, phone, pf, NAVY, gx + 28, y + h / 2 - 7.5f, groupW);
        float tx = x + 10 + 28 + 8, tw = gx - 8 - tx;
        text(g, "Shop Contact", font(8, false), MUTED, tx, y + 10, tw);
        text(g, shop, font(11, true), NAVY, tx, y + 20, tw);
        return h;
    }

    /** Dark-green thank-you band · white "Powered by NearGFix" panel. */
    private float footer(Graphics2D g, Ctx c, float y) {
        float x = PAD, w = W - 2 * PAD, h = 46;
        Shape box = rr(x, y, w, h, 12);
        fill(g, box, GREEN_DARK);
        if (g != null) {
            Shape clip = g.getClip();
            g.clip(box);
            fill(g, topLeftRounded(x + w - 112, y, 112, h, 40), GREEN);
            fill(g, topLeftRounded(x + w - 106, y, 106, h, 40), WHITE);
            g.setClip(clip);
        }
        float lx = x + 12, lw = w - 12 - 8 - 112;
        String shop = shopName(c);
        Font tf = font(9.5f, false), tb = font(9.5f, true);
        String lead = "Thank you for choosing ";
        text(g, lead, tf, WHITE, lx, y + 11, lw);
        float sx = lx + width(lead, tf);
        String shopFit = ellipsize(shop, tb, lw - (sx - lx) - width("!", tb));
        text(g, shopFit + "!", tb, WHITE, sx, y + 11, lw);
        text(g, "Your trust means a lot to us.", font(8.5f, false), WHITE_90, lx, y + 25, lw);

        float pcx = x + w - 106 + 3 + 53;
        centered(g, "Powered by", font(7.5f, false), MUTED, pcx, y + 10);
        Font bf = font(12, true);
        float groupW = 16 + 3 + width("NearGFix", bf), gx = pcx - groupW / 2;
        if (g != null && BRAND_LOGO != null) drawContain(g, BRAND_LOGO, gx, y + 21, 16, 16);
        text(g, "NearGFix", bf, NAVY, gx + 19, y + 21.5f, groupW);
        return h;
    }

    // ── Drawing helpers ────────────────────────────────────────────────────────

    private String shopName(Ctx c) {
        return BookingTemplateVariables.clean(c.b().shopName(), "Your Shop");
    }

    private static String money(Ctx c, BigDecimal v) {
        return c.rupee() + BookingTemplateVariables.money(v);
    }

    private void sectionTitle(Graphics2D g, String title, float x, float y) {
        fill(g, rr(x, y + 1.5f, 9, 9, 2.5f), GREEN);
        text(g, title, font(8.5f, true), NAVY, x + 15, y + 1, 300);
    }

    private BufferedImage load(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            return imageLoader.apply(url);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BufferedImage loadBrandLogo() {
        try (InputStream in = BookingReceiptRenderer.class.getResourceAsStream("/whatsapp/neargfix-logo.png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static Font font(float size, boolean bold) {
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(size);
    }

    private static float width(String s, Font f) {
        return s == null || s.isEmpty() ? 0 : (float) f.getStringBounds(s, FRC).getWidth();
    }

    private static float ascent(Font f) {
        return f.getLineMetrics("Hg", FRC).getAscent();
    }

    /** One line with its top at y, ellipsized to maxW. */
    private static void text(Graphics2D g, String s, Font f, Color c, float x, float y, float maxW) {
        if (g == null || s == null || s.isEmpty()) return;
        g.setFont(f);
        g.setColor(c);
        g.drawString(ellipsize(s, f, maxW), x, y + ascent(f));
    }

    private static void rightText(Graphics2D g, String s, Font f, Color c, float right, float y) {
        text(g, s, f, c, right - width(s, f), y, Float.MAX_VALUE);
    }

    private static void centered(Graphics2D g, String s, Font f, Color c, float cx, float y) {
        text(g, s, f, c, cx - width(s, f) / 2, y, Float.MAX_VALUE);
    }

    /** Wrapped paragraph (at most maxLines, last one ellipsized). Returns its height. */
    private static float para(Graphics2D g, String s, Font f, Color c, float x, float y, float maxW, float lh, int maxLines) {
        List<String> lines = wrap(s, f, maxW, maxLines);
        for (int i = 0; i < lines.size(); i++) text(g, lines.get(i), f, c, x, y + i * lh, maxW);
        return lines.size() * lh;
    }

    static List<String> wrap(String s, Font f, float maxW, int maxLines) {
        List<String> lines = new ArrayList<>();
        String rest = s == null ? "" : s.trim();
        while (!rest.isEmpty() && lines.size() < maxLines) {
            if (width(rest, f) <= maxW || lines.size() == maxLines - 1) {
                lines.add(rest);
                break;
            }
            int cut = -1;
            for (int k = rest.indexOf(' '); k > 0; k = rest.indexOf(' ', k + 1)) {
                if (width(rest.substring(0, k), f) <= maxW) cut = k; else break;
            }
            if (cut <= 0) { // one long word: break by characters
                cut = 1;
                while (cut < rest.length() && width(rest.substring(0, cut + 1), f) <= maxW) cut++;
            }
            lines.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        return lines.isEmpty() ? List.of("-") : lines;
    }

    /** Mixed-style text (e.g. a bold shop name inside a sentence), wrapped word by word. Returns its height. */
    private static float rich(Graphics2D g, List<Run> runs, float x, float y, float maxW, float lh) {
        float cx = x, cy = y;
        for (Run r : runs) {
            String[] words = r.text().split("(?<= )");
            for (String word : words) {
                float ww = width(word, r.font());
                if (cx > x && cx + width(word.stripTrailing(), r.font()) > x + maxW) {
                    cx = x;
                    cy += lh;
                }
                text(g, word, r.font(), r.color(), cx, cy, Float.MAX_VALUE);
                cx += ww;
            }
        }
        return cy - y + lh;
    }

    private static String ellipsize(String s, Font f, float maxW) {
        if (width(s, f) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && width(t + "…", f) > maxW) t = t.substring(0, t.length() - 1);
        return t.trim() + "…";
    }

    private static RoundRectangle2D rr(float x, float y, float w, float h, float r) {
        return new RoundRectangle2D.Float(x, y, w, h, 2 * r, 2 * r);
    }

    private static Ellipse2D circle(float cx, float cy, float r) {
        return new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r);
    }

    private static Shape topLeftRounded(float x, float y, float w, float h, float r) {
        float rad = Math.min(r, h);
        Path2D p = new Path2D.Float();
        p.moveTo(x, y + h);
        p.lineTo(x, y + rad);
        p.quadTo(x, y, x + rad, y);
        p.lineTo(x + w, y);
        p.lineTo(x + w, y + h);
        p.closePath();
        return p;
    }

    private static void fill(Graphics2D g, Shape s, Color c) {
        if (g == null) return;
        g.setColor(c);
        g.fill(s);
    }

    private static void stroke(Graphics2D g, Shape s, Color c, float width) {
        if (g == null) return;
        g.setColor(c);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
    }

    private static void line(Graphics2D g, float x1, float y1, float x2, float y2, Color c, float width) {
        stroke(g, new Line2D.Float(x1, y1, x2, y2), c, width);
    }

    private static void dot(Graphics2D g, float cx, float cy, Color c) {
        fill(g, circle(cx, cy, 2.4f), c);
    }

    private static void check(Graphics2D g, float cx, float cy, float s, Color c) {
        Path2D p = new Path2D.Float();
        p.moveTo(cx - s * 0.6f, cy + s * 0.05f);
        p.lineTo(cx - s * 0.15f, cy + s * 0.5f);
        p.lineTo(cx + s * 0.65f, cy - s * 0.45f);
        stroke(g, p, c, 2f);
    }

    /** Small phone outline with its home bar, so it never reads as a zero. */
    private static void phoneGlyph(Graphics2D g, float cx, float cy, float s, Color c) {
        float sw = Math.max(0.9f, s * 0.26f);
        stroke(g, rr(cx - s * 0.62f, cy - s, s * 1.24f, s * 2, s * 0.32f), c, sw);
        line(g, cx - s * 0.22f, cy + s * 0.62f, cx + s * 0.22f, cy + s * 0.62f, c, sw);
    }

    /** Phone placeholder for a device without a catalogue photo. */
    private static void deviceGlyph(Graphics2D g, float cx, float cy, Color c) {
        stroke(g, rr(cx - 8, cy - 13, 16, 26, 3), c, 1.5f);
        line(g, cx - 2.5f, cy + 9.5f, cx + 2.5f, cy + 9.5f, c, 1.5f);
    }

    private static void calendarGlyph(Graphics2D g, float cx, float cy, Color c) {
        stroke(g, rr(cx - 7, cy - 6, 14, 13, 2.5f), c, 1.6f);
        line(g, cx - 7, cy - 2, cx + 7, cy - 2, c, 1.4f);
        line(g, cx - 3.5f, cy - 8, cx - 3.5f, cy - 4.5f, c, 1.4f);
        line(g, cx + 3.5f, cy - 8, cx + 3.5f, cy - 4.5f, c, 1.4f);
    }

    /** Shop logo in a circle, else a green circle with the shop's initial. */
    private static void logo(Graphics2D g, BufferedImage img, String name, float x, float y, float size) {
        if (g == null) return;
        if (img != null) {
            Shape clip = g.getClip();
            g.clip(circle(x + size / 2, y + size / 2, size / 2));
            double s = Math.max(size / img.getWidth(), size / img.getHeight());
            double iw = img.getWidth() * s, ih = img.getHeight() * s;
            AffineTransform at = new AffineTransform();
            at.translate(x + (size - iw) / 2, y + (size - ih) / 2);
            at.scale(s, s);
            g.drawImage(img, at, null);
            g.setClip(clip);
            return;
        }
        fill(g, circle(x + size / 2, y + size / 2, size / 2), GREEN);
        String initial = name == null || name.isBlank() ? "S" : name.trim().substring(0, 1).toUpperCase();
        Font f = font(size * 0.46f, true);
        centered(g, initial, f, WHITE, x + size / 2, y + (size - ascent(f)) / 2 - 0.5f);
    }

    private static void drawContain(Graphics2D g, BufferedImage img, float x, float y, float w, float h) {
        if (g == null || img == null) return;
        double s = Math.min(w / img.getWidth(), h / img.getHeight());
        double iw = img.getWidth() * s, ih = img.getHeight() * s;
        AffineTransform at = new AffineTransform();
        at.translate(x + (w - iw) / 2, y + (h - ih) / 2);
        at.scale(s, s);
        g.drawImage(img, at, null);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String joinNonBlank(String sep, String... parts) {
        List<String> out = new ArrayList<>();
        for (String p : parts) if (notBlank(p)) out.add(p.trim());
        return out.isEmpty() ? null : String.join(sep, out);
    }
}
