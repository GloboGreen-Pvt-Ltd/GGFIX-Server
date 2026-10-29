package com.repairshop.saas.ticket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.ticket.dto.BookingDraftDtos.Detail;
import com.repairshop.saas.ticket.dto.BookingDraftDtos.PageResponse;
import com.repairshop.saas.ticket.dto.BookingDraftDtos.SaveResponse;
import com.repairshop.saas.ticket.dto.BookingDraftDtos.SubmitRequest;
import com.repairshop.saas.ticket.dto.BookingDraftDtos.Summary;
import com.repairshop.saas.ticket.dto.BookingDraftDtos.UpsertRequest;
import com.repairshop.saas.ticket.exception.BookingDraftException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Booking drafts of the owner app's New Booking wizard, stored in
 * {@code booking_drafts} through JdbcTemplate (deliberately no JPA entity —
 * see DatabaseSchemaInitializer). Every statement is scoped to the caller's
 * shop; a draft of another shop is indistinguishable from a missing one.
 *
 * <p>Concurrency: saves are optimistic. The version check and the write are
 * one {@code UPDATE ... WHERE version = ? AND status = 'ACTIVE'}, so of two
 * concurrent saves on the same base version exactly one wins.
 */
@Service
@Slf4j
public class BookingDraftService {

    public static final String ACTIVE = "ACTIVE";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String DISCARDED = "DISCARDED";
    private static final Set<String> STATUSES = Set.of(ACTIVE, SUBMITTED, DISCARDED);

    public static final int MAX_PAYLOAD_CHARS = 1_000_000;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_SUBMIT_TICKETS = 100;
    private static final String MASK = "•";

    private static final String SUMMARY_COLUMNS = "id, status, current_step, step_label, customer_name,"
            + " customer_mobile, device_summary, device_count, version, created_by, created_at, updated_at";

    private static final String DETAIL_COLUMNS = SUMMARY_COLUMNS + ", submitted_at, payload, ticket_ids";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BookingDraftService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Result of an upsert: the wire response plus whether a row was inserted (201) or updated (200). */
    public record Saved(SaveResponse response, boolean created) {}

    private record Fields(String currentStep, String stepLabel, String customerName, String customerMobile,
                          String deviceSummary, int deviceCount, String payloadJson) {}

    private record DraftState(String status, int version, Set<UUID> ticketIds) {}

    // ------------------------------------------------------------------ upsert

    public Saved upsert(UUID shopId, UUID userId, UUID id, UpsertRequest req) {
        Fields f = validate(req);
        Integer baseVersion = req.getBaseVersion();
        Instant now = now();

        if (baseVersion != null && baseVersion > 0) {
            int updated = jdbc.update(
                    "UPDATE booking_drafts SET current_step = ?, step_label = ?, customer_name = ?,"
                            + " customer_mobile = ?, device_summary = ?, device_count = ?, payload = ?,"
                            + " version = version + 1, updated_at = ?, updated_by = ?"
                            + " WHERE id = ? AND shop_id = ? AND version = ? AND status = 'ACTIVE'",
                    f.currentStep(), f.stepLabel(), f.customerName(), f.customerMobile(), f.deviceSummary(),
                    f.deviceCount(), f.payloadJson(), ts(now), userId, id, shopId, baseVersion);
            if (updated == 1) {
                return new Saved(new SaveResponse(id, baseVersion + 1, ACTIVE, now), false);
            }
        }

        DraftState existing = findState(shopId, id);
        if (existing != null) throw conflictFor(existing);
        if (baseVersion != null && baseVersion != 0) {
            // Client believes it is editing a saved draft the server doesn't have in this shop.
            if (existsInAnyShop(id)) throw BookingDraftException.notFound();
            throw BookingDraftException.versionConflict(0);
        }

        try {
            jdbc.update(
                    "INSERT INTO booking_drafts (id, shop_id, created_by, updated_by, status, current_step,"
                            + " step_label, customer_name, customer_mobile, device_summary, device_count,"
                            + " payload, version, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
                    id, shopId, userId, userId, f.currentStep(), f.stepLabel(), f.customerName(),
                    f.customerMobile(), f.deviceSummary(), f.deviceCount(), f.payloadJson(), ts(now), ts(now));
        } catch (DuplicateKeyException e) {
            // Lost a creation race, or the id belongs to another shop.
            DraftState raced = findState(shopId, id);
            if (raced == null) throw BookingDraftException.notFound();
            throw conflictFor(raced);
        }
        return new Saved(new SaveResponse(id, 1, ACTIVE, now), true);
    }

    private BookingDraftException conflictFor(DraftState state) {
        if (!ACTIVE.equals(state.status())) return BookingDraftException.closed(state.status());
        return BookingDraftException.versionConflict(state.version());
    }

