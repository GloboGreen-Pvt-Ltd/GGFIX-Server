package com.repairshop.saas.subscription.invoice;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * subscription_invoices + subscription_invoice_counters (migration 109).
 * JDBC (no JPA entity) so the service still boots before the migration runs;
 * every caller treats a failure here as "no invoice yet", never as a payment error.
 */
public interface InvoiceStore {

    String WA_PENDING = "PENDING";
    String WA_SENT = "SENT";
    String WA_FAILED = "FAILED";
    String WA_SKIPPED = "SKIPPED";

    /** One invoice row without the PDF bytes. {@code viewJson} = the frozen InvoiceView (whatsapp block omitted). */
    record InvoiceRecord(UUID id, String invoiceNo, UUID paymentId, UUID orderId, UUID ownerUserId,
                         String documentType, Instant issuedAt, String viewJson, boolean hasPdf,
                         String whatsappStatus, String whatsappReason, String whatsappMessageId,
                         Instant whatsappSentAt, int whatsappAttempts) {}

    /** Everything an insert writes. */
    record NewInvoice(UUID id, String invoiceNo, String financialYear, long seq, UUID paymentId, UUID orderId,
                      UUID ownerUserId, String documentType, String title, String paymentMode, long totalPaise,
                      long taxablePaise, long cgstPaise, long sgstPaise, long igstPaise, long taxPaise,
                      String placeOfSupply, String viewJson, String whatsappStatus, String whatsappReason,
                      Instant issuedAt) {}

    Optional<InvoiceRecord> findById(UUID id);

    Optional<InvoiceRecord> findByPaymentId(UUID paymentId);

    Optional<InvoiceRecord> findByOrderId(UUID orderId);

    /** The owner's invoices, newest first. */
    List<InvoiceRecord> findByOwner(UUID ownerUserId);

    /** paymentId → invoice, for the payment history. */
    Map<UUID, InvoiceRecord> findByPaymentIds(Collection<UUID> paymentIds);

    /** SELECT … FOR UPDATE on the payment row: serialises concurrent issuers of the same payment. */
    void lockPayment(UUID paymentId);

    /** Atomic per-series counter (upsert … RETURNING); runs in the caller's transaction, so a rollback leaves no gap. */
    long nextSeq(String series);

    InvoiceRecord insert(NewInvoice invoice);

    Optional<byte[]> pdf(UUID id);

    void savePdf(UUID id, byte[] pdf, Instant at);

    /**
     * Optimistic claim before a WhatsApp send: bumps attempts and sets PENDING only
     * when the row still has {@code expectedStatus} and {@code expectedAttempts}.
     * False = someone else is (or was) sending it.
     */
    boolean claimWhatsApp(UUID id, String expectedStatus, int expectedAttempts, Instant at);

    void finishWhatsApp(UUID id, String status, String reason, String messageId, String recipientMasked, Instant sentAt);

    /** ACTIVATED, captured payments paid since {@code since} that have no invoice (recovery sweep). */
    List<UUID> paymentsMissingInvoice(Instant since, Instant paidBefore, int limit);

    /** Invoices still PENDING and never attempted, issued before {@code issuedBefore}. */
    List<UUID> unsentWhatsApp(Instant issuedBefore, int limit);

    /** PENDING rows whose claimed send never finished (crash mid-send) → FAILED so the owner can retry. */
    int failStaleWhatsApp(Instant claimedBefore, Instant at);
}
