package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Partner-scoped ticket numbers: {first letter of the business name}TS-{seq},
 * e.g. GTS-001 … GTS-999, GTS-1000.
 *
 * The partner is the shop's owner (shops.owner_user_id, or the shop itself when
 * it has none), so every shop of one owner draws from one counter and a new
 * partner starts at 001. The counter row is bumped with a single upsert that
 * row-locks it until the caller's transaction commits — concurrent bookings for
 * the same partner queue on that lock instead of reading the same value, and a
 * rolled-back booking hands its number back. See 98_partner_ticket_numbers.sql.
 */
@Service
@RequiredArgsConstructor
public class TicketNumberService {

    private final JdbcTemplate jdbc;

    /**
     * trackingId is the device's own id (GTS-001 / GTS-001-A); bookingNumber is
     * the parent number every device of the booking shares (GTS-001);
     * deviceSuffix is null for a single-device booking.
     */
    public record Assigned(UUID partnerId, String trackingId, String bookingNumber, String deviceSuffix) {}

    /** Single-device booking: a fresh number, no suffix. */
    @Transactional
    public Assigned next(UUID shopId) {
        return next(shopId, false);
    }

    /**
     * A fresh booking number. {@code multiDevice} makes this the booking's first
     * device (suffix A); the rest join with {@link #nextInBooking}.
     */
    @Transactional
    public Assigned next(UUID shopId, boolean multiDevice) {
        Map<String, Object> shop = partnerOf(shopId);
        UUID partnerId = (UUID) shop.get("partner_id");
        Long seq = jdbc.queryForObject(
                "INSERT INTO partner_ticket_sequences (partner_id, last_value) VALUES (?, 1) " +
                        "ON CONFLICT (partner_id) DO UPDATE " +
                        "SET last_value = partner_ticket_sequences.last_value + 1, updated_at = now() " +
                        "RETURNING last_value",
                Long.class, partnerId);
        String booking = initial((String) shop.get("name")) + "TS-" + String.format("%03d", seq);
        return multiDevice
                ? new Assigned(partnerId, booking + "-A", booking, "A")
                : new Assigned(partnerId, booking, booking, null);
    }

    /**
     * Adds a device to an existing booking of this partner: the next free
     * suffix, without drawing a new number or renaming any existing ticket. A
     * booking that so far had one device (GTS-001, no suffix) counts that device
     * as A, so the newcomer is GTS-001-B.
     *
     * Serialised per booking with a transaction-scoped advisory lock, so two
     * devices added at once can't pick the same suffix; the unique indexes on
     * (partner_id, tracking_id) and (partner_id, booking_number, device_suffix)
     * back that up.
     */
    @Transactional
    public Assigned nextInBooking(UUID shopId, String bookingNumber, UUID customerId) {
        Map<String, Object> shop = partnerOf(shopId);
        UUID partnerId = (UUID) shop.get("partner_id");
        String booking = bookingNumber.trim().toUpperCase();
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, partnerId + ":" + booking);

        List<Map<String, Object>> devices = jdbc.queryForList(
                "SELECT id, tracking_id, booking_number, device_suffix, customer_id FROM tickets " +
                        "WHERE partner_id = ? AND (booking_number = ? OR tracking_id = ?)",
                partnerId, booking, booking);
        if (devices.isEmpty()) {
            throw new ResourceNotFoundException("Booking not found: " + booking);
        }
        // Rule: every device of a booking belongs to the same customer.
        if (customerId != null) {
            for (Map<String, Object> d : devices) {
                Object existing = d.get("customer_id");
                if (existing != null && !customerId.equals(existing)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Booking " + booking + " belongs to a different customer");
                }
            }
        }

        Set<String> used = new HashSet<>();
        for (Map<String, Object> d : devices) {
            String suffix = (String) d.get("device_suffix");
            if (suffix != null) {
                used.add(suffix);
            } else if (booking.equals(d.get("tracking_id"))) {
                // The booking's original single device keeps its id and is "A".
                used.add("A");
                if (d.get("booking_number") == null) {
                    jdbc.update("UPDATE tickets SET booking_number = ? WHERE id = ?", booking, d.get("id"));
                }
            }
        }
        String next = nextSuffix(used);
        return new Assigned(partnerId, booking + "-" + next, booking, next);
    }

    /** A … Z, then AA, AB … — the first one not already used. */
    static String nextSuffix(Set<String> used) {
        for (int n = 0; ; n++) {
            String s = suffixAt(n);
            if (!used.contains(s)) return s;
        }
    }

    private static String suffixAt(int n) {
        StringBuilder sb = new StringBuilder();
        n += 1;
        while (n > 0) {
            n -= 1;
            sb.insert(0, (char) ('A' + n % 26));
            n /= 26;
        }
        return sb.toString();
    }

    private Map<String, Object> partnerOf(UUID shopId) {
        try {
            return jdbc.queryForMap(
                    "SELECT COALESCE(owner_user_id, id) AS partner_id, name FROM shops WHERE id = ?",
                    shopId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResourceNotFoundException("Shop not found: " + shopId);
        }
    }

    /** First letter of the business name, upper-cased; a digit if it has no letter, else "X". */
    static String initial(String name) {
        if (name != null) {
            String s = name.trim();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c < 128 && Character.isLetter(c)) return String.valueOf(Character.toUpperCase(c));
            }
            for (int i = 0; i < s.length(); i++) {
                if (Character.isDigit(s.charAt(i)) && s.charAt(i) < 128) return String.valueOf(s.charAt(i));
            }
        }
        return "X";
    }
}
