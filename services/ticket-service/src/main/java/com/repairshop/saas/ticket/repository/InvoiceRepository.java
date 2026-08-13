package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByTicketId(UUID ticketId);

    /** One query per ticket PAGE rather than one per row — see TicketService.listByShop. */
    List<Invoice> findByTicketIdIn(Collection<UUID> ticketIds);

    boolean existsByInvoiceNo(String invoiceNo);
}
