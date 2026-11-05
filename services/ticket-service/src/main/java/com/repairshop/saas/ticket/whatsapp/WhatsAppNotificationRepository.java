package com.repairshop.saas.ticket.whatsapp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * whatsapp_notifications (migration 108) over plain JDBC — deliberately no JPA
 * entity, so ticket-service still starts (ddl-auto: validate) before the
 * migration is applied. Every state change appends to history_json in SQL.
 */
@Repository
public class WhatsAppNotificationRepository {

    /** Appends {at, action, note} to history_json; binds (action, note). */
    private static final String HISTORY = "history_json = (COALESCE(NULLIF(history_json, ''), '[]')::jsonb"
            + " || jsonb_build_array(jsonb_build_object('at', now(), 'action', CAST(? AS TEXT), 'note', CAST(? AS TEXT))))::text";

    private static final String COLS = "id, shop_id, booking_number, ticket_id, event_type, template_name, recipient_phone, "
            + "opt_in, status, status_reason, attempts, media_key, provider_message_id, resend_count, "
            + "sent_at, delivered_at, read_at, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public WhatsAppNotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TicketRef(UUID id, String bookingNumber, String trackingId, String customerPhone) {
        /** GTS-001 for every device of a booking; older tickets without one fall back to their tracking id. */
        public String bookingKey() {
            return bookingNumber != null && !bookingNumber.isBlank() ? bookingNumber : trackingId;
        }
    }

    public record Row(UUID id, UUID shopId, String bookingNumber, UUID ticketId, String eventType, String templateName,
                      String recipientPhone, boolean optIn, String status, String statusReason, int attempts,
                      String mediaKey, String providerMessageId, int resendCount,
                      Instant sentAt, Instant deliveredAt, Instant readAt, Instant createdAt, Instant updatedAt) {}

    private static final RowMapper<Row> ROW = (rs, i) -> new Row(
            rs.getObject("id", UUID.class), rs.getObject("shop_id", UUID.class), rs.getString("booking_number"),
            rs.getObject("ticket_id", UUID.class), rs.getString("event_type"), rs.getString("template_name"),
            rs.getString("recipient_phone"), rs.getBoolean("opt_in"), rs.getString("status"), rs.getString("status_reason"),
            rs.getInt("attempts"), rs.getString("media_key"), rs.getString("provider_message_id"), rs.getInt("resend_count"),
            instant(rs.getTimestamp("sent_at")), instant(rs.getTimestamp("delivered_at")), instant(rs.getTimestamp("read_at")),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));

    public Optional<TicketRef> findTicket(UUID shopId, UUID ticketId) {
        return jdbc.query("SELECT id, booking_number, tracking_id, customer_phone FROM tickets WHERE shop_id = ? AND id = ?",
                (rs, i) -> new TicketRef(rs.getObject("id", UUID.class), rs.getString("booking_number"),
                        rs.getString("tracking_id"), rs.getString("customer_phone")),
                shopId, ticketId).stream().findFirst();
    }

    /** 1 when inserted, 0 when this booking event already has a row (idempotency). */
    public int insertIfAbsent(UUID shopId, String bookingNumber, UUID ticketId, String eventType, String template,
                              String phone, boolean optIn, String status, String reason, Instant nextAttemptAt, String action) {
        return jdbc.update("INSERT INTO whatsapp_notifications (shop_id, booking_number, ticket_id, event_type, template_name, "
                        + "recipient_phone, opt_in, status, status_reason, next_attempt_at, history_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, jsonb_build_array(jsonb_build_object('at', now(), "
                        + "'action', CAST(? AS TEXT), 'note', CAST(? AS TEXT)))::text) "
                        + "ON CONFLICT (shop_id, booking_number, event_type) DO NOTHING",
                shopId, bookingNumber, ticketId, eventType, template, phone, optIn, status, reason, ts(nextAttemptAt),
                action, reason);
    }

    public Optional<Row> findByBooking(UUID shopId, String bookingNumber, String eventType) {
        return jdbc.query("SELECT " + COLS + " FROM whatsapp_notifications WHERE shop_id = ? AND booking_number = ? AND event_type = ?",
                ROW, shopId, bookingNumber, eventType).stream().findFirst();
    }

    public Optional<Row> findById(UUID id) {
        return jdbc.query("SELECT " + COLS + " FROM whatsapp_notifications WHERE id = ?", ROW, id).stream().findFirst();
    }

    /** Moves due PENDING rows to SENDING (attempts + 1) and returns their ids. Safe with several instances. */
    public List<UUID> claimDue(int limit) {
        return jdbc.query("UPDATE whatsapp_notifications SET status = 'SENDING', attempts = attempts + 1, updated_at = now() "
                        + "WHERE id IN (SELECT id FROM whatsapp_notifications WHERE status = 'PENDING' AND next_attempt_at <= now() "
                        + "ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED) RETURNING id",
                (rs, i) -> rs.getObject("id", UUID.class), limit);
    }

    /**
     * A row left in SENDING (process restarted mid-send) is FAILED, never re-sent
     * automatically: BotBee may already have delivered it. The shop can Resend.
     */
    public int failStaleSending(int olderThanMinutes) {
        String note = "Sending was interrupted. Use Resend if the customer did not receive it.";
        return jdbc.update("UPDATE whatsapp_notifications SET status = 'FAILED', status_reason = ?, updated_at = now(), " + HISTORY
                        + " WHERE status = 'SENDING' AND updated_at < now() - make_interval(mins => ?)",
                note, "FAILED", note, olderThanMinutes);
    }

    public void setMedia(UUID id, String key) {
        jdbc.update("UPDATE whatsapp_notifications SET media_key = ?, updated_at = now() WHERE id = ?", key, id);
    }

    public void markSent(UUID id, String messageId, String providerResponse, String variablesJson) {
        jdbc.update("UPDATE whatsapp_notifications SET status = 'SENT', status_reason = NULL, provider_message_id = ?, "
                        + "provider_response = ?, variables_json = ?, sent_at = now(), next_attempt_at = NULL, updated_at = now(), "
                        + HISTORY + " WHERE id = ?",
                messageId, providerResponse, variablesJson, "SENT", messageId, id);
    }

    public void reschedule(UUID id, Instant next, String reason, String providerResponse, String variablesJson) {
        jdbc.update("UPDATE whatsapp_notifications SET status = 'PENDING', status_reason = ?, provider_response = ?, "
                        + "variables_json = COALESCE(?, variables_json), next_attempt_at = ?, updated_at = now(), "
                        + HISTORY + " WHERE id = ?",
                reason, providerResponse, variablesJson, ts(next), "RETRY", reason, id);
    }

    public void markFailed(UUID id, String reason, String providerResponse, String variablesJson) {
        jdbc.update("UPDATE whatsapp_notifications SET status = 'FAILED', status_reason = ?, "
                        + "provider_response = COALESCE(?, provider_response), variables_json = COALESCE(?, variables_json), "
                        + "next_attempt_at = NULL, updated_at = now(), " + HISTORY + " WHERE id = ?",
                reason, providerResponse, variablesJson, "FAILED", reason, id);
    }

    /** Owner resend of a settled message (not PENDING / SENDING). Returns 0 when the row moved on meanwhile. */
    public int requeue(UUID id, String phone, String note) {
        return jdbc.update("UPDATE whatsapp_notifications SET status = 'PENDING', status_reason = NULL, recipient_phone = ?, "
                        + "opt_in = true, attempts = 0, resend_count = resend_count + 1, next_attempt_at = now(), "
                        + "provider_message_id = NULL, sent_at = NULL, delivered_at = NULL, read_at = NULL, "
                        + "updated_at = now(), " + HISTORY
                        + " WHERE id = ? AND status IN ('FAILED', 'SKIPPED', 'SENT', 'DELIVERED', 'READ')",
                phone, "RESEND", note, id);
    }

    public record PollRef(UUID id, String messageId) {}

    /** SENT / DELIVERED rows sent within the window and not checked in the last two minutes. */
    public List<PollRef> dueForStatusCheck(int withinHours, int limit) {
        return jdbc.query("SELECT id, provider_message_id FROM whatsapp_notifications "
                        + "WHERE status IN ('SENT', 'DELIVERED') AND provider_message_id IS NOT NULL "
                        + "AND sent_at > now() - make_interval(hours => ?) "
                        + "AND (status_checked_at IS NULL OR status_checked_at < now() - interval '2 minutes') "
                        + "ORDER BY status_checked_at NULLS FIRST LIMIT ?",
                (rs, i) -> new PollRef(rs.getObject("id", UUID.class), rs.getString("provider_message_id")), withinHours, limit);
    }

    public void markStatusChecked(UUID id) {
        jdbc.update("UPDATE whatsapp_notifications SET status_checked_at = now() WHERE id = ?", id);
    }

    /** Provider status only moves forward: SENT → DELIVERED → READ, or SENT/DELIVERED → FAILED. */
    public int applyProviderStatus(String messageId, String status, String reason) {
        return jdbc.update("UPDATE whatsapp_notifications SET status = ?, "
                        + "delivered_at = CASE WHEN ? IN ('DELIVERED', 'READ') THEN COALESCE(delivered_at, now()) ELSE delivered_at END, "
                        + "read_at = CASE WHEN ? = 'READ' THEN COALESCE(read_at, now()) ELSE read_at END, "
                        + "status_reason = CASE WHEN ? = 'FAILED' THEN ? ELSE status_reason END, updated_at = now(), "
                        + HISTORY
                        + " WHERE provider_message_id = ? AND ("
                        + "(? = 'DELIVERED' AND status = 'SENT') OR "
                        + "(? = 'READ' AND status IN ('SENT', 'DELIVERED')) OR "
                        + "(? = 'FAILED' AND status IN ('SENT', 'DELIVERED')))",
                status, status, status, status, reason, status, reason, messageId, status, status, status);
    }

    public record MediaRef(UUID id, String key) {}

    public List<MediaRef> mediaOlderThan(int hours, int limit) {
        return jdbc.query("SELECT id, media_key FROM whatsapp_notifications WHERE media_key IS NOT NULL AND media_deleted_at IS NULL "
                        + "AND created_at < now() - make_interval(hours => ?) AND status NOT IN ('PENDING', 'SENDING') "
                        + "ORDER BY created_at LIMIT ?",
                (rs, i) -> new MediaRef(rs.getObject("id", UUID.class), rs.getString("media_key")), hours, limit);
    }

    public void markMediaDeleted(UUID id) {
        jdbc.update("UPDATE whatsapp_notifications SET media_deleted_at = now(), media_key = NULL WHERE id = ?", id);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }
}
