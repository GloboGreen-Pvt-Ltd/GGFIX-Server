package com.repairshop.saas.ticket.whatsapp;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Booking receipt PNG for the template's image header: white card, navy header,
 * green accents, "Powered by NearGFix". Drawn with Java2D only (no extra
 * dependencies). Needs fonts on the host (fontconfig + DejaVu on Amazon Linux);
 * device photos are optional and come from an injected loader.
 */
public final class BookingReceiptRenderer {

    private static final int W = 1080;
    private static final int PAD = 56;
    private static final Color NAVY = new Color(0x1C3053);
    private static final Color GREEN = new Color(0x609138);
    private static final Color INK = new Color(0x1F2937);
    private static final Color MUTED = new Color(0x6B7280);
    private static final Color LINE = new Color(0xE5E7EB);
    private static final Color SOFT = new Color(0xF4F7FA);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final Function<String, BufferedImage> imageLoader;

    /** @param imageLoader device photo by URL; returns null when unavailable (never throws). */
    public BookingReceiptRenderer(Function<String, BufferedImage> imageLoader) {
        this.imageLoader = imageLoader == null ? u -> null : imageLoader;
    }

    public byte[] renderPng(BookingSnapshot b, ZoneId zone) throws IOException {
        // Two passes: measure (no drawing) then draw onto an exact-height canvas.
        int height = draw(null, b, zone);
        BufferedImage img = new BufferedImage(W, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, height);
            draw(g, b, zone);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Draws when g != null; always returns the total height. */
    private int draw(Graphics2D g, BookingSnapshot b, ZoneId zone) {
        Graphics2D m = g != null ? g : measure();
        String rupee = font(28, false).canDisplay('₹') ? "₹" : "Rs.";
        int y;

        // Header band
        int headerH = 236;
        if (g != null) { g.setColor(NAVY); g.fillRect(0, 0, W, headerH); }
        y = 92;
        text(g, m, BookingTemplateVariables.clean(b.shopName(), "NearGFix Partner"), PAD, y, font(46, true), Color.WHITE, W - PAD * 2);
        y += 52;
        if (g != null) {
            g.setColor(GREEN);
            g.fill(new RoundRectangle2D.Float(PAD, y - 4, 404, 50, 50, 50));
        }
        text(g, m, "SERVICE BOOKING CONFIRMED", PAD + 24, y + 31, font(22, true), Color.WHITE, 380);
        y = headerH + 64;

        // Booking id + date
        text(g, m, "Booking ID", PAD, y, font(24, false), MUTED, 400);
        rightText(g, m, "Date", W - PAD, y, font(24, false), MUTED);
        y += 44;
        text(g, m, BookingTemplateVariables.clean(b.bookingNumber(), "-"), PAD, y, font(36, true), NAVY, 560);
        rightText(g, m, b.bookedAt() == null ? "-" : DAY.format(b.bookedAt().atZone(zone)), W - PAD, y, font(30, true), INK);
        y += 40;
        y = rule(g, y);

        // Customer (phone masked: the image travels through third parties)
        y = label(g, m, "CUSTOMER", y);
        text(g, m, BookingTemplateVariables.clean(b.customerName(), "Customer"), PAD, y, font(30, true), INK, W - PAD * 2);
        y += 40;
        text(g, m, PhoneNumbers.mask(PhoneNumbers.toE164(b.customerPhone(), "91")), PAD, y, font(26, false), MUTED, W - PAD * 2);
        y += 28;
        y = rule(g, y);

        // Devices
        for (BookingSnapshot.Device d : b.devices()) {
            y = label(g, m, b.devices().size() > 1 ? "DEVICE · " + nz(d.trackingId()) : "DEVICE", y);
            boolean hasPhoto = d.imageUrl() != null && !d.imageUrl().isBlank();
            int photoSize = 132;
            int textX = hasPhoto ? PAD + photoSize + 28 : PAD;
            if (g != null && hasPhoto) {
                g.setColor(SOFT);
                g.fill(new RoundRectangle2D.Float(PAD, y - 34, photoSize, photoSize, 24, 24));
                BufferedImage photo = imageLoader.apply(d.imageUrl());
                if (photo != null) {
                    double s = Math.min((photoSize - 16.0) / photo.getWidth(), (photoSize - 16.0) / photo.getHeight());
                    int pw = (int) (photo.getWidth() * s), ph = (int) (photo.getHeight() * s);
                    g.drawImage(photo, PAD + (photoSize - pw) / 2, y - 34 + (photoSize - ph) / 2, pw, ph, null);
                }
            }
            int top = y;
            for (String line : wrap(m, nz(d.name()), font(30, true), W - PAD - textX)) {
                text(g, m, line, textX, y, font(30, true), INK, W - PAD - textX);
                y += 40;
            }
            if (d.accessories() != null && !d.accessories().isBlank()) {
                for (String line : wrap(m, "Accessories: " + d.accessories().trim(), font(24, false), W - PAD - textX)) {
                    text(g, m, line, textX, y, font(24, false), MUTED, W - PAD - textX);
                    y += 34;
                }
            }
            if (hasPhoto) y = Math.max(y, top + photoSize - 10);
            y += 14;

            List<BookingSnapshot.PriceItem> items = d.items() == null || d.items().isEmpty()
                    ? List.of(new BookingSnapshot.PriceItem(nz(BookingTemplateVariables.services(d)), d.estimate(), null))
                    : d.items();
            for (BookingSnapshot.PriceItem it : items) {
                text(g, m, nz(it.label()), PAD, y, font(27, false), INK, W - PAD * 2 - 220);
                if (it.amount() != null) rightText(g, m, rupee + BookingTemplateVariables.money(it.amount()), W - PAD, y, font(27, true), INK);
                y += 34;
                if (it.warranty() != null && !it.warranty().isBlank()) {
                    text(g, m, "Warranty: " + it.warranty().trim(), PAD, y, font(22, false), GREEN, W - PAD * 2);
                    y += 32;
                }
                y += 6;
            }
            y = rule(g, y);
        }

        // Delivery + totals
        y = label(g, m, "ESTIMATED DELIVERY", y);
        text(g, m, BookingTemplateVariables.when(b.latestDelivery(), zone), PAD, y, font(30, true), NAVY, W - PAD * 2);
        y += 44;

        int boxTop = y;
        int boxH = 3 * 52 + 36;
        if (g != null) {
            g.setColor(SOFT);
            g.fill(new RoundRectangle2D.Float(PAD, boxTop, W - PAD * 2, boxH, 28, 28));
        }
        y = boxTop + 60;
        for (Map.Entry<String, BigDecimal> row : List.of(
                Map.entry("Repair Total", b.total()), Map.entry("Paid", b.paid()), Map.entry("Balance", b.balance()))) {
            boolean balance = "Balance".equals(row.getKey());
            text(g, m, row.getKey(), PAD + 32, y, font(28, balance), balance ? NAVY : MUTED, 400);
            rightText(g, m, rupee + BookingTemplateVariables.money(row.getValue()), W - PAD - 32, y, font(30, true), balance ? NAVY : INK);
            y += 52;
        }
        y = boxTop + boxH + 52;

        // Shop contact
        y = label(g, m, "FOR ASSISTANCE", y);
        text(g, m, BookingTemplateVariables.clean(b.shopPhone(), "Contact the shop"), PAD, y, font(30, true), INK, W - PAD * 2);
        y += 36;
        if (b.shopAddress() != null && !b.shopAddress().isBlank()) {
            for (String line : wrap(m, b.shopAddress().replaceAll("\\s+", " ").trim(), font(24, false), W - PAD * 2)) {
                text(g, m, line, PAD, y, font(24, false), MUTED, W - PAD * 2);
                y += 32;
            }
        }
        y += 40;

        // Footer
        int footerH = 96;
        if (g != null) { g.setColor(GREEN); g.fillRect(0, y, W, footerH); }
        String powered = "Powered by NearGFix";
        int pw = m.getFontMetrics(font(28, true)).stringWidth(powered);
        text(g, m, powered, (W - pw) / 2, y + 58, font(28, true), Color.WHITE, W);
        if (g == null) m.dispose();
        return y + footerH;
    }

    private static int label(Graphics2D g, Graphics2D m, String s, int y) {
        text(g, m, s, PAD, y + 8, font(22, true), GREEN, W - PAD * 2);
        return y + 50;
    }

    private static int rule(Graphics2D g, int y) {
        if (g != null) {
            g.setColor(LINE);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(PAD, y, W - PAD, y);
        }
        return y + 44;
    }

    private static void text(Graphics2D g, Graphics2D m, String s, int x, int y, Font f, Color c, int maxW) {
        if (g == null) return;
        g.setFont(f);
        g.setColor(c);
        g.drawString(ellipsize(m.getFontMetrics(f), s, maxW), x, y);
    }

    private static void rightText(Graphics2D g, Graphics2D m, String s, int right, int y, Font f, Color c) {
        if (g == null) return;
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, right - m.getFontMetrics(f).stringWidth(s), y);
    }

    /** At most three lines; the last one is ellipsized when drawn. */
    private static List<String> wrap(Graphics2D m, String s, Font f, int maxW) {
        FontMetrics fm = m.getFontMetrics(f);
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String[] words = s.split(" ");
        for (int i = 0; i < words.length; i++) {
            String next = cur.isEmpty() ? words[i] : cur + " " + words[i];
            if (fm.stringWidth(next) <= maxW || cur.isEmpty()) {
                cur = new StringBuilder(next);
            } else {
                lines.add(cur.toString());
                if (lines.size() == 2) {
                    cur = new StringBuilder(String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length)));
                    break;
                }
                cur = new StringBuilder(words[i]);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines.isEmpty() ? List.of("-") : lines;
    }

    private static String ellipsize(FontMetrics fm, String s, int maxW) {
        if (fm.stringWidth(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && fm.stringWidth(t + "…") > maxW) t = t.substring(0, t.length() - 1);
        return t.trim() + "…";
    }

    private static Font font(int size, boolean bold) {
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size);
    }

    private static Graphics2D measure() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "-" : s.trim();
    }
}
