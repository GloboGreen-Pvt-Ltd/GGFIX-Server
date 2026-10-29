package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.BookingDraftDtos.Detail;
import com.repairshop.saas.ticket.dto.BookingDraftDtos.PageResponse;
import com.repairshop.saas.ticket.dto.BookingDraftDtos.SaveResponse;
import com.repairshop.saas.ticket.dto.BookingDraftDtos.SubmitRequest;
import com.repairshop.saas.ticket.dto.BookingDraftDtos.UpsertRequest;
import com.repairshop.saas.ticket.exception.BookingDraftException;
import com.repairshop.saas.ticket.service.BookingDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side drafts of the owner app's New Booking wizard
 * (public path /ticket/booking-drafts). Owner-side callers only
 * (SHOP_OWNER / SUPER_ADMIN); everything is scoped to the JWT's shop.
 *
 * <p>Errors are rendered here as {@code { status, error, message, code, ... }}
 * and never carry SQL or stack traces. If the booking_drafts table is missing
 * or the database is unreachable the endpoints answer 503
 * "Drafts are temporarily unavailable." instead of a 500.
 */
@RestController
@RequestMapping("/booking-drafts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Booking Drafts", description = "Resumable New Booking wizard drafts (owner only)")
@SecurityRequirement(name = "Bearer")
public class BookingDraftController {

    public static final String UNAVAILABLE_MESSAGE = "Drafts are temporarily unavailable.";
    private static final Set<String> OWNER_ROLES = Set.of("SHOP_OWNER", "SUPER_ADMIN");
    /** Postgres undefined_table / undefined_column / insufficient_privilege; ANSI (H2) table / column not found. */
    private static final Set<String> UNAVAILABLE_SQL_STATES = Set.of("42P01", "42703", "42501", "42S02", "42S22");

    private final BookingDraftService service;

    @PutMapping("/{id}")
    @Operation(summary = "Create (201) or update (200) a draft. Optimistic: baseVersion must equal the stored version")
    public ResponseEntity<SaveResponse> upsert(@PathVariable UUID id,
                                               @RequestBody(required = false) UpsertRequest body,
                                               HttpServletRequest request) {
        Caller caller = caller(request);
        BookingDraftService.Saved saved = service.upsert(caller.shopId(), caller.userId(), id, body);
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK).body(saved.response());
    }

    @GetMapping
    @Operation(summary = "List the shop's drafts, newest first (no payload, masked mobile)")
    public PageResponse list(@RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             HttpServletRequest request) {
        Caller caller = caller(request);
        return service.list(caller.shopId(), status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a draft with its full payload")
    public Detail get(@PathVariable UUID id, HttpServletRequest request) {
        return service.get(caller(request).shopId(), id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Discard a draft (soft delete; idempotent)")
    public ResponseEntity<Void> discard(@PathVariable UUID id, HttpServletRequest request) {
        Caller caller = caller(request);
        service.discard(caller.shopId(), caller.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Mark a draft submitted with the tickets it produced (idempotent for the same ticket set)")
    public Detail submit(@PathVariable UUID id,
                         @RequestBody(required = false) SubmitRequest body,
                         HttpServletRequest request) {
        Caller caller = caller(request);
        return service.submit(caller.shopId(), caller.userId(), id, body);
    }

    // ------------------------------------------------------------------ auth

    private record Caller(UUID shopId, UUID userId) {}

    private Caller caller(HttpServletRequest request) {
        if (!hasOwnerRole(request)) {
            throw new BookingDraftException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Only the shop owner can use booking drafts.");
        }
        UUID userId = uuidAttribute(request, "userId");
        if (userId == null) {
            throw new BookingDraftException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Missing or invalid Authorization token.");
        }
        UUID shopId = uuidAttribute(request, "shopId");
        if (shopId == null) {
            throw new BookingDraftException(HttpStatus.FORBIDDEN, "SHOP_CONTEXT_REQUIRED",
                    "A shop context is required to use booking drafts.");
        }
        return new Caller(shopId, userId);
    }

    private static boolean hasOwnerRole(HttpServletRequest request) {
        return request.getAttribute("roles") instanceof List<?> roles
                && roles.stream().anyMatch(r -> OWNER_ROLES.contains(String.valueOf(r).trim().toUpperCase(Locale.ROOT)));
    }

    private static UUID uuidAttribute(HttpServletRequest request, String name) {
        Object raw = request.getAttribute(name);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ errors

    @ExceptionHandler(BookingDraftException.class)
    public ResponseEntity<Map<String, Object>> handleDraft(BookingDraftException ex) {
        return error(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex, HttpServletRequest req) {
        if (isStoreUnavailable(ex)) {
            log.warn("booking-drafts unavailable for {} {}: {}", req.getMethod(), req.getRequestURI(),
                    ex.getMostSpecificCause().getMessage());
            return error(HttpStatus.SERVICE_UNAVAILABLE, "DRAFTS_UNAVAILABLE", UNAVAILABLE_MESSAGE, Map.of());
        }
        log.error("booking-drafts database error for {} {}", req.getMethod(), req.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Could not process the draft. Please try again.", Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_BODY",
                "Request body is not valid JSON for this endpoint (check field types and UUIDs).", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid value for '" + ex.getName() + "'.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse er && er.getStatusCode().value() < 500) {
            HttpStatusCode code = er.getStatusCode();
            HttpStatus status = HttpStatus.resolve(code.value());
            return error(status != null ? status : HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    status != null ? status.getReasonPhrase() : "Invalid request", Map.of());
        }
        log.error("booking-drafts unexpected error for {} {}", req.getMethod(), req.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Could not process the draft. Please try again.", Map.of());
    }

    static boolean isStoreUnavailable(Throwable ex) {
        if (ex instanceof DataAccessResourceFailureException || ex instanceof TransientDataAccessResourceException) {
            return true;
        }
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException sql) {
                if (sql.getSQLState() != null && UNAVAILABLE_SQL_STATES.contains(sql.getSQLState())) return true;
                int h2Code = sql.getErrorCode();
                if ((h2Code >= 42102 && h2Code <= 42104) || h2Code == 42122) return true; // H2 table/column not found
            }
        }
        return false;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message,
                                                             Map<String, Object> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (code != null) body.put("code", code);
        body.putAll(details);
        return ResponseEntity.status(status).body(body);
    }
}
