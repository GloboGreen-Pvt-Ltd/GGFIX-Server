package com.repairshop.saas.shop.controller;

import com.repairshop.saas.shop.dto.ShopKycDocumentDtos.*;
import com.repairshop.saas.shop.entity.ShopKycDocument;
import com.repairshop.saas.shop.exception.ResourceNotFoundException;
import com.repairshop.saas.shop.repository.ShopKycDocumentRepository;
import com.repairshop.saas.shop.repository.ShopRepository;
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
 * KYC document storage for the owner mobile app.
 *
 * Endpoints:
 *   GET    /shops/{shopId}/kyc-documents              — list all docs for a shop
 *   POST   /shops/{shopId}/kyc-documents              — batch upsert (one entry
 *                                                       per docType replaces the
 *                                                       previous row in place)
 *   DELETE /shops/{shopId}/kyc-documents/{docType}    — remove a single doc
 *
 * Hosted URLs come from master-service's /media/upload. This service only
 * stores the metadata + URL pointer.
 */
@RestController
@RequestMapping("/shops/{shopId}/kyc-documents")
@RequiredArgsConstructor
@Slf4j
public class ShopKycDocumentController {

    private final ShopKycDocumentRepository kycRepo;
    private final ShopRepository shopRepository;

    /**
     * Diagnostic ping. Hit GET /shops/{anyId}/kyc-documents/ping in a browser to
     * confirm the controller is on the classpath and the table is reachable.
     * Returns 200 with row counts; the path is permitAll-eligible (GET on /shops/**).
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping(@PathVariable UUID shopId) {
        long total;
        try {
            total = kycRepo.count();
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "shopId", shopId.toString(),
                    "controller", "ShopKycDocumentController",
                    "tableError", e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage())
            ));
        }
        long forShop = kycRepo.findByShopIdOrderByCreatedAtAsc(shopId).size();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "shopId", shopId.toString(),
                "controller", "ShopKycDocumentController",
                "rowsTotal", total,
                "rowsForShop", forShop
        ));
    }

    @GetMapping
    public ResponseEntity<List<ShopKycDocumentResponse>> list(@PathVariable UUID shopId) {
        ensureShopExists(shopId);
        List<ShopKycDocumentResponse> out = kycRepo
                .findByShopIdOrderByCreatedAtAsc(shopId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> save(
            @PathVariable UUID shopId,
            @RequestBody SaveShopKycRequest body) {
        log.info("KYC save: ENTERED shopId={} bodyNull={}",
                shopId, body == null);
        try {
            ensureShopExists(shopId);
            if (body == null || body.getDocuments() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Missing 'documents' in request body"));
            }
            log.info("KYC save: shopId={} docs={}", shopId, body.getDocuments().size());
            int saved = 0;
            for (ShopKycDocumentInput doc : body.getDocuments()) {
                if (doc.getDocType() == null || doc.getDocType().isBlank()) continue;
                if (doc.getUrl() == null || doc.getUrl().isBlank()) continue;
                log.debug("KYC save: shopId={} docType={} urlLen={}", shopId, doc.getDocType(), doc.getUrl().length());
                ShopKycDocument entity = kycRepo
                        .findByShopIdAndDocType(shopId, doc.getDocType())
                        .orElseGet(() -> ShopKycDocument.builder()
                                .shopId(shopId)
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
            log.info("KYC save: shopId={} saved={}", shopId, saved);
            return list(shopId);
        } catch (ResourceNotFoundException nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", nf.getMessage()));
        } catch (Exception e) {
            // Log the full stack to the server, but return a useful message to the client.
            log.error("KYC save failed for shopId=" + shopId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "no message" : e.getMessage())
            ));
        }
    }

    @DeleteMapping("/{docType}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable UUID shopId, @PathVariable String docType) {
        ensureShopExists(shopId);
        kycRepo.deleteByShopIdAndDocType(shopId, docType);
        return ResponseEntity.noContent().build();
    }

    private void ensureShopExists(UUID shopId) {
        if (!shopRepository.existsById(shopId)) {
            throw new ResourceNotFoundException("Shop not found: " + shopId);
        }
    }

    private ShopKycDocumentResponse toResponse(ShopKycDocument e) {
        return ShopKycDocumentResponse.builder()
                .id(e.getId())
                .shopId(e.getShopId())
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
