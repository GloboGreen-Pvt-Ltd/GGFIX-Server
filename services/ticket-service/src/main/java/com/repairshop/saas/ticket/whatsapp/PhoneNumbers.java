package com.repairshop.saas.ticket.whatsapp;

/** E.164 formatting and log masking for customer WhatsApp numbers. */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    /**
     * "+919876543210" for "98765 43210", "09876543210", "919876543210" or
     * "+91 98765-43210" (defaultCc "91"); null when it cannot be a mobile number.
     */
    public static String toE164(String raw, String defaultCc) {
        if (raw == null) return null;
        String s = raw.trim();
        boolean plus = s.startsWith("+");
        String digits = s.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        if (!plus) {
            if (digits.startsWith("00")) digits = digits.substring(2);
            else if (digits.length() == 11 && digits.startsWith("0")) digits = defaultCc + digits.substring(1);
            else if (digits.length() == 10) digits = defaultCc + digits;
        }
        if (digits.length() < 8 || digits.length() > 15 || digits.startsWith("0")) return null;
        // Indian numbers: 91 + a 10-digit mobile starting 6–9 (landlines can't receive WhatsApp templates reliably).
        if (digits.startsWith("91") && (digits.length() != 12 || "6789".indexOf(digits.charAt(2)) < 0)) return null;
        return "+" + digits;
    }

    /** "+91******3210" — the only form a phone number takes in logs and API responses. */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 6) return "—";
        int keep = 4;
        int prefix = phone.startsWith("+91") ? 3 : Math.min(3, phone.length() - keep);
        return phone.substring(0, prefix) + "*".repeat(phone.length() - prefix - keep) + phone.substring(phone.length() - keep);
    }
}
