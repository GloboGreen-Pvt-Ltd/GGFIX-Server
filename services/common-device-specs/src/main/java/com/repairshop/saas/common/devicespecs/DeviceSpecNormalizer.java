package com.repairshop.saas.common.devicespecs;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever a client sent for one attribute into the single stored form,
 * or rejects it. The apps already send the stored form; this is what keeps a
 * hand-built request, an older build or an admin script from writing "16 GB",
 * "16gb" and "16GB" as three different RAM sizes.
 *
 * Mirrors the normalize* helpers in the Partner App's src/utils/deviceSpecs.js.
 */
public final class DeviceSpecNormalizer {

    private DeviceSpecNormalizer() {}

    private static final Pattern CAPACITY = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(GB|TB|MB|G|T|M)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CASE_SIZE = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(MM)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9]+(_[A-Z0-9]+)*$");
    private static final int MAX_CODE_LENGTH = 40;

    /** Storage type is a closed set. Keys are the value upper-cased with everything but letters/digits removed. */
    private static final Map<String, String> STORAGE_TYPES = Map.ofEntries(
            Map.entry("HDD", "HDD"),
            Map.entry("HARDDISK", "HDD"),
            Map.entry("HARDDISKDRIVE", "HDD"),
            Map.entry("HARDDISKDRIVEHDD", "HDD"),
            Map.entry("SATASSD", "SATA_SSD"),
            // Master data's Laptop "Storage Type" field lists a bare "SSD": a
            // laptop SSD that isn't called out as NVMe is a SATA drive.
            Map.entry("SSD", "SATA_SSD"),
            Map.entry("NVME", "NVME_SSD"),
            Map.entry("NVMESSD", "NVME_SSD")
    );

    public static final String STORAGE_TYPE_VALUES = "HDD, SATA_SSD, NVME_SSD";

    /** "16 GB" / "16gb" / "16" → "16GB"; "1 tb" → "1TB". */
    public static String capacity(String raw, DeviceSpecKey key) {
        String v = trimToNull(raw);
        if (v == null) return null;
        Matcher m = CAPACITY.matcher(v);
        if (!m.matches()) {
            throw new IllegalArgumentException(key.jsonKey() + " must be a size such as 16GB or 1TB (got '" + raw + "')");
        }
        String unit = m.group(2) == null ? "GB" : m.group(2).toUpperCase(Locale.ROOT);
        String suffix = unit.startsWith("T") ? "TB" : unit.startsWith("M") ? "MB" : "GB";
        return stripZeroFraction(m.group(1)) + suffix;
    }

    /** "NVMe SSD" / "nvme" / "NVME_SSD" → "NVME_SSD". */
    public static String storageType(String raw) {
        String v = trimToNull(raw);
        if (v == null) return null;
        String key = v.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        String out = STORAGE_TYPES.get(key);
        if (out == null) {
            throw new IllegalArgumentException("storageType must be one of " + STORAGE_TYPE_VALUES + " (got '" + raw + "')");
        }
        return out;
    }

    /** "44 mm" / "44mm" / "44" → "44MM". */
    public static String caseSize(String raw) {
        String v = trimToNull(raw);
        if (v == null) return null;
        Matcher m = CASE_SIZE.matcher(v);
        if (!m.matches()) {
            throw new IllegalArgumentException("caseSize must be a size in millimetres such as 44MM (got '" + raw + "')");
        }
        return stripZeroFraction(m.group(1)) + "MM";
    }

    /**
     * Open-ended code (connectivity, deviceType): "GPS + Cellular" → "GPS_CELLULAR",
     * "Bluetooth + Wi-Fi" → "BLUETOOTH_WIFI", "3.5 mm" → "AUX_3_5MM".
     * Open rather than a fixed enum so values an admin adds in master data are
     * accepted, while still being stored in one spelling.
     */
    public static String code(String raw, DeviceSpecKey key) {
        String v = trimToNull(raw);
        if (v == null) return null;
        String s = v.toUpperCase(Locale.ROOT)
                .replaceAll("WI[\\s-]?FI", "WIFI")
                .replaceAll("USB[\\s-]?C\\b|TYPE[\\s-]?C\\b", "USB_C")
                .replaceAll("3\\.5\\s*MM", "AUX_3_5MM")
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (s.isEmpty() || s.length() > MAX_CODE_LENGTH || !CODE.matcher(s).matches()) {
            throw new IllegalArgumentException(key.jsonKey() + " is not a valid value (got '" + raw + "')");
        }
        return s;
    }

    /** Normalize one attribute by its key. */
    public static String normalize(DeviceSpecKey key, String raw) {
        return switch (key) {
            case RAM, STORAGE_CAPACITY -> capacity(raw, key);
            case STORAGE_TYPE -> storageType(raw);
            case CASE_SIZE -> caseSize(raw);
            case CONNECTIVITY, DEVICE_TYPE -> code(raw, key);
        };
    }

    /** Lenient form for reading master data: null instead of an exception. */
    static String capacityOrNull(String raw) {
        try {
            return capacity(raw, DeviceSpecKey.STORAGE_CAPACITY);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String stripZeroFraction(String n) {
        return n.contains(".") ? n.replaceAll("\\.?0+$", "") : n;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
