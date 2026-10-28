package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.StaffTaskDtos.StatusRequest;
import com.repairshop.saas.ticket.dto.StaffTaskDtos.TaskRequest;
import com.repairshop.saas.ticket.dto.StaffTaskDtos.TaskResponse;
import com.repairshop.saas.ticket.entity.Technician;
import com.repairshop.saas.ticket.repository.TechnicianRepository;
import com.repairshop.saas.ticket.service.StaffTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Staff tasks (Partner App -> Employees -> Staff Tasks; Employee App -> My Tasks).
 *
 * Scoped to the shopId on the caller's JWT. The owner (SHOP_OWNER / SUPER_ADMIN,
 * which SHOP_LOGIN tokens also carry) manages every task of the shop. Any other
 * caller must be a technician row of that shop and only reaches tasks they are
 * assigned to: read, move status (not cancel), tick the checklist.
 */
@RestController
@RequestMapping("/staff-tasks")
@RequiredArgsConstructor
@Tag(name = "Staff Tasks", description = "Shop work assigned to employees")
@SecurityRequirement(name = "Bearer")
public class StaffTaskController {

    /** Default list window when the app sends no range. */
    private static final int DEFAULT_PAST_DAYS = 31;
    private static final int DEFAULT_FUTURE_DAYS = 62;
    private static final int MAX_RANGE_DAYS = 400;

    private final StaffTaskService service;
    private final TechnicianRepository technicianRepository;

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        if (sid == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing shop context");
        return UUID.fromString(sid);
    }

    @SuppressWarnings("unchecked")
    private boolean isOwner(HttpServletRequest request) {
        Object r = request.getAttribute("roles");
        List<String> roles = (r instanceof List) ? (List<String>) r : List.of();
        return roles.contains("SHOP_OWNER") || roles.contains("SUPER_ADMIN");
    }

    private void requireOwner(HttpServletRequest request) {
        if (!isOwner(request)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the shop owner can do this.");
    }

    /** null for the owner; the caller's technician id otherwise. */
    private UUID technicianIdOrNull(HttpServletRequest request, UUID shopId) {
        if (isOwner(request)) return null;
        String uid = (String) request.getAttribute("userId");
        if (uid == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission.");
        Technician me = technicianRepository.findByShopIdAndUserId(shopId, UUID.fromString(uid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission."));
        return me.getId();
    }

    private static LocalDate dateOr(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return LocalDate.parse(raw.trim()); }
        catch (DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date."); }
    }

    @Operation(summary = "Tasks in a date range — all of the shop's for the owner, own tasks for an employee")
    @GetMapping
    public List<TaskResponse> list(@RequestParam(required = false) String from,
                                   @RequestParam(required = false) String to,
                                   HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        LocalDate today = LocalDate.now();
        LocalDate f = dateOr(from, today.minusDays(DEFAULT_PAST_DAYS));
        LocalDate t = dateOr(to, today.plusDays(DEFAULT_FUTURE_DAYS));
        if (t.isBefore(f) || f.plusDays(MAX_RANGE_DAYS).isBefore(t)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a shorter date range.");
        }
        UUID techId = technicianIdOrNull(request, shopId);
        return techId == null ? service.listForShop(shopId, f, t) : service.listForTechnician(shopId, techId, f, t);
    }

    @Operation(summary = "One task")
    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable UUID id, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        return service.get(shopId, id, technicianIdOrNull(request, shopId));
    }

    @Operation(summary = "Create a task (owner). A repeated clientRef returns the first task.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@RequestBody TaskRequest body, HttpServletRequest request) {
        requireOwner(request);
        String uid = (String) request.getAttribute("userId");
        return service.create(shopIdFrom(request), uid == null ? null : UUID.fromString(uid), body);
    }

    @Operation(summary = "Edit / reschedule / reassign a task (owner; only the fields sent change)")
    @PatchMapping("/{id}")
    public TaskResponse update(@PathVariable UUID id, @RequestBody TaskRequest body, HttpServletRequest request) {
        requireOwner(request);
        return service.update(shopIdFrom(request), id, body);
    }

    @Operation(summary = "Set status (owner any; assigned employee IN_PROGRESS / COMPLETED / INCOMPLETE)")
    @PostMapping("/{id}/status")
    public TaskResponse setStatus(@PathVariable UUID id, @RequestBody StatusRequest body, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        return service.setStatus(shopId, id, technicianIdOrNull(request, shopId), body);
    }

    @Operation(summary = "Tick / untick a checklist item (owner or assigned employee)")
    @PostMapping("/{id}/checklist/{itemId}/toggle")
    public TaskResponse toggleChecklist(@PathVariable UUID id, @PathVariable String itemId, HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        return service.toggleChecklistItem(shopId, id, technicianIdOrNull(request, shopId), itemId);
    }

    @Operation(summary = "Delete a task (owner)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, HttpServletRequest request) {
        requireOwner(request);
        service.delete(shopIdFrom(request), id);
    }
}
