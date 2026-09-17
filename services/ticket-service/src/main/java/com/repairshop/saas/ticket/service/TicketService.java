package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.dto.CreateRepairNoteRequest;
import com.repairshop.saas.ticket.dto.CreateSolutionPackRequest;
import com.repairshop.saas.ticket.dto.RepairNoteResponse;
import com.repairshop.saas.ticket.dto.SolutionPackResponse;
import com.repairshop.saas.ticket.dto.TicketEventResponse;
import com.repairshop.saas.ticket.dto.TicketRequest;
import com.repairshop.saas.ticket.dto.TicketResponse;
import com.repairshop.saas.ticket.entity.PlatformRepairBookingEvent;
import com.repairshop.saas.ticket.entity.RepairNote;
import com.repairshop.saas.ticket.entity.Technician;
import com.repairshop.saas.ticket.entity.Ticket;
import com.repairshop.saas.ticket.entity.TicketSolutionPack;
import com.repairshop.saas.ticket.exception.ResourceNotFoundException;
import com.repairshop.saas.ticket.repository.MasterTechnicianWorkStatusViewRepository;
import com.repairshop.saas.ticket.repository.PlatformCustomerAddressRepository;
import com.repairshop.saas.ticket.repository.PlatformCustomerUserRepository;
import com.repairshop.saas.ticket.repository.PlatformRepairBookingEventRepository;
import com.repairshop.saas.ticket.repository.PlatformRepairBookingRepository;
import com.repairshop.saas.ticket.repository.RepairNoteRepository;
import com.repairshop.saas.ticket.repository.TechnicianRepository;
import com.repairshop.saas.ticket.repository.TicketRepository;
import com.repairshop.saas.ticket.repository.TicketSolutionPackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TechnicianRepository technicianRepository;
    private final PlatformRepairBookingRepository platformRepairBookingRepository;
    private final PlatformRepairBookingEventRepository platformRepairBookingEventRepository;
    private final PlatformCustomerAddressRepository platformCustomerAddressRepository;
    private final PlatformCustomerUserRepository platformCustomerUserRepository;
    private final RepairNoteRepository repairNoteRepository;
    private final TicketSolutionPackRepository ticketSolutionPackRepository;
    private final MasterTechnicianWorkStatusViewRepository masterWorkStatusRepository;
    private final CustomerOrderMirrorService customerOrderMirrorService;

    private static final String TRACKING_PREFIX = "CSPEN";

    @Transactional
    public TicketResponse getById(UUID shopId, UUID id) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        // Self-healing: walk-in tickets created before the mirror was widened
        // to support null customer_user_id won't have a booking row yet, so
        // step events have no booking_id to attach to. Run the mirror first
        // so it creates the booking + the lifecycle events; then sync fills
        // in any step events implied by current ticket state.
        //
        // Errors are caught + logged: the ticket-detail screen fires this
        // read in parallel with getEventsForShop, and both call mirrorOnUpsert
        // in REQUIRES_NEW transactions. A transient race (optimistic lock)
        // must not break the primary read — the next mirror run will heal it.
        try { customerOrderMirrorService.mirrorOnUpsert(t); }
        catch (Exception e) { log.error("mirrorOnUpsert failed for ticket {}", id, e); }
        try { syncStepEventsFromTicketState(t); }
        catch (Exception e) { log.error("syncStepEventsFromTicketState failed for ticket {}", id, e); }
        return toResponse(t);
    }

    /**
     * Service timeline for the owner BookingTimelineScreen. Reads from the
     * mirrored repair_booking_events (same table the customer history reads),
     * scoped to a ticket via repair_bookings.ticket_id. Returns an empty list
     * (not 404) when the mirror booking hasn't been created yet so the screen
     * can render its phase skeleton.
     */
    @Transactional
    public List<TicketEventResponse> getEventsForShop(UUID shopId, UUID ticketId) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        // Same self-heal as getById: create the booking mirror if missing so
        // walk-in tickets gain a timeline on first view. Errors here are
        // caught + logged so a bad mirror state doesn't 500 the whole events
        // endpoint — we still try to return whatever events exist.
        try { customerOrderMirrorService.mirrorOnUpsert(t); }
        catch (Exception e) { log.error("mirrorOnUpsert failed for ticket {}", ticketId, e); }
        try { syncStepEventsFromTicketState(t); }
        catch (Exception e) { log.error("syncStepEventsFromTicketState failed for ticket {}", ticketId, e); }
        return platformRepairBookingRepository.findByTicketId(t.getId())
                .map(b -> platformRepairBookingEventRepository
                        .findByBookingIdOrderByCreatedAtAsc(b.getId())
                        .stream()
                        .map(e -> TicketEventResponse.builder()
                                .id(e.getId())
                                .status(e.getStatus())
                                .note(e.getNote())
                                .actor(e.getActor())
                                .audioUrl(e.getAudioUrl())
                                .imageUrls(parseImagesJson(e.getImagesJson()))
                                .createdAt(e.getCreatedAt())
                                .build())
                        .toList())
                .orElse(List.of());
    }

    // Emit every "In Service Process" step event that the ticket's current
    // state implies but that hasn't been written yet. Idempotent — the inner
    // emit helpers check for existing rows. Run on every ticket read so
    // pre-existing tickets self-heal without a separate backfill job.
    private void syncStepEventsFromTicketState(Ticket t) {
        if (t == null) return;
        // SERVICE_ACCEPTED follows BOOKING_CREATED_BY_SHOP for every shop-side
        // booking. Self-heals pre-existing tickets that pre-date the auto-emit.
        emitBookingEvent(t.getId(), "SERVICE_ACCEPTED",
                "Service Accepted", "SHOP");
        String status = t.getStatus() == null ? "" : t.getStatus().toUpperCase();
        if (Set.of("IN_REPAIR", "APPROVED", "READY", "DELIVERED").contains(status)) {
            emitBookingEvent(t.getId(), "TECHNICIAN_WORK_STARTED",
                    "Technician Work Started", "TECHNICIAN");
        }
        if (Set.of("QUOTED", "APPROVED", "IN_REPAIR", "READY", "DELIVERED").contains(status)) {
            emitBookingEvent(t.getId(), "WAITING_FOR_CUSTOMER_APPROVAL",
                    "Waiting for Customer Approval", "TECHNICIAN");
        }
        if (hasAtLeastOneUrl(t.getTechnicianPhotosJson())) {
            emitBookingEvent(t.getId(), "TECHNICIAN_UPLOADED_DEVICE_IMAGES",
                    "Technician Uploaded Device Images", "TECHNICIAN");
        }
        // Repair notes / solution packs: peek by existence, no full load.
        boolean hasComplianceNote = repairNoteRepository
                .findByTicketIdOrderByCreatedAtDesc(t.getId()).stream()
                .anyMatch(n -> !Boolean.TRUE.equals(n.getIsInternal()));
        if (hasComplianceNote) {
            emitBookingEvent(t.getId(), "TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED_UPDATED",
                    "Technician Issue Verified & Updated", "TECHNICIAN");
        }
        boolean hasNewSolutionPack = !ticketSolutionPackRepository
                .findByTicketIdAndPackTypeOrderByCreatedAtDesc(t.getId(), "NEW").isEmpty();
        if (hasNewSolutionPack) {
            emitBookingEvent(t.getId(), "ISSUE_IDENTIFIED",
                    "Issue identified by technician", "TECHNICIAN");
        }
    }

    /**
     * Read a single ticket as the customer who placed it. Ownership is established
     * by ticket.customer_id → customers.platform_user_id == JWT subject. The
     * device security value is masked since the customer already knows their own
     * PIN/pattern and the field is sensitive in transit.
     */
    @Transactional(readOnly = true)
    public TicketResponse getForCustomer(UUID platformUserId, UUID id) {
        Ticket t = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        if (t.getCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ticket has no customer");
        }
        // tickets.customer_id is now the customer_users.id directly (the old
        // per-shop customers row + indirect platform_user_id link is gone).
        if (!t.getCustomerId().equals(platformUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your ticket");
        }
        return toCustomerResponse(t);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> listByShop(UUID shopId, String status, String q, Pageable pageable) {
        String normalizedStatus = status != null && !status.isBlank() ? status : null;
        String normalizedQuery = q != null && !q.isBlank() ? q.trim() : null;
        Page<Ticket> page = normalizedQuery != null
                ? ticketRepository.searchByShop(shopId, normalizedStatus, normalizedQuery, pageable)
                : normalizedStatus != null
                        ? ticketRepository.findByShopIdAndStatus(shopId, normalizedStatus, pageable)
                        : ticketRepository.findByShopId(shopId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> listByAssignedTechnician(UUID technicianId, Pageable pageable) {
        return ticketRepository.findByAssignedTechnicianId(technicianId, pageable).map(this::toResponse);
    }

    /** For technician "my tickets": resolve user id to technician id then list assigned tickets. */
    @Transactional(readOnly = true)
    public Page<TicketResponse> listByAssignedUser(UUID userId, Pageable pageable) {
        return technicianRepository.findFirstByUserId(userId)
                .map(tech -> ticketRepository.findByAssignedTechnicianId(tech.getId(), pageable).map(this::toResponse))
                .orElse(new PageImpl<>(Collections.emptyList(), pageable, 0));
    }

    /**
     * Returns booking/ticket counts for the shop (for owner dashboard).
     * Keys: CREATED, IN_DIAGNOSIS, QUOTED, APPROVED, IN_REPAIR, READY, DELIVERED, CANCELLED, total, assignedCount.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getCountsByShop(UUID shopId) {
        Map<String, Long> counts = new HashMap<>();
        String[] statuses = { "CREATED", "IN_DIAGNOSIS", "QUOTED", "APPROVED", "IN_REPAIR", "READY", "DELIVERED", "CANCELLED" };
        for (String s : statuses) {
            counts.put(s, ticketRepository.countByShopIdAndStatus(shopId, s));
        }
        counts.put("total", ticketRepository.countByShopId(shopId));
        counts.put("assignedCount", ticketRepository.countByShopIdAndAssignedTechnicianIdNotNull(shopId));
        return counts;
    }

    @Transactional
    public TicketResponse create(UUID shopId, TicketRequest request) {
        String trackingId = generateTrackingId(shopId);
        Ticket ticket = Ticket.builder()
                .shopId(shopId)
                .customerId(request.getCustomerId())
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .brandId(request.getBrandId())
                .modelId(request.getModelId())
                .ramOptionId(request.getRamOptionId())
                .storageOptionId(request.getStorageOptionId())
                .color(request.getColor())
                .imei(request.getImei())
                .issueDescription(request.getIssueDescription())
                .issueAudioUrl(request.getIssueAudioUrl())
                .estimatedPrice(request.getEstimatedPrice())
                .deviceDisplayName(request.getDeviceDisplayName())
                .deviceImageUrl(request.getDeviceImageUrl())
                .repairServicesSummary(request.getRepairServicesSummary())
                .priceItemsJson(request.getPriceItemsJson())
                .missingPartsJson(request.getMissingPartsJson())
                .devicePhotosJson(request.getDevicePhotosJson())
                .deviceSecurityType(request.getDeviceSecurityType())
                .deviceSecurityValue(request.getDeviceSecurityValue())
                .customerApproval(request.getCustomerApproval())
                .estimatedReadyAt(request.getEstimatedReadyAt())
                .estimatedDeliveryAt(request.getEstimatedDeliveryAt())
                .trackingId(trackingId)
                .status("CREATED")
                .build();
        // saveAndFlush + mirrorOnUpsertInline (REQUIRED propagation, not
        // REQUIRES_NEW) — the booking mirror writes repair_bookings.ticket_id
        // with a FK to tickets.id, and PlatformRepairBooking has only a plain
        // UUID (no @ManyToOne) so Hibernate can't infer insert order. Forcing
        // the ticket INSERT to flush first and keeping the mirror in the same
        // transaction means the FK passes.
        ticket = ticketRepository.saveAndFlush(ticket);
        customerOrderMirrorService.mirrorOnUpsertInline(ticket);
        return toResponse(ticket);
    }

    @Transactional
    public TicketResponse update(UUID shopId, UUID id, TicketRequest request) {
        Ticket ticket = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        // Snapshot the prior approval + estimate state. A re-edit can change
        // priceItemsJson / estimatedPrice (= service re-estimated) or flip
        // customerApproval; both feed the Service History timeline.
        boolean wasApproved = Boolean.TRUE.equals(ticket.getCustomerApproval());
        String oldPriceItemsJson = ticket.getPriceItemsJson();
        BigDecimal oldEstimatedPrice = ticket.getEstimatedPrice();
        ticket.setCustomerId(request.getCustomerId());
        if (request.getCustomerName() != null) ticket.setCustomerName(request.getCustomerName());
        if (request.getCustomerPhone() != null) ticket.setCustomerPhone(request.getCustomerPhone());
        ticket.setBrandId(request.getBrandId());
        ticket.setModelId(request.getModelId());
        ticket.setRamOptionId(request.getRamOptionId());
        ticket.setStorageOptionId(request.getStorageOptionId());
        ticket.setColor(request.getColor());
        ticket.setImei(request.getImei());
        ticket.setIssueDescription(request.getIssueDescription());
        ticket.setEstimatedPrice(request.getEstimatedPrice());
        ticket.setDeviceDisplayName(request.getDeviceDisplayName());
        ticket.setDeviceImageUrl(request.getDeviceImageUrl());
        ticket.setRepairServicesSummary(request.getRepairServicesSummary());
        ticket.setPriceItemsJson(request.getPriceItemsJson());
        ticket.setMissingPartsJson(request.getMissingPartsJson());
        ticket.setDevicePhotosJson(request.getDevicePhotosJson());
        ticket.setDeviceSecurityType(request.getDeviceSecurityType());
        ticket.setDeviceSecurityValue(request.getDeviceSecurityValue());
        ticket.setCustomerApproval(request.getCustomerApproval());
        ticket.setEstimatedReadyAt(request.getEstimatedReadyAt());
        ticket.setEstimatedDeliveryAt(request.getEstimatedDeliveryAt());
        // Re-edit semantics: when the shop edits a ticket the customer had
        // already approved AND the edit didn't carry a fresh approval, treat
        // it as a re-booking — clear the prior approval and refresh the
        // Waiting-for-Approval timeline row so the customer is re-prompted.
        boolean reEditAfterApproval = wasApproved
                && !Boolean.TRUE.equals(request.getCustomerApproval());
        if (reEditAfterApproval) {
            ticket.setCustomerApproval(null);
        }
        ticket = ticketRepository.save(ticket);
        customerOrderMirrorService.mirrorOnUpsert(ticket);
        if (reEditAfterApproval) {
            emitOrUpdateBookingEvent(ticket.getId(),
                    "WAITING_FOR_CUSTOMER_APPROVAL",
                    "Booking re-edited — waiting for customer approval",
                    "SHOP");
        }
        // Re-estimate: shop added/removed a service or changed the price on an
        // existing booking. Light up the "Service Re-estimated" rail row so the
        // customer + owner timelines reflect the change. emitOrUpdate refreshes
        // the timestamp so multiple re-edits surface as "latest re-estimate".
        boolean priceItemsChanged = !java.util.Objects.equals(oldPriceItemsJson, ticket.getPriceItemsJson());
        boolean estimateChanged = (oldEstimatedPrice == null)
                ? ticket.getEstimatedPrice() != null
                : (ticket.getEstimatedPrice() == null
                        || oldEstimatedPrice.compareTo(ticket.getEstimatedPrice()) != 0);
        if (priceItemsChanged || estimateChanged) {
            emitOrUpdateBookingEvent(ticket.getId(),
                    "RE_ESTIMATED_CONFIRMED",
                    "Service Re-estimated",
                    "SHOP");
        }
        // Shop-side approval flip (owner ticked "Customer Repair Approval" in
        // the edit flow): light up CUSTOMER_APPROVED. Customer-side approval
        // is emitted from RepairBookingController#customerApproval; emitBookingEvent
        // dedupes so an existing row won't double-write.
        if (!wasApproved && Boolean.TRUE.equals(ticket.getCustomerApproval())) {
            emitBookingEvent(ticket.getId(),
                    "CUSTOMER_APPROVED",
                    "Customer Approved",
                    "SHOP");
        }
        return toResponse(ticket);
    }

    @Transactional
    public void updateStatus(UUID shopId, UUID id, String status) {
        Ticket ticket = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
        ticket.setStatus(status);
        ticket = ticketRepository.save(ticket);
        customerOrderMirrorService.mirrorOnUpsert(ticket);
        emitStepEventsForTicketStatus(ticket.getId(), status);
    }

    /**
     * Lightweight PATCH that currently supports assigning technicians and a few optional fields
     * from a generic map payload. This is primarily used by the mobile "Assign Technician" flow.
     */
    @Transactional
    public TicketResponse patch(UUID shopId, UUID id, Map<String, Object> body) {
        Ticket ticket = ticketRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));

        UUID technicianBeforePatch = ticket.getAssignedTechnicianId();
        boolean wasApprovedBeforePatch = Boolean.TRUE.equals(ticket.getCustomerApproval());
        Technician assignedTechAfterPatch = null;

        // EditBookingScreen (mobile) PATCHes a small set of free-form fields.
        // Honor only the ones backed by a column; quietly ignore unknown keys.
        if (body.containsKey("imei")) {
            Object raw = body.get("imei");
            ticket.setImei(raw == null ? null : String.valueOf(raw));
        }
        if (body.containsKey("issueDescription")) {
            Object raw = body.get("issueDescription");
            ticket.setIssueDescription(raw == null ? null : String.valueOf(raw));
        }
        if (body.containsKey("estimatedDeliveryAt")) {
            Object raw = body.get("estimatedDeliveryAt");
            ticket.setEstimatedDeliveryAt(parseInstantOrNull(raw));
        }
        if (body.containsKey("estimatedReadyAt")) {
            Object raw = body.get("estimatedReadyAt");
            ticket.setEstimatedReadyAt(parseInstantOrNull(raw));
        }
        // Accept both shapes the mobile screens send: customerApproved (Edit
        // Booking checkbox) and customerApproval (PUT-aligned name).
        if (body.containsKey("customerApproved") || body.containsKey("customerApproval")) {
            Object raw = body.containsKey("customerApproved")
                    ? body.get("customerApproved")
                    : body.get("customerApproval");
            ticket.setCustomerApproval(parseBooleanOrNull(raw));
        }
        if (body.containsKey("assignedTechnicianId")) {
            Object raw = body.get("assignedTechnicianId");
            if (raw == null || String.valueOf(raw).isBlank()) {
                ticket.setAssignedTechnicianId(null);
            } else {
                UUID value = UUID.fromString(String.valueOf(raw));
                // Resolve to technician id: auth returns user id, ticket stores technicians.id
                Technician tech = technicianRepository.findByShopIdAndId(shopId, value)
                        .or(() -> technicianRepository.findByShopIdAndUserId(shopId, value))
                        .orElse(null);
                if (tech == null) {
                    // Auth may return user id with no technicians row; create one so assignment works
                    tech = technicianRepository.save(Technician.builder()
                            .shopId(shopId)
                            .userId(value)
                            .name("Technician")
                            .build());
                }
                ticket.setAssignedTechnicianId(tech.getId());
                assignedTechAfterPatch = tech;
            }
            // Owner-side (re)assignment ALWAYS clears the technician's prior
            // acceptance. A reassign puts the booking back into "awaiting
            // acceptance" — the new technician must tap Accept themselves
            // before the timeline lights up ACCEPTED / WORK_STARTED.
            ticket.setTechnicianAcceptedAt(null);
        }

        String statusBeforePatch = ticket.getStatus();
        if (body.containsKey("status")) {
            Object raw = body.get("status");
            ticket.setStatus(raw != null ? String.valueOf(raw) : ticket.getStatus());
        }

        // The technician detail screen PATCHes this whenever it adds a new
        // "Your Side Device Image". The screen sends the full JSON array each
        // time, so we just overwrite — no merge logic on the server.
        boolean photosBecameNonEmpty = false;
        if (body.containsKey("technicianPhotosJson")) {
            Object raw = body.get("technicianPhotosJson");
            String newValue = raw != null ? String.valueOf(raw) : null;
            // "Became non-empty" = the PATCH carries at least one URL. We don't
            // care about the prior value here; the booking-side event lookup
            // below dedupes so a re-submit with the same photos won't double-emit.
            photosBecameNonEmpty = hasAtLeastOneUrl(newValue);
            ticket.setTechnicianPhotosJson(newValue);
        }

        ticket = ticketRepository.save(ticket);
        customerOrderMirrorService.mirrorOnUpsert(ticket);

        // After the mirror has ensured the booking row exists, drop a
        // TECH_UPLOADED_IMAGES event so the customer/owner Service History
        // screens light up the "Technician uploaded device images" step. This
        // step key matches serviceHistoryPhases.js in repair-shop-mobile and
        // is rendered the same way on the owner side.
        if (photosBecameNonEmpty) {
            emitBookingEvent(ticket.getId(), "TECHNICIAN_UPLOADED_DEVICE_IMAGES",
                    "Technician Uploaded Device Images", "TECHNICIAN");
        }
        // If the patch carried a status change, emit the matching step event(s)
        // (work-started / waiting-for-approval). Use the prior status as a guard
        // so a no-op repaint doesn't churn events.
        if (ticket.getStatus() != null && !ticket.getStatus().equalsIgnoreCase(statusBeforePatch)) {
            emitStepEventsForTicketStatus(ticket.getId(), ticket.getStatus());
        }
        // Technician assignment changed — light up the customer/owner timeline
        // row that says "Assigned to <Tech>". emitBookingEvent is idempotent
        // on (booking, statusKey) so a re-save of the same technician id is
        // a no-op, but a re-assignment to a different technician fires
        // TECHNICIAN_REASSIGNED so the rail can show both steps.
        UUID technicianAfterPatch = ticket.getAssignedTechnicianId();
        boolean technicianChanged = !java.util.Objects.equals(technicianBeforePatch, technicianAfterPatch);
        if (technicianChanged && technicianAfterPatch != null) {
            String techName = assignedTechAfterPatch != null && assignedTechAfterPatch.getName() != null
                    ? assignedTechAfterPatch.getName()
                    : "Technician";
            if (technicianBeforePatch == null) {
                emitBookingEvent(ticket.getId(), "TECHNICIAN_ASSIGNED",
                        "Assigned to " + techName, "SHOP");
            } else {
                emitBookingEvent(ticket.getId(), "TECHNICIAN_REASSIGNED",
                        "Re-assigned to " + techName, "SHOP");
            }
        }
        // Owner ticked "Customer Repair Approval" in the Edit Booking screen.
        // Light up CUSTOMER_APPROVED on the timeline; emitBookingEvent dedupes
        // so a no-op re-save with the flag already true won't double-write.
        if (!wasApprovedBeforePatch && Boolean.TRUE.equals(ticket.getCustomerApproval())) {
            emitBookingEvent(ticket.getId(), "CUSTOMER_APPROVED",
                    "Customer Approved", "SHOP");
        }
        return toResponse(ticket);
    }

    private static java.time.Instant parseInstantOrNull(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return null;
        try { return java.time.Instant.parse(s); } catch (Exception ignored) {}
        try { return java.time.OffsetDateTime.parse(s).toInstant(); } catch (Exception ignored) {}
        return null;
    }

    private static Boolean parseBooleanOrNull(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Boolean b) return b;
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) return null;
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) return Boolean.FALSE;
        return null;
    }

    /**
     * Technician's explicit Accept action. Sets tickets.technician_accepted_at
     * to now() and emits TECHNICIAN_ACCEPTED_SERVICE + TECHNICIAN_WORK_STARTED
     * once. If the ticket was sitting at CREATED (walk-in flow), it also bumps
     * status to IN_DIAGNOSIS so the rest of the lifecycle plays out as before.
     *
     * Authorisation: the JWT user must be the technician currently assigned
     * to this ticket (technicians.user_id → assigned_technician_id).
     * Idempotent — calling again after acceptance is a no-op.
     */
    @Transactional
    public TicketResponse acceptByTechnician(UUID shopId, UUID userId, UUID ticketId) {
        Ticket ticket = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        if (ticket.getAssignedTechnicianId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ticket has no assigned technician");
        }
        Technician me = technicianRepository.findByShopIdAndUserId(shopId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "not a technician of this shop"));
        if (!ticket.getAssignedTechnicianId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ticket is assigned to a different technician");
        }
        if (ticket.getTechnicianAcceptedAt() == null) {
            ticket.setTechnicianAcceptedAt(java.time.Instant.now());
            // Walk-in tickets are minted at CREATED; pickup-flow tickets at
            // IN_DIAGNOSIS. Accepting from CREATED also advances status —
            // pickup-flow tickets are already at IN_DIAGNOSIS so the status
            // stays put.
            if ("CREATED".equalsIgnoreCase(ticket.getStatus())) {
                ticket.setStatus("IN_DIAGNOSIS");
            }
            ticket = ticketRepository.save(ticket);
            customerOrderMirrorService.mirrorOnUpsert(ticket);

            // Emit the customer/owner timeline events now that acceptance is
            // proven. emitBookingEvent dedupes so a stale auto-fire from a
            // pre-fix run won't double-write.
            String techName = me.getName() != null ? me.getName() : "Technician";
            emitBookingEvent(ticket.getId(), "TECHNICIAN_ACCEPTED_SERVICE",
                    techName + " accepted the service", "TECHNICIAN");
            emitBookingEvent(ticket.getId(), "TECHNICIAN_WORK_STARTED",
                    "Technician Work Started", "TECHNICIAN");
        }
        return toResponse(ticket);
    }

    private static boolean hasAtLeastOneUrl(String json) {
        if (json == null) return false;
        String trimmed = json.trim();
        // Cheap parse: any "http" substring inside the JSON array/object means
        // the technician submitted at least one uploaded media URL. Avoids
        // pulling in ObjectMapper for a one-liner check.
        return !trimmed.isEmpty() && !trimmed.equals("[]") && trimmed.contains("http");
    }

    // Idempotent event emit for the customer/owner Service History rail. Looks
    // up the booking mirrored against the ticket; skips if an event with the
    // same status key already exists so re-saves don't double-emit.
    private void emitBookingEvent(UUID ticketId, String statusKey, String note, String actor) {
        platformRepairBookingRepository.findByTicketId(ticketId).ifPresent(booking -> {
            boolean alreadyEmitted = platformRepairBookingEventRepository
                    .findByBookingIdOrderByCreatedAtAsc(booking.getId())
                    .stream()
                    .anyMatch(e -> statusKey.equalsIgnoreCase(e.getStatus()));
            if (alreadyEmitted) return;
            platformRepairBookingEventRepository.save(PlatformRepairBookingEvent.builder()
                    .bookingId(booking.getId())
                    .status(statusKey)
                    .note(note)
                    .actor(actor)
                    .build());
            // Ping the customer's Notifications screen for the same status,
            // so they see one entry per real-life update (technician
            // uploaded images, issue verified, repair completed, etc.).
            // The mirror's template map silently drops low-signal statuses
            // and walk-in (no customer_user_id) cases.
            customerOrderMirrorService.emitCustomerNotificationForStatus(booking.getId(), statusKey);
        });
    }

    /** Manual emit endpoint for service-progress checklist rows on the
     *  technician's Ticket Detail screen — Repair Work In Progress, Parts
     *  Required, Parts Replaced, Quality Check Started/Completed, Repair
     *  Completed. Idempotent: re-submitting refreshes the existing row's
     *  note + timestamp instead of inserting a duplicate. */
    private static final java.util.Set<String> ALLOWED_PROGRESS_STEP_KEYS = java.util.Set.of(
            "IN_REPAIR", "PARTS_REQUIRED", "PARTS_REPLACED",
            "QUALITY_CHECK_STARTED", "QUALITY_CHECK_COMPLETED", "REPAIR_COMPLETED",
            // READY -> billing/handover sub-flow -> DELIVERED. Each substep is
            // its own emit so the customer history rail surfaces the invoice and
            // handover states distinctly instead of skipping straight to
            // "Delivered to Customer".
            "READY", "INVOICE_GENERATED", "INVOICE_READY", "DELIVERED_PROCESSING",
            "DELIVERED", "CANCELLED",
            // RETURN_DELIVERY is the "device not repaired, returning as-is"
            // counterpart to READY. Added so the technician can mark a job
            // returned without going through the full Repair Completed path.
            "RETURN_DELIVERY",
            // REPAIR_NOT_COMPLETED is the technician's "tried but couldn't fix"
            // signal — surfaced on the customer / shop history rail with the
            // canonical "Your repair is not completed" note. Does NOT advance
            // ticket.status; the row is for visibility only.
            "REPAIR_NOT_COMPLETED");

    private static final java.util.Set<String> ALLOWED_PROGRESS_ACTORS = java.util.Set.of(
            "TECHNICIAN", "OWNER", "SHOP");

    @Transactional
    public void emitProgressStepEvent(UUID shopId, UUID ticketId, String statusKey, String note, String actor) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        String key = statusKey == null ? "" : statusKey.trim().toUpperCase();
        if (!ALLOWED_PROGRESS_STEP_KEYS.contains(key)) {
            throw new IllegalArgumentException("Status key not allowed: " + key);
        }
        String text = note != null && !note.isBlank() ? note.trim() : defaultProgressLabel(key);
        String a = actor == null ? "" : actor.trim().toUpperCase();
        if (!ALLOWED_PROGRESS_ACTORS.contains(a)) a = "TECHNICIAN";
        emitOrUpdateBookingEvent(t.getId(), key, text, a);
        // The BookingsHistory list reads ticket.status directly, so a work-status
        // event alone (Parts Required, Repair Completed, Delivered to Customer,
        // ...) used to leave the badge stuck on the previous lifecycle status.
        // Resolve the admin-managed master row for this code and advance
        // ticket.status to its `ticket_status` mapping so the list badge moves
        // forward (IN_REPAIR → READY → DELIVERED). Falls through silently when
        // the code isn't in master (defensive — keeps the event write working).
        advanceTicketStatusForWorkCode(t, key);
    }

    // Only advance forward through the lifecycle — never demote a DELIVERED
    // ticket back to IN_REPAIR because an older code was re-submitted, and
    // never override CANCELLED. The post-READY billing/handover substages
    // (INVOICE_GENERATED, INVOICE_READY, DELIVERED_PROCESSING) sit between
    // READY and DELIVERED so a Ready ticket can't jump straight to Delivered
    // without the invoice + handover steps being recorded first.
    private static final java.util.List<String> LIFECYCLE_ORDER = java.util.List.of(
            "CREATED", "IN_DIAGNOSIS", "QUOTED", "APPROVED", "IN_REPAIR",
            "READY", "INVOICE_GENERATED", "INVOICE_READY", "DELIVERED_PROCESSING",
            "DELIVERED");

    private void advanceTicketStatusForWorkCode(Ticket t, String code) {
        if (code == null || code.isBlank()) return;
        String normalized = code.trim().toUpperCase();

        // Primary: look up the admin-managed master row.
        String target = masterWorkStatusRepository.findByCodeIgnoreCase(normalized)
                .map(row -> row.getTicketStatus() == null ? null : row.getTicketStatus().trim().toUpperCase())
                .filter(s -> !s.isBlank())
                .orElse(null);

        // Fallback: when the work-status key IS itself a lifecycle status
        // (DELIVERED, CANCELLED, READY, …), use it directly. This covers the
        // case where the master row exists under a different code (e.g.
        // DELIVERED_TO_CUSTOMER) so the badge would otherwise be stuck at
        // READY after the technician's "Delivered to Customer" submission.
        if (target == null && (LIFECYCLE_ORDER.contains(normalized) || "CANCELLED".equals(normalized))) {
            target = normalized;
        }
        if (target == null || target.isBlank()) return;

        String current = t.getStatus() == null ? "" : t.getStatus().trim().toUpperCase();
        if (target.equals(current)) return;
        // CANCELLED / RETURNED are terminal — don't overwrite them.
        if ("CANCELLED".equals(current) || "RETURNED".equals(current)) return;
        int currentIdx = LIFECYCLE_ORDER.indexOf(current);
        int targetIdx = LIFECYCLE_ORDER.indexOf(target);
        // Both inside the linear ladder → only move forward.
        if (currentIdx >= 0 && targetIdx >= 0 && targetIdx < currentIdx) return;
        t.setStatus(target);
        ticketRepository.save(t);
        customerOrderMirrorService.mirrorOnUpsert(t);
    }

    private static String defaultProgressLabel(String key) {
        switch (key) {
            case "IN_REPAIR":              return "Repair Work In Progress";
            case "PARTS_REQUIRED":         return "Spare Parts Waiting";
            case "PARTS_REPLACED":         return "Parts Replaced";
            case "QUALITY_CHECK_STARTED":  return "Quality Check Started";
            case "QUALITY_CHECK_COMPLETED":return "Quality Check Completed";
            case "REPAIR_COMPLETED":       return "Repair Completed";
            case "REPAIR_NOT_COMPLETED":   return "Your repair is not completed";
            case "INVOICE_GENERATED":      return "Invoice Generated";
            case "INVOICE_READY":          return "Invoice Ready";
            case "DELIVERED_PROCESSING":   return "Delivered to Customer Processing";
            case "READY":                  return "Ready for Delivery";
            case "DELIVERED":              return "Delivered to Customer";
            case "CANCELLED":              return "Work Cancelled";
            default:                       return key;
        }
    }

    // Same as emitBookingEvent but refreshes the note text and timestamp on
    // an existing row instead of skipping it. Used for steps whose detail
    // changes on each invocation — e.g. compliance notes (latest note text
    // should display) and re-edit-driven approval requests (latest timestamp).
    private void emitOrUpdateBookingEvent(UUID ticketId, String statusKey, String note, String actor) {
        emitOrUpdateBookingEvent(ticketId, statusKey, note, actor, null, null);
    }

    private void emitOrUpdateBookingEvent(UUID ticketId, String statusKey, String note, String actor,
                                          String audioUrl, String imagesJson) {
        platformRepairBookingRepository.findByTicketId(ticketId).ifPresent(booking -> {
            var existing = platformRepairBookingEventRepository
                    .findByBookingIdOrderByCreatedAtAsc(booking.getId())
                    .stream()
                    .filter(e -> statusKey.equalsIgnoreCase(e.getStatus()))
                    .findFirst();
            if (existing.isPresent()) {
                PlatformRepairBookingEvent e = existing.get();
                e.setNote(note);
                e.setActor(actor);
                // Re-emit overwrites media too so editing a compliance note
                // (re-recording the voice clip, swapping an image) lands on
                // the customer/owner timeline row right away.
                e.setAudioUrl(audioUrl);
                e.setImagesJson(imagesJson);
                // Refresh the timestamp so the customer/owner timeline rail
                // reflects this as the most recent action — required because
                // the dedup keyed by status would otherwise keep the original
                // (now stale) createdAt.
                e.setCreatedAt(java.time.Instant.now());
                platformRepairBookingEventRepository.save(e);
            } else {
                platformRepairBookingEventRepository.save(PlatformRepairBookingEvent.builder()
                        .bookingId(booking.getId())
                        .status(statusKey)
                        .note(note)
                        .actor(actor)
                        .audioUrl(audioUrl)
                        .imagesJson(imagesJson)
                        .build());
                // First-time emit of this status → ping the customer's
                // Notifications screen. Update branch above intentionally
                // skips this so re-emits (e.g., the technician editing a
                // verified note) don't spam the same alert twice.
                customerOrderMirrorService.emitCustomerNotificationForStatus(booking.getId(), statusKey);
            }
        });
    }

    // Emit the In-Service-Process step events implied by a ticket status:
    //   IN_REPAIR    → TECH_WORK_STARTED   ("Technician Work Started")
    //   QUOTED       → WAITING_APPROVAL    ("Waiting for Customer Approval")
    //   APPROVED     → also TECH_WORK_STARTED — when the customer approves the
    //                 quote, work resumes; this ensures the timeline reflects it
    //                 even if updateStatus was skipped server-side.
    // Step keys match serviceHistoryPhases.js (repair-shop-mobile).
    private void emitStepEventsForTicketStatus(UUID ticketId, String status) {
        if (status == null) return;
        String s = status.trim().toUpperCase();
        switch (s) {
            case "IN_REPAIR":
            case "APPROVED":
                emitBookingEvent(ticketId, "TECHNICIAN_WORK_STARTED",
                        "Technician Work Started", "TECHNICIAN");
                break;
            case "QUOTED":
                emitBookingEvent(ticketId, "WAITING_FOR_CUSTOMER_APPROVAL",
                        "Waiting for Customer Approval", "TECHNICIAN");
                break;
            default:
                // No step event for CREATED / IN_DIAGNOSIS / READY / DELIVERED /
                // CANCELLED — those map to phase-level transitions instead.
                break;
        }
    }

    private String generateTrackingId(UUID shopId) {
        String suffix = String.valueOf(System.currentTimeMillis() % 10000000);
        return TRACKING_PREFIX + suffix;
    }

    /** Customer-facing read: masks PIN/pattern value, joins technician name+code. */
    private TicketResponse toCustomerResponse(Ticket t) {
        TicketResponse base = toResponse(t);
        base.setDeviceSecurityValue(null);
        if (t.getAssignedTechnicianId() != null) {
            technicianRepository.findById(t.getAssignedTechnicianId()).ifPresent(tech -> {
                base.setAssignedTechnicianName(tech.getName());
                base.setAssignedTechnicianCode(
                        t.getAssignedTechnicianId().toString().substring(0, 8).toUpperCase());
            });
        }
        return base;
    }

    private TicketResponse toResponse(Ticket t) {
        // Resolve the few "booking-side" fields once — these all share the
        // same fallback shape (use the ticket column if present, otherwise
        // pull from the linked repair_booking). Doing the lookup once means
        // we hit platformRepairBookingRepository at most once per ticket
        // render rather than once per field.
        TicketBookingFallback fallback = resolveBookingFallback(t);
        // Latest customer-visible compliance note → flat fields the detail
        // screens render on the "Issue Verified & Updated" card. Internal-
        // only notes are filtered out so private shop chatter never leaks
        // to the customer payload.
        RepairNote complianceNote = repairNoteRepository
                .findByTicketIdOrderByCreatedAtDesc(t.getId()).stream()
                .filter(n -> !Boolean.TRUE.equals(n.getIsInternal()))
                .findFirst()
                .orElse(null);
        List<String> complianceImages = complianceNote != null
                ? parseImagesJson(complianceNote.getImagesJson())
                : Collections.emptyList();
        return TicketResponse.builder()
                .id(t.getId())
                .shopId(t.getShopId())
                .customerId(t.getCustomerId())
                .customerName(fallback.customerName)
                .customerPhone(fallback.customerPhone)
                .customerAddress(fallback.customerAddress)
                .assignedTechnicianId(t.getAssignedTechnicianId())
                .technicianAcceptedAt(t.getTechnicianAcceptedAt())
                .trackingId(t.getTrackingId())
                .brandId(t.getBrandId())
                .modelId(t.getModelId())
                .ramOptionId(t.getRamOptionId())
                .storageOptionId(t.getStorageOptionId())
                .color(t.getColor())
                .status(t.getStatus())
                .estimatedPrice(t.getEstimatedPrice())
                .finalPrice(t.getFinalPrice())
                .issueDescription(t.getIssueDescription())
                .issueAudioUrl(t.getIssueAudioUrl())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .deviceDisplayName(t.getDeviceDisplayName())
                .deviceImageUrl(t.getDeviceImageUrl())
                .repairServicesSummary(t.getRepairServicesSummary())
                .priceItemsJson(t.getPriceItemsJson())
                .missingPartsJson(fallback.missingPartsJson)
                .devicePhotosJson(fallback.devicePhotosJson)
                .technicianPhotosJson(t.getTechnicianPhotosJson())
                .deviceSecurityType(t.getDeviceSecurityType())
                .deviceSecurityValue(fallback.deviceSecurityValue)
                .customerApproval(fallback.customerApproval)
                .estimatedReadyAt(fallback.estimatedReadyAt)
                .estimatedDeliveryAt(fallback.estimatedDeliveryAt)
                .complianceNote(complianceNote != null ? complianceNote.getNote() : null)
                .complianceAudioUrl(complianceNote != null ? complianceNote.getAudioUrl() : null)
                .complianceImageUrls(complianceImages)
                .complianceVerifiedAt(complianceNote != null ? complianceNote.getCreatedAt() : null)
                .build();
    }

    /**
     * Holds the half-dozen fields that the booking detail screens read but
     * that ticket-side columns may not yet have populated (because the
     * pickup person filled them in on the repair_booking row before the
     * shop minted the ticket). Each field prefers the ticket's own value
     * and falls back to the linked PlatformRepairBooking — so tickets minted
     * before mintTicketFromBooking learned to copy these snapshot fields
     * still render correctly on the owner Device Details screen.
     */
    private static final class TicketBookingFallback {
        String customerName;
        String customerPhone;
        String customerAddress;
        String devicePhotosJson;
        String missingPartsJson;
        String deviceSecurityValue;
        Boolean customerApproval;
        Instant estimatedReadyAt;
        Instant estimatedDeliveryAt;
    }

    private TicketBookingFallback resolveBookingFallback(Ticket t) {
        TicketBookingFallback f = new TicketBookingFallback();
        f.customerName = t.getCustomerName();
        f.customerPhone = t.getCustomerPhone();
        f.customerAddress = t.getCustomerAddress();
        f.devicePhotosJson = t.getDevicePhotosJson();
        f.missingPartsJson = t.getMissingPartsJson();
        f.deviceSecurityValue = t.getDeviceSecurityValue();
        f.customerApproval = t.getCustomerApproval();
        f.estimatedReadyAt = t.getEstimatedReadyAt();
        f.estimatedDeliveryAt = t.getEstimatedDeliveryAt();
        if (!needsBookingFallback(f)) return f;
        platformRepairBookingRepository.findByTicketId(t.getId()).ifPresent(b -> {
            if (isBlankStr(f.customerName) && b.getCustomerName() != null && !b.getCustomerName().isBlank()) {
                f.customerName = b.getCustomerName();
            }
            if (isBlankStr(f.customerPhone) && b.getCustomerMobile() != null && !b.getCustomerMobile().isBlank()) {
                f.customerPhone = b.getCustomerMobile();
            }
            if (isBlankStr(f.customerAddress) && b.getPickupAddressId() != null) {
                platformCustomerAddressRepository.findById(b.getPickupAddressId()).ifPresent(addr -> {
                    String joined = joinAddressParts(
                            addr.getAddressLine(), addr.getLocality(),
                            addr.getCity(), addr.getState(), addr.getPincode());
                    if (joined != null) f.customerAddress = joined;
                });
            }
            if (isBlankJson(f.devicePhotosJson)) {
                Map<String, String> photos = new HashMap<>();
                if (b.getFrontImageUrl() != null) photos.put("front", b.getFrontImageUrl());
                if (b.getBackImageUrl()  != null) photos.put("back",  b.getBackImageUrl());
                if (b.getVideoUrl()      != null) photos.put("video", b.getVideoUrl());
                if (!photos.isEmpty()) {
                    try { f.devicePhotosJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(photos); }
                    catch (Exception ignore) {}
                }
            }
            if (isBlankJson(f.missingPartsJson) && b.getMissingDamageParts() != null
                    && !b.getMissingDamageParts().isBlank()) {
                String raw = b.getMissingDamageParts().trim();
                if (raw.startsWith("[")) {
                    f.missingPartsJson = raw;
                } else {
                    List<String> parts = new ArrayList<>();
                    for (String piece : raw.split("[,\\n]")) {
                        String p = piece.trim();
                        if (!p.isEmpty()) parts.add(p);
                    }
                    if (!parts.isEmpty()) {
                        try { f.missingPartsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(parts); }
                        catch (Exception ignore) {}
                    }
                }
            }
            if ((f.deviceSecurityValue == null || f.deviceSecurityValue.isBlank())
                    && b.getDevicePin() != null && !b.getDevicePin().isBlank()) {
                f.deviceSecurityValue = b.getDevicePin();
            }
            if (f.customerApproval == null && b.getCustomerApproval() != null) {
                String s = b.getCustomerApproval().trim().toUpperCase();
                if (s.equals("DONE") || s.equals("TRUE") || s.equals("YES") || s.equals("APPROVED")) {
                    f.customerApproval = Boolean.TRUE;
                } else if (s.equals("PENDING") || s.equals("FALSE") || s.equals("NO")) {
                    f.customerApproval = Boolean.FALSE;
                }
            }
            if (f.estimatedReadyAt == null && b.getEstimatedReadyAt() != null) {
                f.estimatedReadyAt = b.getEstimatedReadyAt();
            }
            if (f.estimatedDeliveryAt == null && b.getEstimatedDeliveryAt() != null) {
                f.estimatedDeliveryAt = b.getEstimatedDeliveryAt();
            }
            // Customer-flow pickup bookings (order-service RepairBookingController.create)
            // store only customer_user_id; the denormalized customer_name/customer_mobile
            // columns stay NULL. Without this reach into customer_users, the owner's
            // Bookings History card and the Booking Details "Customer Details" pane
            // both render blank for every customer-placed pickup.
            if ((isBlankStr(f.customerName) || isBlankStr(f.customerPhone))
                    && b.getCustomerUserId() != null) {
                platformCustomerUserRepository.findById(b.getCustomerUserId()).ifPresent(cu -> {
                    if (isBlankStr(f.customerName) && cu.getFullName() != null && !cu.getFullName().isBlank()) {
                        f.customerName = cu.getFullName();
                    }
                    if (isBlankStr(f.customerPhone) && cu.getMobile() != null && !cu.getMobile().isBlank()) {
                        f.customerPhone = cu.getMobile();
                    }
                });
            }
        });
        return f;
    }

    private static boolean needsBookingFallback(TicketBookingFallback f) {
        return isBlankStr(f.customerName)
                || isBlankStr(f.customerPhone)
                || isBlankStr(f.customerAddress)
                || isBlankJson(f.devicePhotosJson)
                || isBlankJson(f.missingPartsJson)
                || isBlankStr(f.deviceSecurityValue)
                || f.customerApproval == null
                || f.estimatedReadyAt == null
                || f.estimatedDeliveryAt == null;
    }

    private static boolean isBlankStr(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isBlankJson(String s) {
        if (s == null) return true;
        String trim = s.trim();
        return trim.isEmpty() || trim.equals("{}") || trim.equals("[]");
    }

    private static String joinAddressParts(String line, String locality, String city, String state, String pincode) {
        List<String> parts = new ArrayList<>();
        if (line     != null && !line.isBlank())     parts.add(line.trim());
        if (locality != null && !locality.isBlank()) parts.add(locality.trim());
        if (city     != null && !city.isBlank())     parts.add(city.trim());
        if (state    != null && !state.isBlank())    parts.add(state.trim());
        if (pincode  != null && !pincode.isBlank())  parts.add(pincode.trim());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    // Booking-side field fallback lives in resolveBookingFallback() above —
    // it covers device_photos_json AND missing_parts_json, device_security_value,
    // customer_approval, estimated_ready_at, estimated_delivery_at in a single
    // booking lookup, so the booking detail screens render the same data
    // whether the ticket was minted before or after mintTicketFromBooking
    // learned to copy these snapshot fields at mint time.

    // ---------- Repair notes ----------------------------------------------

    @Transactional
    public RepairNoteResponse addRepairNote(UUID shopId, UUID ticketId, UUID authorId, CreateRepairNoteRequest body) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        // Serialize optional image attachments as a JSON array string in the
        // images_json TEXT column (matches how tickets.device_photos_json /
        // tickets.technician_photos_json are stored). Empty list collapses to
        // null so reads can short-circuit on isBlank.
        String imagesJson = null;
        if (body.getImageUrls() != null && !body.getImageUrls().isEmpty()) {
            try {
                imagesJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(body.getImageUrls());
            } catch (Exception ignored) { /* leave null on malformed input */ }
        }
        String audioUrl = body.getAudioUrl();
        if (audioUrl != null && audioUrl.isBlank()) audioUrl = null;
        RepairNote saved = repairNoteRepository.save(RepairNote.builder()
                .ticketId(t.getId())
                .authorId(authorId)
                .note(body.getNote())
                .isInternal(Boolean.TRUE.equals(body.getIsInternal()))
                .audioUrl(audioUrl)
                .imagesJson(imagesJson)
                .build());
        // Customer-visible compliance notes light up the "Technician Issue
        // Verified & Updated" step. Internal-only notes stay off the timeline
        // so the customer doesn't see private shop chatter. The event note
        // carries the technician's actual text so the customer sees what was
        // verified, not just a canned label.
        if (!Boolean.TRUE.equals(body.getIsInternal())) {
            String noteText = body.getNote() != null && !body.getNote().isBlank()
                    ? body.getNote()
                    : "Technician Issue Verified & Updated";
            // Carry the voice-note + image attachments onto the timeline event
            // so the customer / owner Issue Verified row can render the media
            // inline without a separate fetch from repair_notes.
            emitOrUpdateBookingEvent(t.getId(),
                    "TECHNICIAN_COMPLIANCE_ISSUE_VERIFIED_UPDATED",
                    noteText, "TECHNICIAN",
                    audioUrl, imagesJson);
        }
        return toNoteResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RepairNoteResponse> listRepairNotes(UUID shopId, UUID ticketId) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        return repairNoteRepository.findByTicketIdOrderByCreatedAtDesc(t.getId()).stream()
                .map(this::toNoteResponse)
                .toList();
    }

    private RepairNoteResponse toNoteResponse(RepairNote n) {
        return RepairNoteResponse.builder()
                .id(n.getId())
                .ticketId(n.getTicketId())
                .authorId(n.getAuthorId())
                .note(n.getNote())
                .isInternal(n.getIsInternal())
                .audioUrl(n.getAudioUrl())
                .imageUrls(parseImagesJson(n.getImagesJson()))
                .createdAt(n.getCreatedAt())
                .build();
    }

    // Re-hydrate the images_json TEXT column (stored as ["url", ...]) into a
    // List<String>. Shared between repair_notes responses and timeline event
    // responses so both surfaces parse the same shape.
    private static List<String> parseImagesJson(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        try {
            List<?> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(raw, List.class);
            List<String> urls = new ArrayList<>();
            for (Object o : parsed) {
                if (o != null) urls.add(o.toString());
            }
            return urls;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    // ---------- Solution packs --------------------------------------------

    @Transactional
    public SolutionPackResponse addSolutionPack(UUID shopId, UUID ticketId, UUID uploadedBy, CreateSolutionPackRequest body) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        String type = body.getPackType() == null ? "NEW" : body.getPackType().trim().toUpperCase();
        if (!"REFERENCE".equals(type) && !"NEW".equals(type)) type = "NEW";
        TicketSolutionPack saved = ticketSolutionPackRepository.save(TicketSolutionPack.builder()
                .ticketId(t.getId())
                .shopId(t.getShopId())
                .packType(type)
                .title(body.getTitle())
                .description(body.getDescription())
                .fileUrl(body.getFileUrl())
                .fileName(body.getFileName())
                .uploadedBy(uploadedBy)
                .brandId(body.getBrandId())
                .modelId(body.getModelId())
                .brandName(body.getBrandName())
                .modelName(body.getModelName())
                .issueCategory(body.getIssueCategory())
                .issueSubcategory(body.getIssueSubcategory())
                .issueCategoryId(body.getIssueCategoryId())
                .issueSubcategoryId(body.getIssueSubcategoryId())
                .filesJson(body.getFilesJson())
                .build());
        // A new (technician-uploaded) solution pack signals the "Issue identified"
        // step on the customer/owner history rail. REFERENCE packs are just the
        // tech viewing existing knowledge-base entries, so they don't count.
        if ("NEW".equals(type)) {
            String note = body.getIssueCategory() != null && !body.getIssueCategory().isBlank()
                    ? "Issue identified: " + body.getIssueCategory()
                            + (body.getIssueSubcategory() != null && !body.getIssueSubcategory().isBlank()
                                    ? " — " + body.getIssueSubcategory() : "")
                    : "Issue identified by technician";
            emitBookingEvent(t.getId(), "ISSUE_IDENTIFIED", note, "TECHNICIAN");
        }
        return toPackResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SolutionPackResponse> searchSolutionPacks(UUID shopId, String packType,
                                                          UUID brandId, UUID modelId,
                                                          UUID issueCategoryId, UUID issueSubcategoryId,
                                                          String issueCategory, String issueSubcategory) {
        String type = packType == null || packType.isBlank() ? null : packType.trim().toUpperCase();
        String cat = issueCategory == null || issueCategory.isBlank() ? null : issueCategory.trim();
        String sub = issueSubcategory == null || issueSubcategory.isBlank() ? null : issueSubcategory.trim();
        return ticketSolutionPackRepository
                .searchByShop(shopId, type, brandId, modelId, issueCategoryId, issueSubcategoryId, cat, sub)
                .stream().map(this::toPackResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SolutionPackResponse> listSolutionPacks(UUID shopId, UUID ticketId, String packType) {
        Ticket t = ticketRepository.findByShopIdAndId(shopId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
        List<TicketSolutionPack> rows = packType == null || packType.isBlank()
                ? ticketSolutionPackRepository.findByTicketIdOrderByCreatedAtDesc(t.getId())
                : ticketSolutionPackRepository.findByTicketIdAndPackTypeOrderByCreatedAtDesc(t.getId(), packType.trim().toUpperCase());
        return rows.stream().map(this::toPackResponse).toList();
    }

    private SolutionPackResponse toPackResponse(TicketSolutionPack p) {
        return SolutionPackResponse.builder()
                .id(p.getId())
                .ticketId(p.getTicketId())
                .shopId(p.getShopId())
                .packType(p.getPackType())
                .title(p.getTitle())
                .description(p.getDescription())
                .fileUrl(p.getFileUrl())
                .fileName(p.getFileName())
                .uploadedBy(p.getUploadedBy())
                .brandId(p.getBrandId())
                .modelId(p.getModelId())
                .brandName(p.getBrandName())
                .modelName(p.getModelName())
                .issueCategory(p.getIssueCategory())
                .issueSubcategory(p.getIssueSubcategory())
                .issueCategoryId(p.getIssueCategoryId())
                .issueSubcategoryId(p.getIssueSubcategoryId())
                .filesJson(p.getFilesJson())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
