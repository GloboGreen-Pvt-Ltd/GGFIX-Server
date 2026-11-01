package com.repairshop.saas.ticket.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads a booking (all devices sharing its booking number) and its shop, scoped to the shop. */
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
                "SELECT tracking_id, customer_name, customer_phone, device_display_name, device_image_url, "
                        + "repair_services_summary, price_items_json, accessories, estimated_price, payment_amount, "
                        + "balance_amount, estimated_delivery_at, created_at FROM tickets "
                        + "WHERE shop_id = ? AND (booking_number = ? OR (booking_number IS NULL AND tracking_id = ?)) "
                        + "AND COALESCE(status, '') <> 'CANCELLED' ORDER BY tracking_id",
                shopId, bookingNumber, bookingNumber);
        if (rows.isEmpty()) return null;

        List<BookingSnapshot.Device> devices = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            devices.add(new BookingSnapshot.Device(
                    str(r.get("tracking_id")), str(r.get("device_display_name")), str(r.get("device_image_url")),
                    str(r.get("repair_services_summary")), priceItems(str(r.get("price_items_json"))),
                    str(r.get("accessories")), dec(r.get("estimated_price")), dec(r.get("payment_amount")),
                    dec(r.get("balance_amount")),
                    r.get("estimated_delivery_at") instanceof Timestamp t ? t.toInstant() : null));
        }
        Map<String, Object> first = rows.get(0);
        Map<String, Object> shop = jdbc.queryForList("SELECT name, phone, address, city FROM shops WHERE id = ?", shopId)
                .stream().findFirst().orElse(Map.of());
        String address = joinNonBlank(str(shop.get("address")), str(shop.get("city")));
        return new BookingSnapshot(bookingNumber, str(shop.get("name")), str(shop.get("phone")), address,
                str(first.get("customer_name")), str(first.get("customer_phone")),
                first.get("created_at") instanceof Timestamp t ? t.toInstant() : null, devices);
    }

    /** priceItemsJson as the Partner App stores it: [{label, amount, warranty}]. Bad JSON → no items. */
    List<BookingSnapshot.PriceItem> priceItems(String json) {
        List<BookingSnapshot.PriceItem> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            JsonNode arr = mapper.readTree(json);
            if (!arr.isArray()) return out;
            for (JsonNode n : arr) {
                String label = text(n.path("label"));
                if (label == null) label = text(n.path("name"));
                BigDecimal amount = n.path("amount").isNumber() ? n.path("amount").decimalValue()
                        : n.path("amount").isTextual() ? parseDec(n.path("amount").asText()) : null;
                out.add(new BookingSnapshot.PriceItem(label, amount, warranty(n.path("warranty"))));
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return out;
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
