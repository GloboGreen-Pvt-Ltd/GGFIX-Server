package com.repairshop.saas.ticket.controller;

import com.repairshop.saas.ticket.dto.TechnicianKycDocumentDtos.*;
import com.repairshop.saas.ticket.entity.Technician;
import com.repairshop.saas.ticket.entity.TechnicianKycDocument;
import com.repairshop.saas.ticket.exception.ResourceNotFoundException;
import com.repairshop.saas.ticket.repository.TechnicianKycDocumentRepository;
import com.repairshop.saas.ticket.repository.TechnicianRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * KYC document storage for the technician (employee) mobile app.
 *
 * Mirrors ShopKycDocumentController (shop-service) but keyed by technician_id
 * instead of shop_id. The employee app only needs Aadhar (front + back) + PAN,
 * but the controller is type-agnostic — any docType the client sends is stored
 * verbatim.
 *
 * Endpoints:
 *   GET    /technicians/me/kyc-documents               — list current tech's docs
 *   POST   /technicians/me/kyc-documents               — batch upsert (one entry
 *                                                        per docType replaces the
 *                                                        previous row in place)
 *   DELETE /technicians/me/kyc-documents/{docType}     — remove a single doc
 *   GET    /technicians/{id}/kyc-documents             — list any tech's docs
 *                                                        (owner-side view)
 *
 * Hosted URLs come from master-service's /media/upload. This controller only
 * stores the metadata + URL pointer.
 */
@RestController
@RequestMapping("/technicians")
@RequiredArgsConstructor
@Tag(name = "Technician KYC", description = "Technician personal KYC documents")
@SecurityRequirement(name = "Bearer")
@Slf4j
public class TechnicianKycDocumentController {

    private final TechnicianKycDocumentRepository kycRepo;
    private final TechnicianRepository technicianRepository;

    private UUID shopIdFrom(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        return sid != null ? UUID.fromString(sid) : null;
    }

    private UUID userIdFrom(HttpServletRequest request) {
        String uid = (String) request.getAttribute("userId");
        return uid != null ? UUID.fromString(uid) : null;
    }

    @SuppressWarnings("unchecked")
    private boolean isOwnerOrAdmin(HttpServletRequest request) {
        Object r = request.getAttribute("roles");
        List<String> roles = (r instanceof List) ? (List<String>) r : List.of();
        return roles.contains("SHOP_OWNER") || roles.contains("SUPER_ADMIN");
    }

    /** Resolves the current technician row using the same (shopId, userId)
     *  pair TechnicianController uses for /me endpoints. */
    private Technician currentTechnician(HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        UUID userId = userIdFrom(request);
        if (shopId == null || userId == null) {
            throw new IllegalStateException("Missing shop or user context");
        }
        return technicianRepository.findByShopIdAndUserId(shopId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Technician not found for current session"));
    }

    @GetMapping("/me/kyc-documents")
    @Operation(summary = "List KYC documents for the current technician")
    public ResponseEntity<List<TechnicianKycDocumentResponse>> listMine(HttpServletRequest request) {
        Technician me = currentTechnician(request);
        return ResponseEntity.ok(loadDocs(me.getId()));
    }

    @PostMapping("/me/kyc-documents")
    @Transactional
    @Operation(summary = "Batch upsert KYC documents for the current technician")
    public ResponseEntity<?> saveMine(
            @RequestBody SaveTechnicianKycRequest body,
            HttpServletRequest request) {
        Technician me = currentTechnician(request);
        if (body == null || body.getDocuments() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing 'documents' in request body"));
        }
        try {
            int saved = 0;
            for (TechnicianKycDocumentInput doc : body.getDocuments()) {
                if (doc.getDocType() == null || doc.getDocType().isBlank()) continue;
                if (doc.getUrl() == null || doc.getUrl().isBlank()) continue;
                TechnicianKycDocument entity = kycRepo
                        .findByTechnicianIdAndDocType(me.getId(), doc.getDocType())
                        .orElseGet(() -> TechnicianKycDocument.builder()
                                .technicianId(me.getId())
                                .docType(doc.getDocType())
                                .build());
                entity.setTitle(doc.getTitle() != null ? doc.getTitle() : doc.getDocType());
                entity.setUrl(doc.getUrl());
                entity.setIsRequired(Boolean.TRUE.equals(doc.getRequired()));
                entity.setStatus("PENDING_REVIEW");
                entity.setRejectReason(null);
                kycRepo.save(entity);
                saved++;
            }
            log.info("Technician KYC save: technicianId={} saved={}", me.getId(), saved);
            return ResponseEntity.ok(loadDocs(me.getId()));
        } catch (Exception e) {
            log.error("Technician KYC save failed for technicianId=" + me.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getClass().getSimpleName() + ": "
                            + (e.getMessage() == null ? "no message" : e.getMessage())
            ));
        }
    }

    @DeleteMapping("/me/kyc-documents/{docType}")
    @Transactional
    @Operation(summary = "Remove a KYC document for the current technician")
    public ResponseEntity<Void> deleteMine(
            @PathVariable String docType,
            HttpServletRequest request) {
        Technician me = currentTechnician(request);
        kycRepo.deleteByTechnicianIdAndDocType(me.getId(), docType);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/kyc-documents")
    @Operation(summary = "List KYC documents for any technician in this shop (owner view)")
    public ResponseEntity<List<TechnicianKycDocumentResponse>> listForTechnician(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID shopId = shopIdFrom(request);
        if (shopId == null) throw new IllegalStateException("Missing shop context");
        // Aadhaar/PAN are highly sensitive: only the owner/admin may view another
        // technician's KYC. An employee may only read their OWN (which they'd
        // normally fetch via /me/kyc-documents anyway).
        if (!isOwnerOrAdmin(request) && !currentTechnician(request).getId().equals(id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You can only view your own KYC documents.");
        }
        Technician tech = technicianRepository.findByShopIdAndId(shopId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + id));
        return ResponseEntity.ok(loadDocs(tech.getId()));
    }

    private List<TechnicianKycDocumentResponse> loadDocs(UUID technicianId) {
        return kycRepo.findByTechnicianIdOrderByCreatedAtAsc(technicianId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TechnicianKycDocumentResponse toResponse(TechnicianKycDocument e) {
        return TechnicianKycDocumentResponse.builder()
                .id(e.getId())
                .technicianId(e.getTechnicianId())
                .docType(e.getDocType())
                .title(e.getTitle())
                .url(e.getUrl())
                .required(e.getIsRequired())
                .status(e.getStatus())
                .rejectReason(e.getRejectReason())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