    private Fields validate(UpsertRequest req) {
        if (req == null) throw BookingDraftException.badRequest("INVALID_BODY", "Request body is required.");
        JsonNode payload = req.getPayload();
        if (payload == null || !payload.isObject()) {
            throw BookingDraftException.badRequest("INVALID_PAYLOAD", "payload is required and must be a JSON object.");
        }
        String payloadJson = toJson(payload);
        if (payloadJson.length() > MAX_PAYLOAD_CHARS) {
            throw new BookingDraftException(HttpStatus.valueOf(413), "PAYLOAD_TOO_LARGE",
                    "payload is too large (max 1,000,000 characters).", Map.of("maxChars", MAX_PAYLOAD_CHARS));
        }
        int deviceCount = req.getDeviceCount() == null ? 0 : req.getDeviceCount();
        if (deviceCount < 0) {
            throw BookingDraftException.badRequest("INVALID_DEVICE_COUNT", "deviceCount must be 0 or more.");
        }
        return new Fields(clip(req.getCurrentStep(), 60), clip(req.getStepLabel(), 80),
                clip(req.getCustomerName(), 160), clip(req.getCustomerMobile(), 30),
                clip(req.getDeviceSummary(), 255), deviceCount, payloadJson);
    }

    // ------------------------------------------------------------------ reads

    public PageResponse list(UUID shopId, String status, int page, int size) {
        String st = normalizeStatus(status);
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM booking_drafts WHERE shop_id = ? AND status = ?", Long.class, shopId, st);
        long totalElements = total == null ? 0 : total;
        List<Summary> content = totalElements == 0 ? List.of() : jdbc.query(
                "SELECT " + SUMMARY_COLUMNS + " FROM booking_drafts WHERE shop_id = ? AND status = ?"
                        + " ORDER BY updated_at DESC, id LIMIT ? OFFSET ?",
                summaryMapper(), shopId, st, s, (long) p * s);
        return new PageResponse(content, p, s, totalElements);
    }

    public Detail get(UUID shopId, UUID id) {
        List<Detail> rows = jdbc.query(
                "SELECT " + DETAIL_COLUMNS + " FROM booking_drafts WHERE id = ? AND shop_id = ?",
                detailMapper(), id, shopId);
        if (rows.isEmpty()) throw BookingDraftException.notFound();
        return rows.get(0);
    }

    // ------------------------------------------------------------------ discard / submit

    /** ACTIVE → DISCARDED (row kept). Repeating it is a no-op; a submitted draft can't be discarded. */
    public void discard(UUID shopId, UUID userId, UUID id) {
        int updated = jdbc.update(
                "UPDATE booking_drafts SET status = 'DISCARDED', updated_at = ?, updated_by = ?"
                        + " WHERE id = ? AND shop_id = ? AND status = 'ACTIVE'",
                ts(now()), userId, id, shopId);
        if (updated == 1) return;
        DraftState state = findState(shopId, id);
        if (state == null) throw BookingDraftException.notFound();
        if (DISCARDED.equals(state.status())) return;
        throw BookingDraftException.closed(state.status());
    }

    /** ACTIVE → SUBMITTED with the minted ticket ids. Re-submitting the same ticket set returns the draft again. */
    public Detail submit(UUID shopId, UUID userId, UUID id, SubmitRequest req) {
        Set<UUID> ticketIds = validateTicketIds(req);
        DraftState state = findState(shopId, id);
        if (state == null) throw BookingDraftException.notFound();
        if (!ACTIVE.equals(state.status())) return alreadyClosed(shopId, id, state, ticketIds);

        verifyTicketsBelongToShop(shopId, ticketIds);
        Instant now = now();
        int updated = jdbc.update(
                "UPDATE booking_drafts SET status = 'SUBMITTED', ticket_ids = ?, submitted_at = ?,"
                        + " updated_at = ?, updated_by = ? WHERE id = ? AND shop_id = ? AND status = 'ACTIVE'",
                toJson(new ArrayList<>(ticketIds)), ts(now), ts(now), userId, id, shopId);
        if (updated == 0) {
            // Closed concurrently (another submit or a discard) between the read and the write.
            DraftState raced = findState(shopId, id);
            if (raced == null) throw BookingDraftException.notFound();
            return alreadyClosed(shopId, id, raced, ticketIds);
        }
        log.info("booking-draft {} submitted by {} in shop {} with {} ticket(s)", id, userId, shopId, ticketIds.size());
        return get(shopId, id);
    }

    private Detail alreadyClosed(UUID shopId, UUID id, DraftState state, Set<UUID> requested) {
        if (SUBMITTED.equals(state.status()) && requested.equals(state.ticketIds())) return get(shopId, id);
        throw BookingDraftException.closed(state.status());
    }

    private Set<UUID> validateTicketIds(SubmitRequest req) {
        List<UUID> raw = req == null ? null : req.getTicketIds();
        if (raw == null || raw.isEmpty()) {
            throw BookingDraftException.badRequest("INVALID_TICKET_IDS", "ticketIds must contain at least one ticket id.");
        }
        if (raw.contains(null)) {
            throw BookingDraftException.badRequest("INVALID_TICKET_IDS", "ticketIds must contain only valid ticket ids.");
        }
        Set<UUID> ids = new LinkedHashSet<>(raw);
        if (ids.size() > MAX_SUBMIT_TICKETS) {
            throw BookingDraftException.badRequest("INVALID_TICKET_IDS",
                    "ticketIds may contain at most " + MAX_SUBMIT_TICKETS + " ids.");
        }
        return ids;
    }

