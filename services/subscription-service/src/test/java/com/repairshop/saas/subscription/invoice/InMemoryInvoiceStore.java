package com.repairshop.saas.subscription.invoice;

import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@link InvoiceStore} with the same unique keys as migration 109, in memory. */
class InMemoryInvoiceStore implements InvoiceStore {

    final Map<UUID, InvoiceRecord> rows = new LinkedHashMap<>();
    final Map<UUID, byte[]> pdfs = new HashMap<>();
    final Map<String, Long> counters = new HashMap<>();
    int nextSeqCalls;
    int locks;

    @Override
    public Optional<InvoiceRecord> findById(UUID id) {
        return Optional.ofNullable(rows.get(id)).map(this::withPdfFlag);
    }

    @Override
    public Optional<InvoiceRecord> findByPaymentId(UUID paymentId) {
        return rows.values().stream().filter(r -> r.paymentId().equals(paymentId)).findFirst().map(this::withPdfFlag);
    }

    @Override
    public Optional<InvoiceRecord> findByOrderId(UUID orderId) {
        return rows.values().stream().filter(r -> r.orderId().equals(orderId)).findFirst().map(this::withPdfFlag);
    }

    @Override
    public List<InvoiceRecord> findByOwner(UUID ownerUserId) {
        return rows.values().stream().filter(r -> r.ownerUserId().equals(ownerUserId))
                .sorted(Comparator.comparing(InvoiceRecord::issuedAt).reversed()).map(this::withPdfFlag).toList();
    }

    @Override
    public Map<UUID, InvoiceRecord> findByPaymentIds(Collection<UUID> paymentIds) {
        Map<UUID, InvoiceRecord> out = new HashMap<>();
        rows.values().stream().filter(r -> paymentIds.contains(r.paymentId())).forEach(r -> out.put(r.paymentId(), r));
        return out;
    }

    @Override
    public void lockPayment(UUID paymentId) {
        locks++;
    }

    @Override
    public long nextSeq(String series) {
        nextSeqCalls++;
        return counters.merge(series, 1L, Long::sum);
    }

    @Override
    public InvoiceRecord insert(NewInvoice n) {
        boolean dup = rows.values().stream().anyMatch(r -> r.paymentId().equals(n.paymentId())
                || r.orderId().equals(n.orderId()) || r.invoiceNo().equals(n.invoiceNo()));
        if (dup) throw new DuplicateKeyException("uq_subscription_invoices");
        InvoiceRecord r = new InvoiceRecord(n.id(), n.invoiceNo(), n.paymentId(), n.orderId(), n.ownerUserId(),
                n.documentType(), n.issuedAt(), n.viewJson(), false, n.whatsappStatus(), n.whatsappReason(), null, null, 0);
        rows.put(r.id(), r);
        return r;
    }

    @Override
    public Optional<byte[]> pdf(UUID id) {
        return Optional.ofNullable(pdfs.get(id));
    }

    @Override
    public void savePdf(UUID id, byte[] pdf, Instant at) {
        pdfs.put(id, pdf);
    }

    @Override
    public boolean claimWhatsApp(UUID id, String expectedStatus, int expectedAttempts, Instant at) {
        InvoiceRecord r = rows.get(id);
        if (r == null || !expectedStatus.equals(r.whatsappStatus()) || r.whatsappAttempts() != expectedAttempts) return false;
        rows.put(id, copy(r, WA_PENDING, null, r.whatsappMessageId(), r.whatsappSentAt(), r.whatsappAttempts() + 1));
        return true;
    }

    @Override
    public void finishWhatsApp(UUID id, String status, String reason, String messageId, String recipientMasked,
                               Instant sentAt) {
        InvoiceRecord r = rows.get(id);
        rows.put(id, copy(r, status, reason, messageId != null ? messageId : r.whatsappMessageId(),
                sentAt != null ? sentAt : r.whatsappSentAt(), r.whatsappAttempts()));
    }

    @Override
    public List<UUID> paymentsMissingInvoice(Instant since, Instant paidBefore, int limit) {
        return List.of();
    }

    @Override
    public List<UUID> unsentWhatsApp(Instant issuedBefore, int limit) {
        return List.of();
    }

    @Override
    public int failStaleWhatsApp(Instant claimedBefore, Instant at) {
        return 0;
    }

    private InvoiceRecord withPdfFlag(InvoiceRecord r) {
        return new InvoiceRecord(r.id(), r.invoiceNo(), r.paymentId(), r.orderId(), r.ownerUserId(), r.documentType(),
                r.issuedAt(), r.viewJson(), pdfs.containsKey(r.id()), r.whatsappStatus(), r.whatsappReason(),
                r.whatsappMessageId(), r.whatsappSentAt(), r.whatsappAttempts());
    }

    private static InvoiceRecord copy(InvoiceRecord r, String status, String reason, String messageId, Instant sentAt,
                                      int attempts) {
        return new InvoiceRecord(r.id(), r.invoiceNo(), r.paymentId(), r.orderId(), r.ownerUserId(), r.documentType(),
                r.issuedAt(), r.viewJson(), r.hasPdf(), status, reason, messageId, sentAt, attempts);
    }
}
