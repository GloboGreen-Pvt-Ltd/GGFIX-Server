package com.repairshop.saas.ticket.whatsapp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * WhatsApp booking confirmation status for the Partner App and owner resend.
 * Public paths: /ticket/tickets/{id}/whatsapp… (nginx strips /ticket).
 */
@RestController
@Tag(name = "WhatsApp", description = "Booking confirmation messages (BotBee)")
public class WhatsAppController {

    private final WhatsAppNotificationService service;

    public WhatsAppController(WhatsAppNotificationService service) {
        this.service = service;
    }

    @GetMapping("/tickets/{id}/whatsapp")
    @Operation(summary = "WhatsApp booking confirmation status of this ticket's booking")
    public WhatsAppNotificationService.StatusView status(@PathVariable UUID id, HttpServletRequest request) {
        return service.status(shopId(request), id);
    }

    @PostMapping("/tickets/{id}/whatsapp/resend")
    @Operation(summary = "Resend a failed / skipped booking confirmation (shop owner; customer has agreed)")
    public WhatsAppNotificationService.StatusView resend(@PathVariable UUID id, HttpServletRequest request) {
        if (!isOwner(request)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the shop owner can resend.");
        return service.resend(shopId(request), id);
    }

    private static UUID shopId(HttpServletRequest request) {
        Object sid = request.getAttribute("shopId");
        if (sid == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing shop context");
        return UUID.fromString(sid.toString());
    }

    private static boolean isOwner(HttpServletRequest request) {
        Object raw = request.getAttribute("roles");
        return raw instanceof List<?> roles && (roles.contains("SHOP_OWNER") || roles.contains("SUPER_ADMIN"));
    }
}
