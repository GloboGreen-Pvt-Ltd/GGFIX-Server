package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.InvoiceRequest;
import com.repairshop.saas.ticket.dto.InvoiceResponse;
import com.repairshop.saas.ticket.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * One latest invoice per ticket. POST upserts (same row each time); GET returns
 * the stored snapshot — used by both the Invoice Generator (prefill on revisit)
 * and the Deliver Invoice Report screen.
 */
@RestController
@RequestMapping("/tickets/{ticketId}/invoice")
@RequiredArgsConstructor
@Tag(name = "Invoices", description = "Per-ticket invoice snapshot")
@SecurityRequirement(name = "Bearer")
public class InvoiceController {

    private final InvoiceService invoiceService;

    private UUID userIdFrom(HttpServletRequest request) {
        String uid = (String) request.getAttribute("userId");
        try {
            return uid != null ? UUID.fromString(uid) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Operation(summary = "Upsert the invoice snapshot for a ticket")
    @PostMapping
    public InvoiceResponse upsert(@PathVariable UUID ticketId,
                                  @RequestBody InvoiceRequest body,
                                  HttpServletRequest request) {
        return invoiceService.upsert(ticketId, body, userIdFrom(request));
    }

    @Operation(summary = "Get the invoice snapshot for a ticket")
    @GetMapping
    public ResponseEntity<InvoiceResponse> getByTicket(@PathVariable UUID ticketId) {
        return invoiceService.findByTicket(ticketId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
