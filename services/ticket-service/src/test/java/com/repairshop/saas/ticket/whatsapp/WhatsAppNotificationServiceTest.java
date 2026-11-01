package com.repairshop.saas.ticket.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Outbox rules: consent, phone, duplicates, retries, resend, delivery callbacks. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppNotificationServiceTest {

    @Mock WhatsAppNotificationRepository repo;
    @Mock BookingSnapshotLoader loader;
    @Mock BotBeeClient botBee;
    @Mock ReceiptMediaStore media;

    private final WhatsAppProperties props = new WhatsAppProperties();
    private WhatsAppNotificationService service;
    private final UUID shopId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private final UUID rowId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        props.setEnabled(true);
        props.setApiKey("k");
        service = new WhatsAppNotificationService(repo, loader, botBee, media, props, new ObjectMapper());
        when(repo.findTicket(shopId, ticketId)).thenReturn(Optional.of(
                new WhatsAppNotificationRepository.TicketRef(ticketId, "GTS-014", "GTS-014", "98765 43210")));
        when(repo.insertIfAbsent(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any())).thenReturn(1);
    }

    private WhatsAppNotificationRepository.Row row(String status, int attempts, int resends) {
        return new WhatsAppNotificationRepository.Row(rowId, shopId, "GTS-014", ticketId, "BOOKING_CONFIRMED",
                "neargfix_booking_confirmed", "+919876543210", true, status, null, attempts, null, null, resends,
                null, null, null, Instant.now(), Instant.now());
    }

    private void stubSending(BotBeeClient.SendResult result) throws Exception {
        when(repo.findById(rowId)).thenReturn(Optional.of(row("SENDING", 1, 0)));
        when(loader.load(shopId, "GTS-014")).thenReturn(WhatsAppFormattingTest.booking(List.of(
                WhatsAppFormattingTest.device("GTS-014", "Galaxy S23", "Display", "6000", "1000", "5000", null))));
        when(media.isEnabled()).thenReturn(true);
        when(media.put(eq(shopId), any())).thenReturn("wa-receipts/s/x.png");
        when(media.url("wa-receipts/s/x.png")).thenReturn("https://signed/x.png");
        when(botBee.sendTemplate(any(), any(), any(), any(), any())).thenReturn(result);
    }

    // ── enqueue ──

    @Test
    void queuesOneBookingConfirmationWithE164PhoneAfterGatherDelay() {
        service.enqueueBookingConfirmed(shopId, ticketId, true);
        verify(repo).insertIfAbsent(eq(shopId), eq("GTS-014"), eq(ticketId), eq("BOOKING_CONFIRMED"), eq("neargfix_booking_confirmed"),
                eq("+919876543210"), eq(true), eq("PENDING"), isNull(), argThat(t -> t != null && t.isAfter(Instant.now().plusSeconds(30))), eq("QUEUED"));
    }

    @Test
    void withoutConsentRecordsSkippedAndNeverSends() {
        service.enqueueBookingConfirmed(shopId, ticketId, null);
        verify(repo).insertIfAbsent(any(), any(), any(), any(), any(), any(), eq(false), eq("SKIPPED"),
                eq(WhatsAppNotificationService.NO_CONSENT), isNull(), eq("SKIPPED"));
    }

    @Test
    void missingPhoneIsSkipped() {
        when(repo.findTicket(shopId, ticketId)).thenReturn(Optional.of(
                new WhatsAppNotificationRepository.TicketRef(ticketId, "GTS-014", "GTS-014", null)));
        service.enqueueBookingConfirmed(shopId, ticketId, true);
        verify(repo).insertIfAbsent(any(), any(), any(), any(), any(), isNull(), eq(true), eq("SKIPPED"),
                eq(WhatsAppNotificationService.NO_PHONE), isNull(), eq("SKIPPED"));
    }

    @Test
    void sendingOffIsSkipped() {
        props.setEnabled(false);
        service.enqueueBookingConfirmed(shopId, ticketId, true);
        verify(repo).insertIfAbsent(any(), any(), any(), any(), any(), any(), eq(true), eq("SKIPPED"),
                eq(WhatsAppNotificationService.TURNED_OFF), isNull(), eq("SKIPPED"));
    }

    @Test
    void duplicateBookingEventIsIgnoredQuietly() {
        when(repo.insertIfAbsent(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any())).thenReturn(0);
        assertDoesNotThrow(() -> service.enqueueBookingConfirmed(shopId, ticketId, true));
    }

    @Test
    void enqueueFailureNeverBreaksTheBooking() {
        when(repo.findTicket(any(), any())).thenThrow(new org.springframework.jdbc.BadSqlGrammarException("x", "sql",
                new java.sql.SQLException("relation \"whatsapp_notifications\" does not exist")));
        assertDoesNotThrow(() -> service.enqueueBookingConfirmed(shopId, ticketId, true));
    }

    // ── worker ──

    @Test
    void sendsWithReceiptImageAndRecordsMessageId() throws Exception {
        stubSending(new BotBeeClient.SendResult(true, false, 200, "wamid.1", "{\"to\":\"919876543210\"}", null));
        service.process(rowId);
        verify(repo).setMedia(rowId, "wa-receipts/s/x.png");
        verify(botBee).sendTemplate(eq("+919876543210"), eq("neargfix_booking_confirmed"), eq("en"),
                argThat(v -> v.size() == 8 && v.get(2).equals("GTS-014") && v.get(1).equals("Sri Mobile Care")), eq("https://signed/x.png"));
        verify(repo).markSent(eq(rowId), eq("wamid.1"), argThat(s -> !s.contains("9876543210")), contains("GTS-014"));
    }

    @Test
    void providerFailureRetriesWithBackoff() throws Exception {
        stubSending(new BotBeeClient.SendResult(false, true, 503, null, null, "WhatsApp provider returned 503"));
        service.process(rowId);
        verify(repo).reschedule(eq(rowId), argThat(t -> t.isAfter(Instant.now().plusSeconds(20))), eq("WhatsApp provider returned 503"), any(), any());
        verify(repo, never()).markSent(any(), any(), any(), any());
    }

    @Test
    void finalProviderErrorFails() throws Exception {
        stubSending(new BotBeeClient.SendResult(false, false, 403, null, null, "WhatsApp provider returned 403: API access not included in your plan"));
        service.process(rowId);
        verify(repo).markFailed(eq(rowId), contains("API access"), any(), any());
    }

    @Test
    void retriesStopAtMaxAttempts() throws Exception {
        stubSending(new BotBeeClient.SendResult(false, true, 500, null, null, "boom"));
        when(repo.findById(rowId)).thenReturn(Optional.of(row("SENDING", props.getMaxAttempts(), 0)));
        service.process(rowId);
        verify(repo).markFailed(eq(rowId), eq("boom"), any(), any());
        verify(repo, never()).reschedule(any(), any(), any(), any(), any());
    }

    @Test
    void backoffDoublesAndCaps() {
        assertEquals(Duration.ofSeconds(30), WhatsAppNotificationService.backoff(1));
        assertEquals(Duration.ofSeconds(60), WhatsAppNotificationService.backoff(2));
        assertEquals(Duration.ofSeconds(1800), WhatsAppNotificationService.backoff(20));
    }

    @Test
    void workerDoesNothingWhileSendingIsOff() {
        props.setEnabled(false);
        service.runDue();
        verifyNoInteractions(botBee);
        verify(repo, never()).claimDue(anyInt());
    }

    // ── resend ──

    @Test
    void resendRequeuesFailedMessage() {
        when(repo.findByBooking(shopId, "GTS-014", "BOOKING_CONFIRMED")).thenReturn(Optional.of(row("FAILED", 6, 0)));
        when(repo.requeue(rowId, "+919876543210", "Resent by shop")).thenReturn(1);
        service.resend(shopId, ticketId);
        verify(repo).requeue(rowId, "+919876543210", "Resent by shop");
    }

    @Test
    void resendRefusesSentMessageSoNoDuplicate() {
        when(repo.findByBooking(shopId, "GTS-014", "BOOKING_CONFIRMED")).thenReturn(Optional.of(row("SENT", 1, 0)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.resend(shopId, ticketId));
        assertEquals(409, e.getStatusCode().value());
        verify(repo, never()).requeue(any(), any(), any());
    }

    @Test
    void resendLimit() {
        when(repo.findByBooking(shopId, "GTS-014", "BOOKING_CONFIRMED")).thenReturn(Optional.of(row("FAILED", 1, props.getMaxResends())));
        assertThrows(ResponseStatusException.class, () -> service.resend(shopId, ticketId));
    }

    @Test
    void resendRefusedWhileSendingOff() {
        props.setEnabled(false);
        assertThrows(ResponseStatusException.class, () -> service.resend(shopId, ticketId));
    }

    @Test
    void statusMasksPhoneAndOffersResendOnlyAfterFailure() {
        when(repo.findByBooking(shopId, "GTS-014", "BOOKING_CONFIRMED")).thenReturn(Optional.of(row("FAILED", 6, 0)));
        WhatsAppNotificationService.StatusView v = service.status(shopId, ticketId);
        assertEquals("+91******3210", v.recipient());
        assertTrue(v.canResend());
        when(repo.findByBooking(shopId, "GTS-014", "BOOKING_CONFIRMED")).thenReturn(Optional.of(row("DELIVERED", 1, 0)));
        assertFalse(service.status(shopId, ticketId).canResend());
    }

    // ── delivery status polling ──

    @Test
    void pollsSentMessagesAndRecordsDeliveryAndRead() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        when(repo.dueForStatusCheck(anyInt(), anyInt())).thenReturn(List.of(
                new WhatsAppNotificationRepository.PollRef(a, "wamid.a"),
                new WhatsAppNotificationRepository.PollRef(b, "wamid.b"),
                new WhatsAppNotificationRepository.PollRef(c, "wamid.c")));
        when(botBee.fetchStatus("wamid.a")).thenReturn(new BotBeeClient.StatusResult("delivered", null));
        when(botBee.fetchStatus("wamid.b")).thenReturn(new BotBeeClient.StatusResult("read", null));
        when(botBee.fetchStatus("wamid.c")).thenReturn(new BotBeeClient.StatusResult("sent", null));
        service.pollDeliveryStatus();
        verify(repo).applyProviderStatus("wamid.a", "DELIVERED", null);
        verify(repo).applyProviderStatus("wamid.b", "READ", null);
        verify(repo, never()).applyProviderStatus(eq("wamid.c"), any(), any());
        verify(repo, times(3)).markStatusChecked(any());
    }

    @Test
    void failedDeliveryIsRecordedWithReason() {
        when(repo.dueForStatusCheck(anyInt(), anyInt())).thenReturn(List.of(new WhatsAppNotificationRepository.PollRef(rowId, "wamid.f")));
        when(botBee.fetchStatus("wamid.f")).thenReturn(new BotBeeClient.StatusResult("failed", null));
        service.pollDeliveryStatus();
        verify(repo).applyProviderStatus("wamid.f", "FAILED", "Not delivered by WhatsApp.");
    }

    @Test
    void noPollingWhileSendingOff() {
        props.setEnabled(false);
        service.pollDeliveryStatus();
        verifyNoInteractions(botBee);
    }

    @Test
    void mapsProviderStatuses() {
        assertEquals("DELIVERED", WhatsAppNotificationService.providerStatus("delivered"));
        assertEquals("READ", WhatsAppNotificationService.providerStatus("READ"));
        assertEquals("FAILED", WhatsAppNotificationService.providerStatus("undelivered"));
        assertNull(WhatsAppNotificationService.providerStatus("sent"));
        assertNull(WhatsAppNotificationService.providerStatus(null));
    }
}
