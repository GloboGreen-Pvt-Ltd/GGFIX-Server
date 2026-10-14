package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
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

    public record Assigned(UUID partnerId, String trackingId) {}

    @Transactional
    public Assigned next(UUID shopId) {
        Map<String, Object> shop;
        try {
            shop = jdbc.queryForMap(
                    "SELECT COALESCE(owner_user_id, id) AS partner_id, name FROM shops WHERE id = ?",
                    shopId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResourceNotFoundException("Shop not found: " + shopId);
        }
        UUID partnerId = (UUID) shop.get("partner_id");
        Long seq = jdbc.queryForObject(
                "INSERT INTO partner_ticket_sequences (partner_id, last_value) VALUES (?, 1) " +
                        "ON CONFLICT (partner_id) DO UPDATE " +
                        "SET last_value = partner_ticket_sequences.last_value + 1, updated_at = now() " +
                        "RETURNING last_value",
                Long.class, partnerId);
        return new Assigned(partnerId, initial((String) shop.get("name")) + "TS-" + String.format("%03d", seq));
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
