package com.repairshop.saas.subscription.invoice;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Postgres implementation of {@link InvoiceStore} (plain JDBC, migration 109). */
@Repository
@RequiredArgsConstructor
public class JdbcInvoiceStore implements InvoiceStore {

    private static final String COLUMNS = """
            id, invoice_no, payment_id, order_id, owner_user_id, document_type, issued_at, view_json,
            (pdf IS NOT NULL) AS has_pdf, whatsapp_status, whatsapp_reason, whatsapp_message_id,
            whatsapp_sent_at, whatsapp_attempts
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    private static final RowMapper<InvoiceRecord> ROW = (rs, i) -> new InvoiceRecord(
            (UUID) rs.getObject("id"), rs.getString("invoice_no"), (UUID) rs.getObject("payment_id"),
            (UUID) rs.getObject("order_id"), (UUID) rs.getObject("owner_user_id"), rs.getString("document_type"),
            instant(rs.getTimestamp("issued_at")), rs.getString("view_json"), rs.getBoolean("has_pdf"),
            rs.getString("whatsapp_status"), rs.getString("whatsapp_reason"), rs.getString("whatsapp_message_id"),
            instant(rs.getTimestamp("whatsapp_sent_at")), rs.getInt("whatsapp_attempts"));

    @Override
    public Optional<InvoiceRecord> findById(UUID id) {
        return one("SELECT " + COLUMNS + " FROM subscription_invoices WHERE id = ?", id);
    }

    @Override
    public Optional<InvoiceRecord> findByPaymentId(UUID paymentId) {
        return one("SELECT " + COLUMNS + " FROM subscription_invoices WHERE payment_id = ?", paymentId);
    }

    @Override
    public Optional<InvoiceRecord> findByOrderId(UUID orderId) {
        return one("SELECT " + COLUMNS + " FROM subscription_invoices WHERE order_id = ?", orderId);
    }

    @Override
    public List<InvoiceRecord> findByOwner(UUID ownerUserId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM subscription_invoices WHERE owner_user_id = ? "
                + "ORDER BY issued_at DESC", ROW, ownerUserId);
    }

    @Override
    public Map<UUID, InvoiceRecord> findByPaymentIds(Collection<UUID> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) return Map.of();
        Map<UUID, InvoiceRecord> out = new HashMap<>();
        for (InvoiceRecord r : named.query("SELECT " + COLUMNS + " FROM subscription_invoices WHERE payment_id IN (:ids)",
                new MapSqlParameterSource("ids", paymentIds), ROW)) {
            out.put(r.paymentId(), r);
        }
        return out;
    }

    @Override
    public void lockPayment(UUID paymentId) {
        jdbc.query("SELECT id FROM subscription_payments WHERE id = ? FOR UPDATE", rs -> {}, paymentId);
    }

    @Override
    public long nextSeq(String series) {
        Long n = jdbc.queryForObject("""
                INSERT INTO subscription_invoice_counters (series, last_seq, updated_at) VALUES (?, 1, now())
                ON CONFLICT (series) DO UPDATE
                   SET last_seq = subscription_invoice_counters.last_seq + 1, updated_at = now()
                RETURNING last_seq
                """, Long.class, series);
        if (n == null) throw new IllegalStateException("Invoice counter returned nothing for " + series);
        return n;
    }

    @Override
    public InvoiceRecord insert(NewInvoice n) {
        jdbc.update("""
                INSERT INTO subscription_invoices (id, invoice_no, financial_year, seq, payment_id, order_id,
                    owner_user_id, document_type, title, payment_mode, total_paise, taxable_paise, cgst_paise,
                    sgst_paise, igst_paise, tax_paise, place_of_supply, view_json, whatsapp_status, whatsapp_reason,
                    issued_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, n.id(), n.invoiceNo(), n.financialYear(), n.seq(), n.paymentId(), n.orderId(), n.ownerUserId(),
                n.documentType(), n.title(), n.paymentMode(), n.totalPaise(), n.taxablePaise(), n.cgstPaise(),
                n.sgstPaise(), n.igstPaise(), n.taxPaise(), n.placeOfSupply(), n.viewJson(), n.whatsappStatus(),
                n.whatsappReason(), Timestamp.from(n.issuedAt()));
        return new InvoiceRecord(n.id(), n.invoiceNo(), n.paymentId(), n.orderId(), n.ownerUserId(), n.documentType(),
                n.issuedAt(), n.viewJson(), false, n.whatsappStatus(), n.whatsappReason(), null, null, 0);
    }

    @Override
    public Optional<byte[]> pdf(UUID id) {
        List<byte[]> rows = jdbc.query("SELECT pdf FROM subscription_invoices WHERE id = ? AND pdf IS NOT NULL",
                (rs, i) -> rs.getBytes("pdf"), id);
        return rows.stream().findFirst();
    }

    @Override
    public void savePdf(UUID id, byte[] pdf, Instant at) {
        jdbc.update("UPDATE subscription_invoices SET pdf = ?, pdf_generated_at = ?, updated_at = ? WHERE id = ?",
                pdf, Timestamp.from(at), Timestamp.from(at), id);
    }

    @Override
    public boolean claimWhatsApp(UUID id, String expectedStatus, int expectedAttempts, Instant at) {
        return jdbc.update("""
                UPDATE subscription_invoices
                   SET whatsapp_status = 'PENDING', whatsapp_reason = NULL, whatsapp_attempts = whatsapp_attempts + 1,
                       whatsapp_claimed_at = ?, updated_at = ?
                 WHERE id = ? AND whatsapp_status = ? AND whatsapp_attempts = ?
                """, Timestamp.from(at), Timestamp.from(at), id, expectedStatus, expectedAttempts) == 1;
    }

    @Override
    public void finishWhatsApp(UUID id, String status, String reason, String messageId, String recipientMasked,
                               Instant sentAt) {
        jdbc.update("""
                UPDATE subscription_invoices
                   SET whatsapp_status = ?, whatsapp_reason = ?, whatsapp_message_id = COALESCE(?, whatsapp_message_id),
                       whatsapp_recipient = COALESCE(?, whatsapp_recipient),
                       whatsapp_sent_at = COALESCE(?, whatsapp_sent_at), updated_at = now()
                 WHERE id = ?
                """, status, truncate(reason), messageId, recipientMasked, sentAt == null ? null : Timestamp.from(sentAt), id);
    }

    @Override
    public List<UUID> paymentsMissingInvoice(Instant since, Instant paidBefore, int limit) {
        return jdbc.query("""
                SELECT p.id FROM subscription_payments p
                  LEFT JOIN subscription_invoices i ON i.payment_id = p.id OR i.order_id = p.order_id
                 WHERE i.id IS NULL AND p.activation_status = 'ACTIVATED'
                   AND p.paid_at >= ? AND p.paid_at < ?
                 ORDER BY p.paid_at
                 LIMIT ?
                """, (rs, i) -> (UUID) rs.getObject("id"), Timestamp.from(since), Timestamp.from(paidBefore), limit);
    }

    @Override
    public List<UUID> unsentWhatsApp(Instant issuedBefore, int limit) {
        return jdbc.query("""
                SELECT id FROM subscription_invoices
                 WHERE whatsapp_status = 'PENDING' AND whatsapp_attempts = 0 AND issued_at < ?
                 ORDER BY issued_at LIMIT ?
                """, (rs, i) -> (UUID) rs.getObject("id"), Timestamp.from(issuedBefore), limit);
    }

    @Override
    public int failStaleWhatsApp(Instant claimedBefore, Instant at) {
        return jdbc.update("""
                UPDATE subscription_invoices
                   SET whatsapp_status = 'FAILED', whatsapp_reason = 'Delivery was interrupted; please retry',
                       updated_at = ?
                 WHERE whatsapp_status = 'PENDING' AND whatsapp_attempts > 0 AND whatsapp_claimed_at < ?
                """, Timestamp.from(at), Timestamp.from(claimedBefore));
    }

    private Optional<InvoiceRecord> one(String sql, Object arg) {
        return jdbc.query(sql, ROW, arg).stream().findFirst();
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }
}
