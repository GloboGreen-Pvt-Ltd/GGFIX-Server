package com.repairshop.saas.subscription.invoice;

import com.repairshop.saas.subscription.billing.SubscriptionTerms;
import com.repairshop.saas.subscription.service.SettingsService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Invoice numbers: {@code {prefix}/{FY}/{seq:05}} e.g. {@code NGF/26-27/00001}
 * (≤ 16 characters as GST requires; FY = April–March in Asia/Kolkata). Razorpay
 * TEST-mode payments use their own series ({@code NGF/26-27/T0001}) so test
 * documents never consume the live, gap-free sequence.
 */
public final class InvoiceNumbers {

    private static final Pattern PREFIX = Pattern.compile("^[A-Z0-9]{1,4}$");

    private InvoiceNumbers() {}

    /** "26-27" for any instant from 1 Apr 2026 00:00 IST to 31 Mar 2027 23:59:59 IST. */
    public static String financialYear(Instant at) {
        LocalDate d = at.atZone(SubscriptionTerms.IST).toLocalDate();
        int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return String.format("%02d-%02d", start % 100, (start + 1) % 100);
    }

    /** The configured prefix when it is 1–4 letters/digits, else the default "NGF". */
    public static String prefix(String configured) {
        String p = configured == null ? "" : configured.trim().toUpperCase(Locale.ROOT);
        return PREFIX.matcher(p).matches() ? p : SettingsService.DEFAULT_INVOICE_PREFIX;
    }

    /** Counter key: one gap-free sequence per prefix + FY (+ a separate TEST series). */
    public static String series(String prefix, String fy, boolean test) {
        return (test ? "TEST:" : "") + prefix + "/" + fy;
    }

    public static String format(String prefix, String fy, long seq, boolean test) {
        return prefix + "/" + fy + "/" + (test ? "T" + String.format("%04d", seq) : String.format("%05d", seq));
    }

    /** "NGF-26-27-00001.pdf" — the download file name. */
    public static String fileName(String invoiceNo) {
        return (invoiceNo == null ? "invoice" : invoiceNo.replaceAll("[^A-Za-z0-9-]", "-")) + ".pdf";
    }
}
