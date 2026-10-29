package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.CreateRepairNoteRequest;
import com.repairshop.saas.ticket.dto.CreateSolutionPackRequest;
import com.repairshop.saas.ticket.dto.ImeiAvailabilityResponse;
import com.repairshop.saas.ticket.dto.RepairNoteResponse;
import com.repairshop.saas.ticket.dto.SolutionPackResponse;
import com.repairshop.saas.ticket.dto.TicketEventResponse;
import com.repairshop.saas.ticket.dto.TicketRequest;
import com.repairshop.saas.ticket.dto.TicketResponse;
import com.repairshop.saas.ticket.security.CallerRoles;
import com.repairshop.saas.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
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

    /** Progress steps the Staff app's Job Details screen posts (JobDetailsScreen ROWS). */
    private static final java.util.Set<String> TECHNICIAN_PROGRESS_KEYS = java.util.Set.of(
            "IN_REPAIR", "PARTS_REQUIRED", "REPAIR_COMPLETED", "QUALITY_CHECK_COMPLETED", "REPAIR_NOT_COMPLETED");

    private final TicketService ticketService;

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        return sid != null ? UUID.fromString(sid) : null;
    }

    private UUID userIdFrom(HttpServletRequest request) {
        String uid = (String) request.getAttribute("userId");
        return uid != null ? UUID.fromString(uid) : null;
    }

    // Employee (Staff app) callers only reach a ticket while it is assigned to
    // them, and may only write to it once they have accepted it. Owner and
    // admin tokens skip the check entirely.
    private void requireOwnJob(HttpServletRequest request, UUID shopId, UUID ticketId, boolean mustBeAccepted) {
        if (!CallerRoles.isEmployee(request)) return;
        ticketService.requireAssignedTechnician(shopId, userIdFrom(request), ticketId, mustBeAccepted);
    }

    // Employees see a job's money, customer contact and unlock code only after
    // accepting it; owner responses are returned as built.
    private TicketResponse forCaller(TicketResponse ticket, HttpServletRequest request) {
        return CallerRoles.isEmployee(request) ? TicketService.redactUntilAccepted(ticket) : ticket;
    }

    @GetMapping("/counts")
    @Operation(summary = "Get booking/ticket counts for shop (dashboard); employees get counts over their own jobs")
    public Map<String, Long> getCounts(HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        if (CallerRoles.isEmployee(request)) return ticketService.getCountsByAssignedUser(shopId, userIdFrom(request));
        return ticketService.getCountsByShop(shopId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ticket by ID")
    public TicketResponse getById(@PathVariable UUID id, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        requireOwnJob(request, shopId, id, false);
        return forCaller(ticketService.getById(shopId, id), request);
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
        requireOwnJob(request, shopId, id, false);
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
    @Operation(summary = "List tickets (paginated). Use assignedToMe=true for technician's assigned tickets "
            + "(status may be comma-separated there); employee logins always get only their own.")
    public Page<TicketResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean assignedToMe,
            Pageable pageable,
            HttpServletRequest request) {
        boolean employee = CallerRoles.isEmployee(request);
        if (assignedToMe || employee) {
            UUID userId = userIdFrom(request);
            if (userId == null) throw new IllegalStateException("Missing user context");
            if (!employee) return ticketService.listByAssignedUser(null, userId, status, q, pageable);
            // Employees resolve their technician row within their own shop.
            UUID shopId = shopIdFrom(request);
            if (shopId == null) throw new IllegalStateException("Missing shop context");
            return ticketService.listByAssignedUser(shopId, userId, status, q, pageable)
                    .map(TicketService::redactUntilAccepted);
        }
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.listByShop(shopId, status, q, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create ticket")
    public TicketResponse create(@Valid @RequestBody TicketRequest body, HttpServletRequest request) {
        CallerRoles.denyEmployee(request);
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
        CallerRoles.denyEmployee(request);
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
        if (CallerRoles.isEmployee(request)) {
            requireOwnJob(request, shopId, id, true);
            // The Staff app only uploads its own device photos here; every other
            // field (booking details, assignment, payment, status) is the owner's.
            Map<String, Object> own = new HashMap<>();
            if (body.containsKey("technicianPhotosJson")) own.put("technicianPhotosJson", body.get("technicianPhotosJson"));
            body = own;
        }
        return ticketService.patch(shopId, id, body);
    }

    @PostMapping("/{id}/re-estimate")
    @Operation(summary = "Re-estimate: add issues + charges found during repair (originals unchanged); awaits customer approval unless approved now")
    public TicketResponse reEstimate(
            @PathVariable UUID id,
            @RequestBody com.repairshop.saas.ticket.dto.ReEstimateRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        requireOwnJob(request, shopId, id, true);
        return ticketService.reEstimate(shopId, id, body);
    }

    @PostMapping("/{id}/price-revision")
    @Operation(summary = "Price Revised: change or cancel existing services (no new services, no status change)")
    public TicketResponse revisePrices(
            @PathVariable UUID id,
            @RequestBody com.repairshop.saas.ticket.dto.PriceRevisionRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        requireOwnJob(request, shopId, id, true);
        return ticketService.revisePrices(shopId, id, body);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update ticket status")
    public void updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            HttpServletRequest request) {
        CallerRoles.denyEmployee(request);
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        ticketService.updateStatus(shopId, id, status);
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Re-Service: reopen a delivered ticket for a new repair")
    public TicketResponse reopen(
            @PathVariable UUID id,
            HttpServletRequest request) {
        CallerRoles.denyEmployee(request);
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.reopen(shopId, id);
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

    // Staff app Decline: hands an assigned, not-yet-accepted job back to the
    // owner for reassignment. The booking itself is not cancelled.
    @PostMapping("/{id}/decline")
    @Operation(summary = "Assigned technician declines the ticket before accepting it (unassigns; status unchanged)")
    public TicketResponse declineByTechnician(
            @PathVariable UUID id,
            @Valid @RequestBody DeclineRequest body,
            HttpServletRequest request) {
        if (!CallerRoles.isEmployee(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned technician can decline a job.");
        }
        UUID shopId = shopIdFrom(request);
        UUID userId = userIdFrom(request);
        if (shopId == null || userId == null) throw new IllegalStateException("Missing auth context");
        return forCaller(ticketService.declineByTechnician(shopId, userId, id, body.getReason().trim()), request);
    }

    // Pre-flight for the shop app's invoice gate: is this IMEI well-formed, and
    // is it free? Omit the query param to check whatever the booking already
    // carries. Advisory only — PATCH /tickets/{id} enforces the same rule on
    // write, so a race between two staff members still ends in one 409.
    @GetMapping("/{id}/imei-availability")
    @Operation(summary = "Check whether an IMEI is valid and not held by another open booking")
    public ImeiAvailabilityResponse checkImeiAvailability(
            @PathVariable UUID id,
            @RequestParam(required = false) String imei,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        return ticketService.checkImeiAvailability(shopId, id, imei);
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
        requireOwnJob(request, shopId, id, true);
        return ticketService.addRepairNote(shopId, id, userIdFrom(request), body);
    }

    // Submitted Notes → Edit on the technician Ticket Detail screen. Same body
    // as the create call: the note text plus the full attachment set it should
    // end up with, so dropping a photo is just sending the shorter list.
    @PutMapping("/{id}/notes/{noteId}")
    @Operation(summary = "Edit a repair note already submitted on this ticket")
    public RepairNoteResponse updateNote(
            @PathVariable UUID id,
            @PathVariable UUID noteId,
            @Valid @RequestBody CreateRepairNoteRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        requireOwnJob(request, shopId, id, true);
        return ticketService.updateRepairNote(shopId, id, noteId, body);
    }

    @GetMapping("/{id}/notes")
    @Operation(summary = "List repair notes attached to a ticket")
    public List<RepairNoteResponse> listNotes(@PathVariable UUID id, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        requireOwnJob(request, shopId, id, false);
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
        requireOwnJob(request, shopId, id, true);
        return ticketService.addSolutionPack(shopId, id, userIdFrom(request), body);
    }

    // Service-progress checklist on the technician Ticket Detail screen.
    // Each Submit click POSTs here with the row's status key; backend emits
    // (or refreshes) the matching repair_booking_events row.
    @PostMapping("/{id}/progress-events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Emit a service-progress step event (Spare Parts Waiting, Quality Check, ...)")
    public void emitProgressEvent(
            @PathVariable UUID id,
            @RequestBody ProgressEventRequest body,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        requireOwnJob(request, shopId, id, true);
        boolean employee = CallerRoles.isEmployee(request);
        // Employees may only post the repair-bench steps the Staff app offers; READY,
        // invoice, delivery and cancel steps move the ticket forward and stay owner-side.
        if (employee && (body.getStatusKey() == null
                || !TECHNICIAN_PROGRESS_KEYS.contains(body.getStatusKey().trim().toUpperCase(java.util.Locale.ROOT)))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This step can only be updated by the shop owner.");
        }
        // An employee's step is always the technician's — never recorded as OWNER.
        String actor = employee ? "TECHNICIAN" : body.getActor();
        ticketService.emitProgressStepEvent(shopId, id, body.getStatusKey(), body.getNote(), actor);
    }

    @lombok.Data
    public static class ProgressEventRequest {
        private String statusKey;
        private String note;
        /** Caller-supplied actor — TECHNICIAN (default) or OWNER. Frontend uses
         *  this to distinguish manually-submitted rows from auto-emitted ones. */
        private String actor;
    }

    @lombok.Data
    public static class DeclineRequest {
        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason must be at most 500 characters")
        private String reason;
    }
}
