package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.entity.Technician;
import com.repairshop.saas.ticket.repository.TechnicianRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Employee App: devices a shop is buying from customers (sell_orders, after
 * the customer accepted the shop's quote) that the owner assigned to the
 * calling pickup person (order-service POST /sell-orders/shop-purchases/{id}/assign).
 *
 * <p>Same shape and status vocabulary as {@link PickupBookingController}'s repair
 * pickups, plus {@code kind: "SELL"}, so the Employee App's pickup screens work
 * unchanged: PICKUP_PERSON_ASSIGNED (accept / decline) → PICKUP_ON_THE_WAY →
 * DEVICE_PICKED_UP → RECEIVED_AT_SHOP. CANCELLED from the pickup person means
 * "unable to pick up": the order goes back to the shop to reassign. Each step
 * notifies the customer and the shop. Customer contact and address are hidden
 * until the pickup is accepted.
 */
@RestController
@RequestMapping("/technicians/me/sell-pickups")
@RequiredArgsConstructor
public class SellPickupController {

    /** sell_orders.status → the Employee App's pickup codes. */
    private static final Map<String, String> TO_PICKUP = Map.of(
            "PICKUP_ASSIGNED", "PICKUP_PERSON_ASSIGNED",
            "OUT_FOR_PICKUP", "PICKUP_ON_THE_WAY",
            "PICKED_UP", "DEVICE_PICKED_UP",
            "CANCELLED", "CANCELLED");

    private final TechnicianRepository technicianRepository;
    private final JdbcTemplate jdbc;

    public record StatusRequest(String status, String note) {}
    public record DeclineRequest(String reason, String note) {}

    @GetMapping
    public List<Map<String, Object>> list(HttpServletRequest req) {
        Technician me = me(req);
        if (me == null || !hasTable()) return List.of();
        return jdbc.queryForList(SELECT + "WHERE s.shop_id = ? AND s.assigned_pickup_person_id = ? ORDER BY s.pickup_date NULLS LAST, s.updated_at DESC LIMIT 200",
                me.getShopId(), me.getId()).stream().map(this::row).toList();
    }

    @PostMapping("/{id}/accept")
    @Transactional
    public ResponseEntity<Map<String, Object>> accept(HttpServletRequest req, @PathVariable UUID id) {
        Technician me = requireMe(req);
        Map<String, Object> s = lockMine(me, id);
        if (s == null) return refuse(HttpStatus.CONFLICT, "NOT_ASSIGNED", "This pickup is no longer assigned to you.");
        if (s.get("pickup_accepted_at") != null) return ResponseEntity.ok(row(reload(id)));
        if (!"PICKUP_ASSIGNED".equals(s.get("status"))) return refuse(HttpStatus.CONFLICT, "STALE_STATUS", "This pickup was updated by the shop.");
        jdbc.update("UPDATE sell_orders SET pickup_accepted_at = now(), updated_at = now() WHERE id = ?", id);
        notifyShop(s, "SELL_PICKUP_ACCEPTED", "Pickup accepted", String.format("%s accepted the pickup for %s.", me.getName(), s.get("sell_number")));
        return ResponseEntity.ok(row(reload(id)));
    }

    /** Before the journey starts: the order goes back to the shop to reassign. */
    @PostMapping("/{id}/decline")
    @Transactional
    public ResponseEntity<Map<String, Object>> decline(HttpServletRequest req, @PathVariable UUID id, @RequestBody DeclineRequest body) {
        Technician me = requireMe(req);
        Map<String, Object> s = lockMine(me, id);
        if (s == null) return refuse(HttpStatus.CONFLICT, "NOT_ASSIGNED", "This pickup is no longer assigned to you.");
        if (!"PICKUP_ASSIGNED".equals(s.get("status"))) return refuse(HttpStatus.CONFLICT, "STALE_STATUS", "This pickup can't be declined now.");
        String reason = joinNote(body == null ? null : body.reason(), body == null ? null : body.note());
        if (reason == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a reason");
        unassign(id, "Declined by " + me.getName() + ": " + reason);
        notifyShop(s, "SELL_PICKUP_DECLINED", "Pickup declined — reassign",
                String.format("%s declined the pickup for %s: %s", me.getName(), s.get("sell_number"), reason));
        return ResponseEntity.ok(Map.of("id", id, "status", "DECLINED"));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<Map<String, Object>> status(HttpServletRequest req, @PathVariable UUID id, @RequestBody StatusRequest body) {
        Technician me = requireMe(req);
        Map<String, Object> s = lockMine(me, id);
        if (s == null) return refuse(HttpStatus.CONFLICT, "NOT_ASSIGNED", "This pickup is no longer assigned to you.");
        String next = body == null || body.status() == null ? "" : body.status().trim().toUpperCase();
        String cur = String.valueOf(s.get("status"));
        String number = String.valueOf(s.get("sell_number"));
        switch (next) {
            case "PICKUP_ON_THE_WAY" -> {
                if ("OUT_FOR_PICKUP".equals(cur)) return ResponseEntity.ok(row(reload(id)));
                if (!"PICKUP_ASSIGNED".equals(cur)) return stale();
                if (s.get("pickup_accepted_at") == null) return refuse(HttpStatus.CONFLICT, "ACCEPT_REQUIRED", "Accept this pickup first.");
                jdbc.update("UPDATE sell_orders SET status = 'OUT_FOR_PICKUP', pickup_started_at = now(), updated_at = now() WHERE id = ?", id);
                notifyCustomer(s, "SELL_PICKUP_ON_THE_WAY", "Pickup on the way", String.format("%s is on the way to collect your device.", me.getName()));
                notifyShop(s, "SELL_PICKUP_ON_THE_WAY", "Pickup started", String.format("%s started the journey for %s.", me.getName(), number));
            }
            case "DEVICE_PICKED_UP", "PICKED_UP" -> {
                if ("PICKED_UP".equals(cur)) return ResponseEntity.ok(row(reload(id)));
                if (!"OUT_FOR_PICKUP".equals(cur)) return stale();
                jdbc.update("UPDATE sell_orders SET status = 'PICKED_UP', picked_up_at = now(), updated_at = now() WHERE id = ?", id);
                notifyCustomer(s, "SELL_PICKED_UP", "Device picked up", String.format("%s collected your device and is taking it to the shop.", me.getName()));
                notifyShop(s, "SELL_PICKED_UP", "Device picked up", String.format("%s picked up the device for %s.", me.getName(), number));
            }
            case "RECEIVED_AT_SHOP" -> {
                if (s.get("received_at") != null) return ResponseEntity.ok(row(reload(id)));
                if (!"PICKED_UP".equals(cur)) return stale();
                jdbc.update("UPDATE sell_orders SET status = 'DEVICE_RECEIVED', received_at = now(), updated_at = now() WHERE id = ?", id);
                notifyCustomer(s, "SELL_DEVICE_RECEIVED", "Device received", "The shop received your device and will inspect it now.");
                notifyShop(s, "SELL_DEVICE_RECEIVED", "Device handed over", String.format("%s handed over the device for %s. Inspect it next.", me.getName(), number));
            }
            case "CANCELLED" -> {
                // Unable to pick up (before collection): back to the shop to reassign.
                if (!"PICKUP_ASSIGNED".equals(cur) && !"OUT_FOR_PICKUP".equals(cur)) return stale();
                String note = joinNote(body.note(), null);
                if (note == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tell the shop what went wrong");
                unassign(id, "Unable to pick up (" + me.getName() + "): " + note);
                notifyShop(s, "SELL_PICKUP_FAILED", "Pickup failed — reassign or reschedule",
                        String.format("%s couldn't pick up %s: %s", me.getName(), number, note));
                notifyCustomer(s, "SELL_PICKUP_FAILED", "Pickup didn't happen", "The pickup couldn't be completed. The shop will contact you to arrange it again.");
                return ResponseEntity.ok(Map.of("id", id, "status", "CANCELLED", "message", "Reported to the shop"));
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status");
        }
        return ResponseEntity.ok(row(reload(id)));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static final String SELECT =
            "SELECT s.*, mb.name AS brand_name, mm.name AS model_name, ms.label AS storage_label, "
                    + "cu.full_name AS customer_name, cu.mobile AS customer_mobile, "
                    + "ca.address_line, ca.locality, ca.city AS addr_city, ca.state AS addr_state, ca.pincode, ca.latitude AS addr_lat, ca.longitude AS addr_lng, "
                    + "ca.mobile AS addr_mobile, ca.full_name AS addr_name "
                    + "FROM sell_orders s "
                    + "LEFT JOIN master_brands mb ON mb.id = s.brand_id "
                    + "LEFT JOIN master_models mm ON mm.id = s.model_id "
                    + "LEFT JOIN master_storage_options ms ON ms.id = s.storage_option_id "
                    + "LEFT JOIN customer_users cu ON cu.id = s.customer_user_id "
                    + "LEFT JOIN customer_addresses ca ON ca.id = s.address_id ";

    private Map<String, Object> row(Map<String, Object> s) {
        boolean accepted = s.get("pickup_accepted_at") != null;
        String status = String.valueOf(s.get("status"));
        boolean handedOver = s.get("received_at") != null && !"CANCELLED".equals(status);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.get("id"));
        out.put("kind", "SELL");
        out.put("bookingNumber", s.get("sell_number"));
        out.put("shopId", s.get("shop_id"));
        out.put("serviceMode", "PICKUP");
        out.put("status", handedOver ? "RECEIVED_AT_SHOP" : TO_PICKUP.getOrDefault(status, "CANCELLED"));
        out.put("issueSummary", "Device purchase — collect the customer's device");
        out.put("pickupDate", s.get("pickup_date") == null ? null : ((Date) s.get("pickup_date")).toLocalDate().toString());
        out.put("pickupSlotStart", s.get("pickup_slot_start") == null ? null : ((Time) s.get("pickup_slot_start")).toLocalTime().toString());
        out.put("pickupSlotEnd", s.get("pickup_slot_end") == null ? null : ((Time) s.get("pickup_slot_end")).toLocalTime().toString());
        out.put("assignedPickupPersonId", s.get("assigned_pickup_person_id"));
        out.put("pickupPersonName", s.get("pickup_person_name"));
        out.put("accepted", accepted);
        out.put("acceptedAt", iso(s.get("pickup_accepted_at")));
        out.put("brandName", s.get("brand_name"));
        out.put("modelName", s.get("model_name"));
        out.put("storageLabel", s.get("storage_label"));
        out.put("color", s.get("color"));
        out.put("frontImageUrl", s.get("front_image_url"));
        if (accepted) {
            out.put("customerName", s.get("customer_name") != null ? s.get("customer_name") : s.get("addr_name"));
            out.put("customerMobile", s.get("addr_mobile") != null ? s.get("addr_mobile") : s.get("customer_mobile"));
            String addr = String.join(", ", Arrays.stream(new Object[] { s.get("address_line"), s.get("locality"), s.get("addr_city"), s.get("addr_state"), s.get("pincode") })
                    .filter(Objects::nonNull).map(Object::toString).filter(v -> !v.isBlank()).toList());
            out.put("pickupAddressText", addr.isBlank() ? null : addr);
            out.put("pickupLatitude", s.get("addr_lat"));
            out.put("pickupLongitude", s.get("addr_lng"));
        }
        List<Map<String, Object>> events = new ArrayList<>();
        event(events, "PICKUP_PERSON_ASSIGNED", s.get("pickup_assigned_at"), "Assigned by the shop");
        event(events, "PICKUP_ACCEPTED", s.get("pickup_accepted_at"), "Accepted");
        event(events, "PICKUP_ON_THE_WAY", s.get("pickup_started_at"), "On the way");
        event(events, "DEVICE_PICKED_UP", s.get("picked_up_at"), "Device collected");
        event(events, "RECEIVED_AT_SHOP", s.get("received_at"), "Handed over to the shop");
        out.put("events", events);
        out.put("createdAt", iso(s.get("pickup_assigned_at")));
        out.put("updatedAt", iso(s.get("updated_at")));
        return out;
    }

    private static void event(List<Map<String, Object>> events, String status, Object at, String note) {
        if (at == null) return;
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("status", status);
        e.put("note", note);
        e.put("createdAt", iso(at));
        events.add(e);
    }

    private void unassign(UUID id, String issue) {
        jdbc.update("UPDATE sell_orders SET assigned_pickup_person_id = NULL, pickup_person_name = NULL, pickup_person_phone = NULL, "
                + "pickup_assigned_at = NULL, pickup_accepted_at = NULL, pickup_started_at = NULL, pickup_issue = ?, "
                + "status = CASE WHEN pickup_date IS NULL THEN 'SELL_CONFIRMED' ELSE 'PICKUP_SCHEDULED' END, updated_at = now() WHERE id = ?",
                issue.length() > 500 ? issue.substring(0, 500) : issue, id);
    }

    /** The order locked, or null when it isn't assigned to this pickup person any more. */
    private Map<String, Object> lockMine(Technician me, UUID id) {
        if (!hasTable()) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM sell_orders WHERE id = ? AND shop_id = ? FOR UPDATE", id, me.getShopId());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pickup not found");
        Map<String, Object> s = rows.get(0);
        return me.getId().equals(s.get("assigned_pickup_person_id")) ? s : null;
    }

    private Map<String, Object> reload(UUID id) {
        return jdbc.queryForList(SELECT + "WHERE s.id = ?", id).get(0);
    }

    private void notifyCustomer(Map<String, Object> s, String key, String title, String body) {
        jdbc.update("INSERT INTO customer_notifications (id, customer_user_id, booking_id, booking_number, status_key, title, body, type, is_read, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'orders', false, now())",
                UUID.randomUUID(), s.get("customer_user_id"), s.get("id"), s.get("sell_number"), key, title, body);
    }

    private void notifyShop(Map<String, Object> s, String key, String title, String body) {
        jdbc.update("INSERT INTO shop_notifications (id, shop_id, booking_id, booking_number, status_key, title, body, type, is_read, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'purchase', false, now())",
                UUID.randomUUID(), s.get("shop_id"), s.get("id"), s.get("sell_number"), key, title, body);
    }

    private Technician me(HttpServletRequest req) {
        Object shop = req.getAttribute("shopId");
        Object user = req.getAttribute("userId");
        if (shop == null || user == null) return null;
        return technicianRepository.findByShopIdAndUserId(UUID.fromString(shop.toString()), UUID.fromString(user.toString())).orElse(null);
    }

    private Technician requireMe(HttpServletRequest req) {
        Technician me = me(req);
        if (me == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the shop's pickup staff can do this");
        return me;
    }

    /** Checked first, not caught: a failed statement would abort the surrounding transaction. */
    private boolean hasTable() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sell_orders' AND column_name = 'assigned_pickup_person_id')", Boolean.class));
    }

    private static ResponseEntity<Map<String, Object>> refuse(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }

    private static ResponseEntity<Map<String, Object>> stale() {
        return refuse(HttpStatus.CONFLICT, "STALE_STATUS", "This pickup was already updated.");
    }

    private static String joinNote(String a, String b) {
        String s = String.join(" — ", Arrays.stream(new String[] { a, b }).filter(v -> v != null && !v.isBlank()).map(String::trim).toList());
        return s.isBlank() ? null : (s.length() > 400 ? s.substring(0, 400) : s);
    }

    private static String iso(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp t) return t.toInstant().toString();
        if (o instanceof OffsetDateTime odt) return odt.toInstant().toString();
        return o.toString();
    }
}
