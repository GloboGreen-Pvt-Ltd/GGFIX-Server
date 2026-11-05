package com.repairshop.saas.ticket.whatsapp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything a booking confirmation shows, read fresh from tickets + shops +
 * customer_users when the message is built (never sample data).
 */
public record BookingSnapshot(
        String bookingNumber,
        String status,
        String shopName,
        String shopPhone,
        String shopAddress,
        String shopLogoUrl,
        String customerName,
        String customerPhone,
        String customerAlternatePhone,
        Instant bookedAt,
        List<Device> devices) {

    public record Device(
            String trackingId,
            String name,
            String imageUrl,
            String servicesSummary,
            List<PriceItem> items,
            String accessories,
            BigDecimal estimate,
            BigDecimal paid,
            BigDecimal balance,
            Instant deliveryAt,
            String color,
            String imei,
            String serialNumber,
            String complaint,
            String missingParts) {

        /** Repair issues only — the auto "Service charge" row is shown as its own line. */
        public List<PriceItem> repairItems() {
            return items == null ? List.of() : items.stream().filter(i -> !i.serviceCharge()).toList();
        }

        public BigDecimal serviceCharge() {
            return items == null ? BigDecimal.ZERO : items.stream().filter(PriceItem::serviceCharge)
                    .map(PriceItem::amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /** label already carries the issue's note: "Display Replacement (cracked glass)". */
    public record PriceItem(String label, BigDecimal amount, String warranty, boolean serviceCharge) {}

    /** deviceDisplayName "Oppo A5 5G · CPH2751 (6 GB / 128 GB)" in parts (same split as the Partner App). */
    public record DeviceName(String name, String modelNumber, String ramStorage) {
        private static final Pattern P = Pattern.compile("^(.*?)(?: · ([^()]+?))?(?: \\(([^)]*)\\))?$");

        public static DeviceName parse(String raw) {
            String s = raw == null ? "" : raw.trim();
            Matcher m = P.matcher(s);
            if (!m.matches()) return new DeviceName(s.isEmpty() ? null : s, null, null);
            String name = m.group(1) == null || m.group(1).isBlank() ? (s.isEmpty() ? null : s) : m.group(1).trim();
            return new DeviceName(name, blankToNull(m.group(2)), blankToNull(m.group(3)));
        }

        private static String blankToNull(String v) {
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    private static final Map<String, String> STATUS_LABEL = Map.of(
            "CREATED", "Order Placed", "ASSIGNED", "Technician Assigned", "IN_DIAGNOSIS", "In Diagnosis",
            "IN_REPAIR", "In Service Process", "QUOTED", "Re-Estimated", "APPROVED", "Customer Approved",
            "READY", "Ready for Delivery", "DELIVERED", "Delivered", "CANCELLED", "Cancelled");

    /** Ticket status → the words the Partner App receipt prints (CREATED = "Order Placed"). */
    public String statusLabel() {
        String key = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty()) return "Order Placed";
        String known = STATUS_LABEL.get(key);
        if (known != null) return known;
        StringBuilder out = new StringBuilder();
        for (String w : key.toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    public BigDecimal total() {
        return devices.stream().map(Device::estimate).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal paid() {
        return devices.stream().map(Device::paid).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Stored balance when every device has one, else total − paid. */
    public BigDecimal balance() {
        boolean allStored = !devices.isEmpty() && devices.stream().allMatch(d -> d.balance() != null);
        if (allStored) return devices.stream().map(Device::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total().subtract(paid()).max(BigDecimal.ZERO);
    }

    /** Latest promised delivery across the booking's devices; null when none is set. */
    public Instant latestDelivery() {
        return devices.stream().map(Device::deliveryAt).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
    }
}
