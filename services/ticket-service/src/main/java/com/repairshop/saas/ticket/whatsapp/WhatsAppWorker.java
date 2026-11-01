package com.repairshop.saas.ticket.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the WhatsApp outbox. Does nothing while sending is off. */
@Component
public class WhatsAppWorker {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWorker.class);

    private final WhatsAppNotificationService service;

    public WhatsAppWorker(WhatsAppNotificationService service) {
        this.service = service;
    }

    @Scheduled(initialDelayString = "${app.whatsapp.initial-delay-ms:30000}", fixedDelayString = "${app.whatsapp.poll-ms:15000}")
    public void sendDue() {
        try {
            service.runDue();
        } catch (RuntimeException e) {
            log.warn("WhatsApp outbox run failed: {}", e.getMessage());
        }
    }

    /** Delivery / read status of sent confirmations (BotBee message/status). */
    @Scheduled(initialDelayString = "${app.whatsapp.initial-delay-ms:30000}", fixedDelayString = "${app.whatsapp.status-poll-ms:120000}")
    public void pollStatus() {
        try {
            service.pollDeliveryStatus();
        } catch (RuntimeException e) {
            log.warn("WhatsApp status poll failed: {}", e.getMessage());
        }
    }

    /** Receipt images are customer data: delete them once WhatsApp has fetched them. */
    @Scheduled(initialDelayString = "${app.whatsapp.initial-delay-ms:30000}", fixedDelayString = "${app.whatsapp.cleanup-ms:900000}")
    public void cleanup() {
        try {
            service.cleanupMedia();
        } catch (RuntimeException e) {
            log.debug("WhatsApp receipt cleanup skipped: {}", e.getMessage());
        }
    }
}
