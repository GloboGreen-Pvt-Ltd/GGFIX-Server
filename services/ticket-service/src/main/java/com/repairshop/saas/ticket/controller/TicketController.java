package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.CreateRepairNoteRequest;
import com.repairshop.saas.ticket.dto.CreateSolutionPackRequest;
import com.repairshop.saas.ticket.dto.RepairNoteResponse;
import com.repairshop.saas.ticket.dto.SolutionPackResponse;
import com.repairshop.saas.ticket.dto.TicketEventResponse;
import com.repairshop.saas.ticket.dto.TicketRequest;
import com.repairshop.saas.ticket.dto.TicketResponse;
import com.repairshop.saas.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Repair ticket CRUD")
@SecurityRequirement(name = "Bearer")
public class TicketController {

    private final TicketService ticketService;

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        return sid != null ? UUID.fromString(sid) : null;
    }

    private UUID userIdFrom(HttpServletRequest request) {
        String uid = (String) request.getAttribute("userId");
        return uid != null ? UUID.fromString(uid) : null;
    }

    @GetMapping("/counts")
    @Operation(summary = "Get booking/ticket counts for shop (dashboard)")
    public Map<String, Long> getCounts(HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.getCountsByShop(shopId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ticket by ID")
    public TicketResponse getById(@PathVariable UUID id, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.getById(shopId, id);
    }

    @GetMapping("/customer/{id}")
    @Operation(summary = "Get ticket by ID for the customer who placed it (mobile My Orders → Service → View Details)")
    public TicketResponse getForCustomer(@PathVariable UUID id, HttpServletRequest request) {
        requireRole(request, "CUSTOMER");
        UUID userId = userIdFrom(request);
        if (userId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing user context");
        return ticketService.getForCustomer(userId, id);
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Service timeline events for a ticket (owner BookingTimelineScreen)")
    public List<TicketEventResponse> getEvents(@PathVariable UUID id, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.getEventsForShop(shopId, id);
    }

    @SuppressWarnings("unchecked")
    private void requireRole(HttpServletRequest request, String role) {
        Object raw = request.getAttribute("roles");
        if (raw instanceof List<?> roles
                && roles.stream().anyMatch(r -> role.equalsIgnoreCase(String.valueOf(r).trim().toUpperCase(Locale.ROOT)))) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role not allowed");
    }

    @GetMapping
    @Operation(summary = "List tickets (paginated). Use assignedToMe=true for technician's assigned tickets.")
    public Page<TicketResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean assignedToMe,
            Pageable pageable,
            HttpServletRequest request) {
        if (assignedToMe) {
            UUID userId = userIdFrom(request);
            if (userId == null) throw new IllegalStateException("Missing user context");
            return ticketService.listByAssignedUser(userId, pageable);
        }
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.listByShop(shopId, status, q, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create ticket")
    public TicketResponse create(@Valid @RequestBody TicketRequest body, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.create(shopId, body);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ticket")
    public TicketResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody TicketRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.update(shopId, id, body);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Patch ticket (currently supports technician assignment and simple fields)")
    public TicketResponse patch(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.patch(shopId, id, body);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update ticket status")
    public void updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        ticketService.updateStatus(shopId, id, status);
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Assigned technician accepts the ticket and starts work")
    public TicketResponse acceptByTechnician(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        UUID userId = userIdFrom(request);
        if (shopId == null || userId == null) throw new IllegalStateException("Missing auth context");
        return ticketService.acceptByTechnician(shopId, userId, id);
    }

    // ---------- Repair notes ----------------------------------------------

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a repair note to a ticket (used by the tech detail screen's Compliance Notes submit)")
    public RepairNoteResponse addNote(
            @PathVariable UUID id,
            @Valid @RequestBody CreateRepairNoteRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.addRepairNote(shopId, id, userIdFrom(request), body);
    }

    @GetMapping("/{id}/notes")
    @Operation(summary = "List repair notes attached to a ticket")
    public List<RepairNoteResponse> listNotes(@PathVariable UUID id, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.listRepairNotes(shopId, id);
    }

    // ---------- Solution packs --------------------------------------------

    @GetMapping("/{id}/solution-packs")
    @Operation(summary = "List solution packs for a ticket (filter by packType: REFERENCE | NEW)")
    public List<SolutionPackResponse> listSolutionPacks(
            @PathVariable UUID id,
            @RequestParam(required = false) String packType,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.listSolutionPacks(shopId, id, packType);
    }

    @PostMapping("/{id}/solution-packs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach a new solution pack to a ticket (file URL produced by /media/upload first)")
    public SolutionPackResponse addSolutionPack(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSolutionPackRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.addSolutionPack(shopId, id, userIdFrom(request), body);
    }

    // Service-progress checklist on the technician Ticket Detail screen.
    // Each Submit click POSTs here with the row's status key; backend emits
    // (or refreshes) the matching repair_booking_events row.
    @PostMapping("/{id}/progress-events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Emit a service-progress step event (Parts Required, Quality Check, ...)")
    public void emitProgressEvent(
            @PathVariable UUID id,
            @RequestBody ProgressEventRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        ticketService.emitProgressStepEvent(shopId, id, body.getStatusKey(), body.getNote(), body.getActor());
    }

    @lombok.Data
    public static class ProgressEventRequest {
        private String statusKey;
        private String note;
        /** Caller-supplied actor — TECHNICIAN (default) or OWNER. Frontend uses
         *  this to distinguish manually-submitted rows from auto-emitted ones. */
        private String actor;
    }
}
