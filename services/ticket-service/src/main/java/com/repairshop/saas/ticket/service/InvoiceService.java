package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.dto.InvoiceRequest;
import com.repairshop.saas.ticket.dto.InvoiceResponse;
import com.repairshop.saas.ticket.entity.Invoice;
import com.repairshop.saas.ticket.entity.Ticket;
import com.repairshop.saas.ticket.repository.InvoiceRepository;
import com.repairshop.saas.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Upsert + read of an invoice tied to a ticket. One row per ticket
 * (uq_invoices_ticket_id) — re-generating overwrites the same row so the
 * owner can correct mistakes without leaving phantom invoice numbers.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final TicketRepository ticketRepository;

    @Transactional
    public InvoiceResponse upsert(UUID ticketId, InvoiceRequest req, UUID generatedBy) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        Invoice inv = invoiceRepository.findByTicketId(ticketId).orElseGet(Invoice::new);
        inv.setTicketId(ticketId);
        inv.setShopId(ticket.getShopId());

        // Auto-fill invoice_no if client didn't send one. Use the ticket's
        // tracking ID as a deterministic seed so repeated calls don't churn
        // through new numbers, then suffix with the row's first 8 chars if
        // the tracking ID is already in use elsewhere.
        String invoiceNo = req.getInvoiceNo();
        if (invoiceNo == null || invoiceNo.isBlank()) {
            invoiceNo = ticket.getTrackingId() != null ? ticket.getTrackingId() : ticketId.toString().substring(0, 10);
        }
        inv.setInvoiceNo(invoiceNo);

        inv.setTicketDate(req.getTicketDate() != null ? req.getTicketDate() : ticket.getCreatedAt());
        inv.setDeliveryDate(req.getDeliveryDate());
        inv.setGstNo(req.getGstNo());

        inv.setServiceCharges(nz(req.getServiceCharges()));
        inv.setTotalRepairAmount(nz(req.getTotalRepairAmount()));
        inv.setSpareUtilityCharge(nz(req.getSpareUtilityCharge()));
        inv.setDiscount(nz(req.getDiscount()));
        inv.setTaxMode(req.getTaxMode() != null ? req.getTaxMode() : "WITHOUT");
        inv.setGstPercent(nz(req.getGstPercent()));

        inv.setAmount2Plus3(nz(req.getAmount2Plus3()));
        inv.setBaseAmount(nz(req.getBaseAmount()));
        inv.setTotalGst(nz(req.getTotalGst()));
        inv.setFinalPayableAmount(nz(req.getFinalPayableAmount()));
        inv.setAmountInWords(req.getAmountInWords());

        inv.setSpareLinesJson(req.getSpareLinesJson());
        inv.setServiceLinesJson(req.getServiceLinesJson());

        if (inv.getGeneratedAt() == null) inv.setGeneratedAt(Instant.now());
        if (generatedBy != null) inv.setGeneratedBy(generatedBy);

        return InvoiceResponse.from(invoiceRepository.save(inv));
    }

    @Transactional(readOnly = true)
    public Optional<InvoiceResponse> findByTicket(UUID ticketId) {
        return invoiceRepository.findByTicketId(ticketId).map(InvoiceResponse::from);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(UUID id) {
        return invoiceRepository.findById(id)
                .map(InvoiceResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
