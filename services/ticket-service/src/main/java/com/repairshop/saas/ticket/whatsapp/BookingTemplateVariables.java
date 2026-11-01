package com.repairshop.saas.ticket.whatsapp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The eight body variables of the approved "neargfix_booking_confirmed" template:
 * {{1}} customer, {{2}} shop, {{3}} booking id, {{4}} device(s), {{5}} service(s),
 * {{6}} estimated cost (the template prints the ₹), {{7}} estimated delivery,
 * {{8}} shop contact. Order matters — it is the template's own numbering.
 */
public final class BookingTemplateVariables {

    /** WhatsApp rejects empty parameters, newlines/tabs and long runs of spaces. */
    private static final int MAX_PARAM = 160;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);

    private BookingTemplateVariables() {}

    public static List<String> of(BookingSnapshot b, ZoneId zone) {
        List<String> v = new ArrayList<>(8);
        v.add(clean(b.customerName(), "Customer"));
        v.add(clean(b.shopName(), "Your Shop"));
        v.add(clean(b.bookingNumber(), "-"));
        v.add(clean(join(b.devices().stream().map(BookingTemplateVariables::device).toList(), ", "), "Device"));
        v.add(clean(join(b.devices().stream().map(BookingTemplateVariables::services).toList(), "; "), "-"));
        v.add(money(b.total()));
        v.add(when(b.latestDelivery(), zone));
        v.add(clean(b.shopPhone(), "Contact the shop"));
        return v;
    }

    /** "Oppo A5 5G · CPH2751 (6 GB / 128 GB · Aurora Green)" — same wording as the Partner App share text. */
    static String device(BookingSnapshot.Device d) {
        BookingSnapshot.DeviceName n = BookingSnapshot.DeviceName.parse(d.name());
        String spec = join(java.util.Arrays.asList(n.ramStorage(), d.color()), " · ");
        return (n.name() == null ? "Device" : n.name())
                + (n.modelNumber() == null ? "" : " · " + n.modelNumber())
                + (spec.isEmpty() ? "" : " (" + spec + ")");
    }

    /** A device's repair issues (with notes), not the service charge; else its summary line. */
    static String services(BookingSnapshot.Device d) {
        String names = join(d.repairItems().stream().map(BookingSnapshot.PriceItem::label).toList(), ", ");
        if (!names.isEmpty()) return names;
        return d.servicesSummary();
    }

    public static String when(Instant at, ZoneId zone) {
        return at == null ? "To Be Confirmed" : WHEN.format(at.atZone(zone)).replace("am", "AM").replace("pm", "PM");
    }

    /** Indian grouping, no symbol: 150000 → "1,50,000"; 1499.5 → "1,499.50". */
    public static String money(BigDecimal amount) {
        BigDecimal a = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        boolean negative = a.signum() < 0;
        a = a.abs();
        String whole = a.toBigInteger().toString();
        int cents = a.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        StringBuilder out = new StringBuilder();
        int len = whole.length();
        if (len <= 3) out.append(whole);
        else {
            String head = whole.substring(0, len - 3);
            StringBuilder h = new StringBuilder();
            for (int i = head.length(); i > 0; i -= 2) {
                h.insert(0, head.substring(Math.max(0, i - 2), i));
                if (i - 2 > 0) h.insert(0, ',');
            }
            out.append(h).append(',').append(whole.substring(len - 3));
        }
        if (cents != 0) out.append('.').append(String.format("%02d", cents));
        return (negative ? "-" : "") + out;
    }

    static String clean(String s, String fallback) {
        String t = s == null ? "" : s.replaceAll("[\r\n\t]+", " ").replaceAll(" {2,}", " ").trim();
        if (t.isEmpty()) t = fallback;
        return t.length() > MAX_PARAM ? t.substring(0, MAX_PARAM - 1).trim() + "…" : t;
    }

    private static String join(List<String> parts, String sep) {
        Set<String> seen = new LinkedHashSet<>();
        parts.stream().filter(Objects::nonNull).map(String::trim).filter(p -> !p.isEmpty()).forEach(seen::add);
        return seen.stream().collect(Collectors.joining(sep));
    }
}
