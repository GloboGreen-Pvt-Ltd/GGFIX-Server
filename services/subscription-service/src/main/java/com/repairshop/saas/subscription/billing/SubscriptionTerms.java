package com.repairshop.saas.subscription.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Term arithmetic. Terms are CALENDAR months/days in Asia/Kolkata — a 6-month
 * plan bought on 31 Aug ends on 28 Feb (plusMonths clamps), never "180 days".
 */
public final class SubscriptionTerms {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final String DAYS = "DAYS";
    public static final String MONTHS = "MONTHS";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private SubscriptionTerms() {}

    public static Instant termEnd(Instant start, int value, String unit) {
        ZonedDateTime z = start.atZone(IST);
        ZonedDateTime end = DAYS.equalsIgnoreCase(unit) ? z.plusDays(value) : z.plusMonths(value);
        return end.toInstant();
    }

    public static Instant plus(Instant from, int days, int months) {
        return from.atZone(IST).plusMonths(months).plusDays(days).toInstant();
    }

    /** "6 Months", "1 Month", "15 Days". */
    public static String durationLabel(int value, String unit) {
        boolean days = DAYS.equalsIgnoreCase(unit);
        String noun = days ? (value == 1 ? "Day" : "Days") : (value == 1 ? "Month" : "Months");
        return value + " " + noun;
    }

    /**
     * "₹1,23,456.50" with en-IN (lakh) grouping, for human-readable line
     * labels only. Done by hand: java.text.DecimalFormat supports a single
     * grouping size, so it cannot produce the 3-then-2 Indian pattern.
     */
    public static String rupees(long paise) {
        long abs = Math.abs(paise);
        String whole = Long.toString(abs / 100);
        String grouped = whole;
        if (whole.length() > 3) {
            String head = whole.substring(0, whole.length() - 3);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < head.length(); i++) {
                sb.append(head.charAt(i));
                int remaining = head.length() - i - 1;
                if (remaining > 0 && remaining % 2 == 0) sb.append(',');
            }
            grouped = sb + "," + whole.substring(whole.length() - 3);
        }
        return (paise < 0 ? "-" : "") + "₹" + grouped + "." + String.format("%02d", abs % 100);
    }

    /** "31 Oct 2026" in IST, for notices. */
    public static String date(Instant instant) {
        return instant == null ? "" : DATE.format(instant.atZone(IST));
    }

    /** Integer paise from a NUMERIC rupee amount (legacy price_amount). */
    public static long paiseOf(BigDecimal rupees) {
        return rupees == null ? 0L : rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** NUMERIC(10,2) rupees from integer paise. */
    public static BigDecimal rupeesOf(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
    }
}
