package com.repairshop.saas.subscription.invoice;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs invoice issuance + the first WhatsApp send AFTER the activation
 * transaction commits, on a small background pool — so verify / webhook
 * responses never wait for (or fail because of) a PDF or BotBee. A scheduled
 * sweep issues any invoice that is still missing (crash, migration applied
 * late, DB hiccup) and recovers WhatsApp sends that never started.
 */
@Slf4j
@Component
public class InvoiceIssuer {

    private final InvoiceService invoices;
    private final boolean sweepEnabled;
    private final int lookbackDays;
    private final ExecutorService executor;
    private volatile boolean sweepFailing;

    public InvoiceIssuer(InvoiceService invoices,
                         @Value("${app.invoice.sweep.enabled:true}") boolean sweepEnabled,
                         @Value("${app.invoice.sweep.lookback-days:30}") int lookbackDays) {
        this.invoices = invoices;
        this.sweepEnabled = sweepEnabled;
        this.lookbackDays = lookbackDays;
        AtomicInteger n = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "invoice-issuer-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** fallbackExecution: also fires when published outside a transaction. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentActivated(PaymentActivatedEvent event) {
        try {
            executor.execute(() -> issueAndDeliver(event.paymentId()));
        } catch (RejectedExecutionException e) {
            log.warn("Invoice for payment {} queued for the sweep: {}", event.paymentId(), e.toString());
        }
    }

    public void issueAndDeliver(UUID paymentId) {
        try {
            invoices.issueForPayment(paymentId).ifPresent(inv -> invoices.deliverWhatsApp(inv.id()));
        } catch (Exception e) {
            log.warn("Invoice for payment {} not issued yet (the sweep will retry): {}", paymentId, e.toString());
        }
    }

    @Scheduled(initialDelayString = "${app.invoice.sweep.initial-delay-ms:90000}",
            fixedDelayString = "${app.invoice.sweep.interval-ms:300000}")
    public void sweep() {
        if (!sweepEnabled) return;
        try {
            List<UUID> missing = invoices.paymentsMissingInvoice(lookbackDays);
            if (!missing.isEmpty()) log.info("Invoice sweep: issuing {} missing invoice(s)", missing.size());
            for (UUID paymentId : missing) issueAndDeliver(paymentId);
            invoices.recoverWhatsApp();
            if (sweepFailing) log.info("Invoice sweep is working again");
            sweepFailing = false;
        } catch (Exception e) {
            if (!sweepFailing) log.warn("Invoice sweep failed (is migration 109 applied?): {}", e.toString());
            sweepFailing = true;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