    private void verifyTicketsBelongToShop(UUID shopId, Set<UUID> ticketIds) {
        List<Object> args = new ArrayList<>(ticketIds.size() + 1);
        args.add(shopId);
        args.addAll(ticketIds);
        String placeholders = String.join(", ", Collections.nCopies(ticketIds.size(), "?"));
        List<UUID> found = jdbc.query(
                "SELECT id FROM tickets WHERE shop_id = ? AND id IN (" + placeholders + ")",
                (rs, i) -> rs.getObject(1, UUID.class), args.toArray());
        Set<UUID> missing = new LinkedHashSet<>(ticketIds);
        found.forEach(missing::remove);
        if (!missing.isEmpty()) {
            throw new BookingDraftException(HttpStatus.BAD_REQUEST, "INVALID_TICKET_IDS",
                    "Some tickets were not found in this shop.", Map.of("invalidTicketIds", List.copyOf(missing)));
        }
    }

    // ------------------------------------------------------------------ helpers

    private DraftState findState(UUID shopId, UUID id) {
        List<DraftState> rows = jdbc.query(
                "SELECT status, version, ticket_ids FROM booking_drafts WHERE id = ? AND shop_id = ?",
                (rs, i) -> new DraftState(rs.getString("status"), rs.getInt("version"),
                        new LinkedHashSet<>(parseTicketIds(rs.getString("ticket_ids")))),
                id, shopId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean existsInAnyShop(UUID id) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM booking_drafts WHERE id = ?", Long.class, id);
        return n != null && n > 0;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return ACTIVE;
        String st = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(st)) {
            throw BookingDraftException.badRequest("INVALID_STATUS", "status must be one of ACTIVE, SUBMITTED, DISCARDED.");
        }
        return st;
    }

    private RowMapper<Summary> summaryMapper() {
        return (rs, i) -> Summary.builder()
                .id(rs.getObject("id", UUID.class))
                .status(rs.getString("status"))
                .currentStep(rs.getString("current_step"))
                .stepLabel(rs.getString("step_label"))
                .customerName(rs.getString("customer_name"))
                .customerMobileMasked(maskMobile(rs.getString("customer_mobile")))
                .deviceSummary(rs.getString("device_summary"))
                .deviceCount(rs.getInt("device_count"))
                .version(rs.getInt("version"))
                .createdBy(rs.getObject("created_by", UUID.class))
                .createdAt(instant(rs, "created_at"))
                .updatedAt(instant(rs, "updated_at"))
                .build();
    }

    private RowMapper<Detail> detailMapper() {
        return (rs, i) -> {
            String mobile = rs.getString("customer_mobile");
            return Detail.builder()
                    .id(rs.getObject("id", UUID.class))
                    .status(rs.getString("status"))
                    .currentStep(rs.getString("current_step"))
                    .stepLabel(rs.getString("step_label"))
                    .customerName(rs.getString("customer_name"))
                    .customerMobileMasked(maskMobile(mobile))
                    .customerMobile(mobile)
                    .deviceSummary(rs.getString("device_summary"))
                    .deviceCount(rs.getInt("device_count"))
                    .version(rs.getInt("version"))
                    .createdBy(rs.getObject("created_by", UUID.class))
                    .createdAt(instant(rs, "created_at"))
                    .updatedAt(instant(rs, "updated_at"))
                    .submittedAt(instant(rs, "submitted_at"))
                    .payload(parsePayload(rs.getString("payload")))
                    .ticketIds(parseTicketIds(rs.getString("ticket_ids")))
                    .build();
        };
    }

    /** Every digit but the last four becomes '•' ("9876543210" → "••••••3210"); four or fewer digits are fully masked. */
    public static String maskMobile(String mobile) {
        if (mobile == null) return null;
        String digits = mobile.replaceAll("\\D", "");
        if (digits.isEmpty()) return null;
        if (digits.length() <= 4) return MASK.repeat(digits.length());
        return MASK.repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    /** Trim; blank → null; cut to the column length without splitting a surrogate pair. */
    static String clip(String value, int max) {
        if (value == null) return null;
        String s = value.strip();
        if (s.isEmpty()) return null;
        if (s.length() > max) {
            int end = Character.isHighSurrogate(s.charAt(max - 1)) ? max - 1 : max;
            s = s.substring(0, end).strip();
        }
        return s.isEmpty() ? null : s;
    }

    private JsonNode parsePayload(String json) {
        try {
            return json == null ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored draft payload is not valid JSON");
        }
    }

    private List<UUID> parseTicketIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<UUID>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored draft ticket ids are not valid JSON");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw BookingDraftException.badRequest("INVALID_PAYLOAD", "payload could not be serialised.");
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static OffsetDateTime ts(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
