package com.repairshop.saas.ticket.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads a booking (all devices sharing its booking number), its shop and the
 * customer's alternate number, scoped to the shop. Issue notes and the
 * remaining complaint are split out of issue_description the same way the
 * Partner App receipt does it.
 */
@Component
public class BookingSnapshotLoader {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public BookingSnapshotLoader(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** null when the booking has no tickets in this shop. */
    public BookingSnapshot load(UUID shopId, String bookingNumber) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT tracking_id, status, customer_id, customer_name, customer_phone, device_display_name, device_image_url, "
                        + "repair_services_summary, price_items_json, accessories, estimated_price, payment_amount, "
                        + "balance_amount, estimated_delivery_at, created_at, color, imei, serial_number, issue_description, "
                        + "missing_parts_json FROM tickets "
                        + "WHERE shop_id = ? AND (booking_number = ? OR (booking_number IS NULL AND tracking_id = ?)) "
                        + "AND COALESCE(status, '') <> 'CANCELLED' ORDER BY tracking_id",
                shopId, bookingNumber, bookingNumber);
        if (rows.isEmpty()) return null;

        List<BookingSnapshot.Device> devices = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String issueText = str(r.get("issue_description"));
            List<BookingSnapshot.PriceItem> items = priceItems(str(r.get("price_items_json")), issueText);
            List<String> issueNames = rawIssueNames(str(r.get("price_items_json")));
            devices.add(new BookingSnapshot.Device(
                    str(r.get("tracking_id")), str(r.get("device_display_name")), str(r.get("device_image_url")),
                    str(r.get("repair_services_summary")), items, blankToNull(str(r.get("accessories"))),
                    dec(r.get("estimated_price")), dec(r.get("payment_amount")), dec(r.get("balance_amount")),
                    r.get("estimated_delivery_at") instanceof Timestamp t ? t.toInstant() : null,
                    blankToNull(str(r.get("color"))), blankToNull(str(r.get("imei"))), blankToNull(str(r.get("serial_number"))),
                    complaint(issueText, issueNames), partsText(str(r.get("missing_parts_json")))));
        }
        Map<String, Object> first = rows.get(0);
        Map<String, Object> shop = jdbc.queryForList(
                        "SELECT name, phone, mobile, address, city, front_image_url FROM shops WHERE id = ?", shopId)
                .stream().findFirst().orElse(Map.of());
        String shopPhone = blankToNull(str(shop.get("mobile")));
        if (shopPhone == null) shopPhone = blankToNull(str(shop.get("phone")));
        return new BookingSnapshot(bookingNumber, str(first.get("status")), str(shop.get("name")), shopPhone,
                joinNonBlank(str(shop.get("address")), str(shop.get("city"))), blankToNull(str(shop.get("front_image_url"))),
                str(first.get("customer_name")), str(first.get("customer_phone")), alternatePhone(first.get("customer_id")),
                first.get("created_at") instanceof Timestamp t ? t.toInstant() : null, devices);
    }

    private String alternatePhone(Object customerId) {
        if (customerId == null) return null;
        try {
            return jdbc.queryForList("SELECT alternate_mobile FROM customer_users WHERE id = CAST(? AS UUID)", customerId.toString())
                    .stream().findFirst().map(m -> blankToNull(str(m.get("alternate_mobile")))).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * priceItemsJson as the Partner App stores it: [{code, label, amount, warranty}].
     * A repair issue's typed note ("<issue>: <note>" line of issue_description) is
     * appended in brackets; code SERVICE_CHARGE marks the service-charge row.
     */
    List<BookingSnapshot.PriceItem> priceItems(String json, String issueText) {
        List<BookingSnapshot.PriceItem> out = new ArrayList<>();
        for (JsonNode n : array(json)) {
            String label = text(n.path("label"));
            if (label == null) label = text(n.path("name"));
            boolean charge = "SERVICE_CHARGE".equalsIgnoreCase(text(n.path("code")) == null ? "" : text(n.path("code")))
                    || "SERVICE_CHARGE".equalsIgnoreCase(text(n.path("serviceCode")) == null ? "" : text(n.path("serviceCode")));
            BigDecimal amount = n.path("amount").isNumber() ? n.path("amount").decimalValue()
                    : n.path("amount").isTextual() ? parseDec(n.path("amount").asText()) : null;
            String note = charge ? null : noteFor(issueText, label);
            out.add(new BookingSnapshot.PriceItem(note == null ? label : label + " (" + note + ")", amount,
                    warranty(n.path("warranty")), charge));
        }
        return out;
    }

    private List<String> rawIssueNames(String json) {
        List<String> names = new ArrayList<>();
        for (JsonNode n : array(json)) {
            if ("SERVICE_CHARGE".equalsIgnoreCase(n.path("code").asText(""))) continue;
            String label = text(n.path("label"));
            if (label != null) names.add(label);
        }
        return names;
    }

    static String noteFor(String issueText, String label) {
        if (issueText == null || label == null) return null;
        String prefix = label + ": ";
        return Arrays.stream(issueText.split("\n")).filter(l -> l.startsWith(prefix))
                .map(l -> l.substring(prefix.length()).trim()).filter(s -> !s.isEmpty()).findFirst().orElse(null);
    }

    /** The rest of issue_description: issue-note lines and a plain list of the issues themselves never repeat. */
    static String complaint(String issueText, List<String> names) {
        if (issueText == null || issueText.isBlank()) return null;
        String rest = Arrays.stream(issueText.split("\n"))
                .filter(l -> names.stream().noneMatch(n -> l.startsWith(n + ": ")))
                .collect(Collectors.joining("\n")).trim();
        return rest.isEmpty() || rest.equals(String.join(", ", names)) ? null : rest;
    }

    /** missing_parts_json → "Display (Damaged – cracked), SIM Card Tray (Missing)"; null when none. */
    String partsText(String json) {
        List<String> parts = new ArrayList<>();
        for (JsonNode p : array(json)) {
            boolean missing = p.path("missing").asBoolean(false);
            boolean damage = p.path("damage").asBoolean(false);
            if (!missing && !damage) continue;
            String name = text(p.path("partName"));
            if (name == null) name = text(p.path("name"));
            if (name == null) name = text(p.path("partId"));
            String detail = text(p.path("detail"));
            parts.add((name == null ? "Part" : name) + " (" + (damage ? "Damaged" : "Missing") + (detail == null ? "" : " – " + detail) + ")");
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private Iterable<JsonNode> array(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode arr = mapper.readTree(json);
            return arr.isArray() ? arr : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String warranty(JsonNode w) {
        if (w == null || w.isMissingNode() || w.isNull()) return null;
        if (w.isTextual() || w.isNumber()) return text(w);
        for (String f : List.of("label", "name", "duration", "text")) {
            String v = text(w.path(f));
            if (v != null) return v;
        }
        return null;
    }

    private static String text(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        String s = n.asText("").trim();
        return s.isEmpty() ? null : s;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BigDecimal dec(Object o) {
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        return null;
    }

    private static BigDecimal parseDec(String s) {
        try {
            return new BigDecimal(s.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String joinNonBlank(String a, String b) {
        boolean ha = a != null && !a.isBlank(), hb = b != null && !b.isBlank();
        if (ha && hb) return a.toLowerCase().contains(b.toLowerCase()) ? a : a + ", " + b;
        return ha ? a : hb ? b : null;
    }
}
