package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByTicketId(UUID ticketId);
    boolean existsByInvoiceNo(String invoiceNo);
}
