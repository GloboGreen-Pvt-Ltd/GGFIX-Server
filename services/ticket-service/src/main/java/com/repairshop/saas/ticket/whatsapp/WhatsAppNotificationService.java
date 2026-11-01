package com.repairshop.saas.ticket.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * WhatsApp booking confirmation: outbox + sender.
 *
 * <ol>
 *   <li>{@link #enqueueBookingConfirmed} runs after POST /tickets has committed and
 *       inserts ONE row per booking (unique shop + booking number + event). It never
 *       throws — a booking must succeed even when WhatsApp cannot.</li>
 *   <li>{@link #runDue} (scheduled) claims due rows, renders the receipt image,
 *       uploads it to S3, sends the approved template through BotBee and records
 *       the result. Provider/network errors retry with exponential backoff.</li>
 *   <li>{@link #pollDeliveryStatus} (scheduled) asks BotBee message/status and moves
 *       SENT → DELIVERED → READ (or FAILED).</li>
 * </ol>
 */
@Service
public class WhatsAppNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);

    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    static final String NO_CONSENT = "Customer has not agreed to WhatsApp messages.";
    static final String NO_PHONE = "No valid WhatsApp number for the customer.";
    static final String TURNED_OFF = "WhatsApp sending is turned off.";
    private static final int MAX_STORED_RESPONSE = 2000;
    private static final long MAX_PHOTO_BYTES = 3L * 1024 * 1024;
    private static final Set<String> PHOTO_HOSTS = Set.of("media.ggfix.in", "res.cloudinary.com");

    private final WhatsAppNotificationRepository repo;
    private final BookingSnapshotLoader loader;
    private final BotBeeClient botBee;
    private final ReceiptMediaStore media;
    private final WhatsAppProperties props;
    private final ObjectMapper mapper;
    private final BookingReceiptRenderer renderer;
    private final HttpClient photoHttp = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();

    public WhatsAppNotificationService(WhatsAppNotificationRepository repo, BookingSnapshotLoader loader, BotBeeClient botBee,
                                       ReceiptMediaStore media, WhatsAppProperties props, ObjectMapper mapper) {
        this.repo = repo;
        this.loader = loader;
        this.botBee = botBee;
        this.media = media;
        this.props = props;
        this.mapper = mapper;
        this.renderer = new BookingReceiptRenderer(this::loadPhoto);
    }

    /** Partner App → Booking Details. status NONE = no row (e.g. a booking made before this feature). */
    public record StatusView(String bookingNumber, String status, String statusReason, String recipient, int attempts,
                             int resendCount, boolean canResend, boolean sendingEnabled,
                             Instant sentAt, Instant deliveredAt, Instant readAt, Instant updatedAt) {}

    // ── Enqueue ────────────────────────────────────────────────────────────────

    /** After the booking committed. Never throws; logs (masked) instead. */
    public void enqueueBookingConfirmed(UUID shopId, UUID ticketId, Boolean optIn) {
        try {
            WhatsAppNotificationRepository.TicketRef t = repo.findTicket(shopId, ticketId).orElse(null);
            if (t == null || t.bookingKey() == null) return;
            String phone = PhoneNumbers.toE164(t.customerPhone(), props.getDefaultCountryCode());
            String skip = skipReason(Boolean.TRUE.equals(optIn), phone);
            Instant next = skip == null ? Instant.now().plusSeconds(Math.max(0, props.getGatherSeconds())) : null;
            int inserted = repo.insertIfAbsent(shopId, t.bookingKey(), ticketId, BOOKING_CONFIRMED, props.getBookingTemplate(),
                    phone, Boolean.TRUE.equals(optIn), skip == null ? "PENDING" : "SKIPPED", skip, next,
                    skip == null ? "QUEUED" : "SKIPPED");
            if (inserted > 0) {
                log.info("WhatsApp booking confirmation {} for {} → {}", skip == null ? "queued" : "skipped (" + skip + ")",
                        t.bookingKey(), PhoneNumbers.mask(phone));
            }
        } catch (RuntimeException e) {
            log.warn("WhatsApp booking confirmation not queued for ticket {}: {}", ticketId, e.getMessage());
        }
    }

    /** null = send; otherwise why the row is recorded as SKIPPED. */
    String skipReason(boolean optIn, String phone) {
        if (!optIn) return NO_CONSENT;
        if (phone == null) return NO_PHONE;
        if (!props.isSendingOn()) return TURNED_OFF;
        return null;
    }

    // ── Read / resend (Partner App) ───────────────────────────────────────────

    public StatusView status(UUID shopId, UUID ticketId) {
        WhatsAppNotificationRepository.TicketRef t = repo.findTicket(shopId, ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found."));
        return view(t.bookingKey(), repo.findByBooking(shopId, t.bookingKey(), BOOKING_CONFIRMED).orElse(null));
    }

    /**
     * Owner send / resend (Booking Details card, Jobs → Receipt) — the owner
     * confirms the customer agreed. Allowed for any settled message (failed,
     * skipped, or already sent / delivered / read when the customer needs it
     * again) and when none exists yet; never while one is queued or sending,
     * and at most max-resends times per booking.
     */
    public StatusView resend(UUID shopId, UUID ticketId) {
        if (!props.isSendingOn()) throw new ResponseStatusException(HttpStatus.CONFLICT, TURNED_OFF);
        WhatsAppNotificationRepository.TicketRef t = repo.findTicket(shopId, ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found."));
        String phone = PhoneNumbers.toE164(t.customerPhone(), props.getDefaultCountryCode());
        if (phone == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, NO_PHONE);

        Optional<WhatsAppNotificationRepository.Row> row = repo.findByBooking(shopId, t.bookingKey(), BOOKING_CONFIRMED);
        if (row.isEmpty()) {
            repo.insertIfAbsent(shopId, t.bookingKey(), ticketId, BOOKING_CONFIRMED, props.getBookingTemplate(), phone, true,
                    "PENDING", null, Instant.now(), "QUEUED");
        } else {
            WhatsAppNotificationRepository.Row r = row.get();
            if (inFlight(r.status())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This confirmation is already being sent.");
            }
            if (r.resendCount() >= props.getMaxResends()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Resend limit reached for this booking.");
            }
            if (repo.requeue(r.id(), phone, "Resent by shop") == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This confirmation was updated meanwhile. Refresh and try again.");
            }
        }
        log.info("WhatsApp booking confirmation resend queued for {} → {}", t.bookingKey(), PhoneNumbers.mask(phone));
        return view(t.bookingKey(), repo.findByBooking(shopId, t.bookingKey(), BOOKING_CONFIRMED).orElse(null));
    }

    /** Queued or being sent right now — a resend then would only duplicate it. */
    static boolean inFlight(String status) {
        return "PENDING".equals(status) || "SENDING".equals(status);
    }

    StatusView view(String bookingNumber, WhatsAppNotificationRepository.Row r) {
        boolean on = props.isSendingOn();
        if (r == null) {
            return new StatusView(bookingNumber, "NONE", null, null, 0, 0, on, on, null, null, null, null);
        }
        boolean canResend = on && !inFlight(r.status()) && r.resendCount() < props.getMaxResends();
        return new StatusView(bookingNumber, r.status(), r.statusReason(), PhoneNumbers.mask(r.recipientPhone()), r.attempts(),
                r.resendCount(), canResend, on, r.sentAt(), r.deliveredAt(), r.readAt(), r.updatedAt());
    }

    // ── Worker ─────────────────────────────────────────────────────────────────

    public void runDue() {
        if (!props.isSendingOn()) return;
        repo.failStaleSending(10);
        for (UUID id : repo.claimDue(Math.max(1, props.getBatchSize()))) {
            try {
                process(id);
            } catch (RuntimeException e) {
                log.warn("WhatsApp notification {} failed unexpectedly: {}", id, e.getMessage());
                repo.markFailed(id, "Unexpected error while sending.", null, null);
            }
        }
    }

    void process(UUID id) {
        WhatsAppNotificationRepository.Row row = repo.findById(id).orElse(null);
        if (row == null || !"SENDING".equals(row.status())) return;
        ZoneId zone = ZoneId.of(props.getTimezone());

        BookingSnapshot snap = loader.load(row.shopId(), row.bookingNumber());
        if (snap == null) {
            repo.markFailed(id, "Booking not found.", null, null);
            return;
        }
        List<String> vars = BookingTemplateVariables.of(snap, zone);
        String varsJson = json(vars);

        String imageUrl = headerImage(row, snap, zone);
        if (imageUrl == null) {
            fail(row, true, "Booking image could not be created.", null, varsJson);
            return;
        }

        BotBeeClient.SendResult r = botBee.sendTemplate(row.recipientPhone(), row.templateName(), props.getLanguage(), vars, imageUrl);
        String stored = maskedResponse(r.body(), row.recipientPhone());
        if (r.ok()) {
            repo.markSent(id, r.messageId(), stored, varsJson);
            log.info("WhatsApp booking confirmation {} sent to {} (message {})", row.bookingNumber(),
                    PhoneNumbers.mask(row.recipientPhone()), r.messageId());
        } else {
            fail(row, r.retryable(), r.error(), stored, varsJson);
        }
    }

    /** Receipt URL (rendered + uploaded once per row), else the configured fallback image, else null. */
    private String headerImage(WhatsAppNotificationRepository.Row row, BookingSnapshot snap, ZoneId zone) {
        String fallback = props.getFallbackHeaderImageUrl() == null || props.getFallbackHeaderImageUrl().isBlank()
                ? null : props.getFallbackHeaderImageUrl();
        if (!media.isEnabled()) return fallback;
        try {
            String key = row.mediaKey();
            if (key == null) {
                key = media.put(row.shopId(), renderer.renderPng(snap, zone));
                repo.setMedia(row.id(), key);
            }
            return media.url(key);
        } catch (Exception | LinkageError | InternalError e) {
            // Missing fonts on the host surface as Errors from AWT, not exceptions.
            log.warn("WhatsApp receipt image failed for {}: {}", row.bookingNumber(), e.toString());
            return fallback;
        }
    }

    private void fail(WhatsAppNotificationRepository.Row row, boolean retryable, String reason, String response, String varsJson) {
        if (retryable && row.attempts() < props.getMaxAttempts()) {
            Instant next = Instant.now().plus(backoff(row.attempts()));
            repo.reschedule(row.id(), next, reason, response, varsJson);
            log.info("WhatsApp booking confirmation {} retry {} at {}: {}", row.bookingNumber(), row.attempts(), next, reason);
        } else {
            repo.markFailed(row.id(), reason, response, varsJson);
            log.warn("WhatsApp booking confirmation {} to {} failed: {}", row.bookingNumber(),
                    PhoneNumbers.mask(row.recipientPhone()), reason);
        }
    }

    /** 30 s, 1 m, 2 m, 4 m … capped at 30 minutes. */
    static Duration backoff(int attempt) {
        long seconds = 30L << Math.min(Math.max(attempt - 1, 0), 6);
        return Duration.ofSeconds(Math.min(seconds, 1800));
    }

    public void cleanupMedia() {
        if (!media.isEnabled()) return;
        for (WhatsAppNotificationRepository.MediaRef m : repo.mediaOlderThan(Math.max(1, props.getReceiptRetentionHours()), 50)) {
            if (media.deleteQuietly(m.key())) repo.markMediaDeleted(m.id());
        }
    }

    // ── Delivery status (polled: BotBee message/status) ───────────────────────

    /** Checks sent messages until read or the poll window ends; 20 per run stays far under BotBee's 120/min. */
    public void pollDeliveryStatus() {
        if (!props.isSendingOn()) return;
        for (WhatsAppNotificationRepository.PollRef p : repo.dueForStatusCheck(Math.max(1, props.getStatusPollHours()), 20)) {
            BotBeeClient.StatusResult r = botBee.fetchStatus(p.messageId());
            repo.markStatusChecked(p.id());
            String status = providerStatus(r.status());
            if (status != null) {
                repo.applyProviderStatus(p.messageId(), status, "FAILED".equals(status) ? "Not delivered by WhatsApp." : null);
            }
        }
    }

    /** BotBee / Meta status → ours; null = nothing new to record (sent, unknown). */
    static String providerStatus(String raw) {
        if (raw == null) return null;
        String s = raw.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("read")) return "READ";
        if (s.contains("undeliver") || s.contains("fail") || s.contains("error") || s.contains("reject")) return "FAILED";
        if (s.contains("deliver")) return "DELIVERED";
        return null;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Device photo for the receipt: https from our media hosts only, small, short timeout. Never throws. */
    BufferedImage loadPhoto(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !PHOTO_HOSTS.contains(uri.getHost().toLowerCase())) return null;
            HttpResponse<InputStream> res = photoHttp.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) return null;
            try (InputStream in = res.body()) {
                byte[] bytes = in.readNBytes((int) MAX_PHOTO_BYTES + 1);
                if (bytes.length > MAX_PHOTO_BYTES) return null;
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    static String maskedResponse(String body, String phone) {
        if (body == null) return null;
        String out = body;
        if (phone != null) {
            String digits = phone.replace("+", "");
            out = out.replace(digits, PhoneNumbers.mask(phone).replace("+", ""));
            if (digits.length() > 10) out = out.replace(digits.substring(digits.length() - 10), "******" + digits.substring(digits.length() - 4));
        }
        return trim(out, MAX_STORED_RESPONSE);
    }

    private String json(List<String> vars) {
        try {
            return mapper.writeValueAsString(vars);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
