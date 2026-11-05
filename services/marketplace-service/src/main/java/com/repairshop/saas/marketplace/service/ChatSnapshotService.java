package com.repairshop.saas.marketplace.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-table reads for the chat module. Marketplace-service doesn't own
 * customer_users or shops; in the shared-Postgres setup these tables live in
 * auth-service's schema. When marketplace-service runs on its H2 dev profile
 * (in-memory, isolated), those tables don't exist at all — so every JDBC
 * call below MUST tolerate "table not found" without bubbling a 500 to the
 * client. Snapshots are best-effort enrichment; the chat itself works
 * regardless of whether we can read counterpart name/avatar.
 */
@Service
@RequiredArgsConstructor
public class ChatSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ChatSnapshotService.class);

    private final JdbcTemplate jdbc;

    public record CustomerSnapshot(String name, String mobile, String avatarUrl, Instant lastSeenAt) {}
    public record ShopSnapshot(String name, String imageUrl, String phone, Instant lastSeenAt) {}

    public CustomerSnapshot loadCustomer(UUID customerUserId) {
        if (customerUserId == null) return new CustomerSnapshot(null, null, null, null);
        try {
            return jdbc.queryForObject(
                "SELECT full_name, mobile, profile_image_url, last_seen_at " +
                "FROM customer_users WHERE id = ?",
                (rs, i) -> new CustomerSnapshot(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getTimestamp(4) != null ? rs.getTimestamp(4).toInstant() : null
                ),
                customerUserId
            );
        } catch (EmptyResultDataAccessException e) {
            return new CustomerSnapshot(null, null, null, null);
        } catch (DataAccessException e) {
            // H2 dev profile: customer_users table doesn't exist here. Don't
            // 500 the chat — just return an empty snapshot so thread creation
            // still succeeds with a placeholder name.
            log.debug("loadCustomer fell back to empty snapshot: {}", e.getMessage());
            return new CustomerSnapshot(null, null, null, null);
        }
    }

    public ShopSnapshot loadShop(UUID shopId) {
        if (shopId == null) return new ShopSnapshot(null, null, null, null);
        try {
            // The shops table uses `mobile` for the contact number (per
            // auth-service schema) — the historical `phone` column never
            // existed, so this snapshot has been silently empty in production.
            return jdbc.queryForObject(
                "SELECT name, banner_image_url, mobile, last_seen_at " +
                "FROM shops WHERE id = ?",
                (rs, i) -> new ShopSnapshot(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getTimestamp(4) != null ? rs.getTimestamp(4).toInstant() : null
                ),
                shopId
            );
        } catch (EmptyResultDataAccessException e) {
            return new ShopSnapshot(null, null, null, null);
        } catch (DataAccessException e) {
            log.debug("loadShop fell back to empty snapshot: {}", e.getMessage());
            return new ShopSnapshot(null, null, null, null);
        }
    }

    public Instant getCustomerLastSeen(UUID customerUserId) {
        return loadCustomer(customerUserId).lastSeenAt();
    }

    public Instant getShopLastSeen(UUID shopId) {
        return loadShop(shopId).lastSeenAt();
    }

    public void touchCustomerPresence(UUID customerUserId) {
        if (customerUserId == null) return;
        try {
            jdbc.update("UPDATE customer_users SET last_seen_at = now() WHERE id = ?", customerUserId);
        } catch (DataAccessException e) {
            // customer_users not present in this datasource (e.g. H2 dev) —
            // presence pings are best-effort, swallow so the request still
            // returns 200.
            log.debug("touchCustomerPresence skipped: {}", e.getMessage());
        }
    }

    public void touchShopPresence(UUID shopId) {
        if (shopId == null) return;
        try {
            jdbc.update("UPDATE shops SET last_seen_at = now() WHERE id = ?", shopId);
        } catch (DataAccessException e) {
            log.debug("touchShopPresence skipped: {}", e.getMessage());
        }
    }

    /** Best-effort presence read; returns null if the table is unavailable. */
    private Instant safeLastSeen(String table, UUID id) {
        if (id == null) return null;
        try {
            return jdbc.queryForObject(
                "SELECT last_seen_at FROM " + table + " WHERE id = ?",
                (rs, i) -> rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null,
                id
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (DataAccessException e) {
            return null;
        }
    }
}
