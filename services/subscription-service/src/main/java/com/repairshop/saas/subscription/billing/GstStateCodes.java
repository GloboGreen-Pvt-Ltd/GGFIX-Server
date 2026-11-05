package com.repairshop.saas.subscription.billing;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The official GST state / UT code table (first two digits of a GSTIN), plus
 * name lookup for shops that only carry a state name. Codes 96/97/99 are the
 * GSTN special codes (foreign country, other territory, centre jurisdiction).
 */
public final class GstStateCodes {

    private GstStateCodes() {}

    /** 15 characters: 2-digit state code, PAN (5 letters, 4 digits, 1 letter), entity no, 'Z', check char. */
    private static final Pattern GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    private static final Map<String, String> BY_NAME = new LinkedHashMap<>();

    static {
        put("01", "Jammu and Kashmir");
        put("02", "Himachal Pradesh");
        put("03", "Punjab");
        put("04", "Chandigarh");
        put("05", "Uttarakhand");
        put("06", "Haryana");
        put("07", "Delhi");
        put("08", "Rajasthan");
        put("09", "Uttar Pradesh");
        put("10", "Bihar");
        put("11", "Sikkim");
        put("12", "Arunachal Pradesh");
        put("13", "Nagaland");
        put("14", "Manipur");
        put("15", "Mizoram");
        put("16", "Tripura");
        put("17", "Meghalaya");
        put("18", "Assam");
        put("19", "West Bengal");
        put("20", "Jharkhand");
        put("21", "Odisha");
        put("22", "Chhattisgarh");
        put("23", "Madhya Pradesh");
        put("24", "Gujarat");
        put("25", "Daman and Diu");
        put("26", "Dadra and Nagar Haveli and Daman and Diu");
        put("27", "Maharashtra");
        put("28", "Andhra Pradesh (Old)");
        put("29", "Karnataka");
        put("30", "Goa");
        put("31", "Lakshadweep");
        put("32", "Kerala");
        put("33", "Tamil Nadu");
        put("34", "Puducherry");
        put("35", "Andaman and Nicobar Islands");
        put("36", "Telangana");
        put("37", "Andhra Pradesh");
        put("38", "Ladakh");
        put("96", "Foreign Country");
        put("97", "Other Territory");
        put("99", "Centre Jurisdiction");

        // Common spellings a shop profile may carry. Andhra Pradesh resolves to the
        // post-2014 code 37; the merged UT (2020) uses 26 for both of its old names.
        alias("andhra pradesh", "37");
        alias("daman and diu", "26");
        alias("dadra and nagar haveli", "26");
        alias("dnh and dd", "26");
        alias("orissa", "21");
        alias("pondicherry", "34");
        alias("uttaranchal", "05");
        alias("new delhi", "07");
        alias("nct of delhi", "07");
        alias("national capital territory of delhi", "07");
        alias("jammu kashmir", "01");
        alias("j and k", "01");
        alias("andaman and nicobar", "35");
        alias("andaman nicobar islands", "35");
        alias("chattisgarh", "22");
        alias("tamilnadu", "33");
        alias("tn", "33");
    }

    private static void put(String code, String name) {
        NAMES.put(code, name);
        BY_NAME.put(normalise(name), code);
    }

    private static void alias(String name, String code) {
        BY_NAME.put(normalise(name), code);
    }

    /** Official name for a 2-digit code, or null. */
    public static String name(String code) {
        return code == null ? null : NAMES.get(code.trim());
    }

    public static boolean isKnownCode(String code) {
        return code != null && NAMES.containsKey(code.trim());
    }

    /** Code for a state name ("Tamil Nadu", "TAMIL NADU", "Orissa"…), or null when unknown. */
    public static String codeForName(String stateName) {
        if (stateName == null || stateName.isBlank()) return null;
        return BY_NAME.get(normalise(stateName));
    }

    /** Upper-cased GSTIN when it is a well-formed GSTIN with a known state code, else null. */
    public static String validGstin(String raw) {
        if (raw == null) return null;
        String g = raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!GSTIN.matcher(g).matches()) return null;
        return isKnownCode(g.substring(0, 2)) ? g : null;
    }

    /** State code from a valid GSTIN, else null. */
    public static String codeFromGstin(String raw) {
        String g = validGstin(raw);
        return g == null ? null : g.substring(0, 2);
    }

    /** "Tamil Nadu (33)" for a known code, else null. */
    public static String label(String code) {
        String name = name(code);
        return name == null ? null : name + " (" + code.trim() + ")";
    }

    private static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
