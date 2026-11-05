package com.repairshop.saas.marketplace.controller;

import com.repairshop.saas.marketplace.dto.ProductRequest;
import com.repairshop.saas.marketplace.dto.ProductResponse;
import com.repairshop.saas.marketplace.entity.MarketplaceProduct;
import com.repairshop.saas.marketplace.exception.ResourceNotFoundException;
import com.repairshop.saas.marketplace.repository.MarketplaceProductRepository;
import com.repairshop.saas.marketplace.security.ShopAccess;
import com.repairshop.saas.marketplace.service.SellListingService;
import com.repairshop.saas.marketplace.service.ProductMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/marketplace")
@RequiredArgsConstructor
public class MarketplaceController {

    private static final Set<String> HIDDEN_FROM_PUBLIC = Set.of("DRAFT", "DELETED");
    // Statuses the legacy PUT may set; deletion goes through DELETE.
    private static final Set<String> LEGACY_STATUSES = Set.of("ACTIVE", "INACTIVE", "DRAFT", "SOLD", "COMPLETED", "CANCELLED");

    private final MarketplaceProductRepository productRepo;
    private final ShopAccess shopAccess;
    private final SellListingService sellListings;

    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> listProducts(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false, defaultValue = "ACTIVE") String status,
            @RequestParam(value = "modelId", required = false) UUID modelId,
            @RequestParam(value = "q", required = false) String q
    ) {
        // The public board never shows drafts or soft-deleted rows, whatever is asked for.
        if (HIDDEN_FROM_PUBLIC.contains(status.trim().toUpperCase())) {
            return ResponseEntity.ok(List.of());
        }
        List<MarketplaceProduct> result;
        if (type != null && !type.isBlank()) {
            result = productRepo.findByTypeAndStatus(type.toUpperCase(), status.toUpperCase());
        } else if (modelId != null) {
            result = productRepo.findByModelIdAndStatus(modelId, status.toUpperCase());
        } else {
            result = productRepo.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        }
        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase();
            result = result.stream().filter(p ->
                    (p.getTitle() != null && p.getTitle().toLowerCase().contains(needle)) ||
                    (p.getDescription() != null && p.getDescription().toLowerCase().contains(needle))
            ).toList();
        }
        return ResponseEntity.ok(result.stream()
                .filter(p -> p.getStatus() == null || !HIDDEN_FROM_PUBLIC.contains(p.getStatus().toUpperCase()))
                .map(ProductMapper::toResponse).toList());
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponse> getProduct(HttpServletRequest httpReq, @PathVariable UUID id) {
        MarketplaceProduct p = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        // Soft-deleted rows are gone for everyone; a draft is visible only to its own shop.
        String st = p.getStatus() == null ? "" : p.getStatus().toUpperCase();
        if ("DELETED".equals(st) || ("DRAFT".equals(st) && !shopAccess.canManage(httpReq, p.getShopId()))) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
        return ResponseEntity.ok(ProductMapper.toResponse(p));
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(HttpServletRequest httpReq, @RequestBody ProductRequest req) {
        // Prefer the seller's userId from the JWT filter; fall back to the body
        // (admin tooling / seed scripts may send it explicitly).
        UUID sellerUserId = req.getSellerUserId();
        Object u = httpReq.getAttribute("userId");
        if (u != null) {
            try { sellerUserId = UUID.fromString(u.toString()); } catch (IllegalArgumentException ignored) {}
        }
        // shop_id is NOT NULL (FK to shops). The client may POST a null shopId when
        // its active-shop state hasn't hydrated yet; a null there fails the DB
        // constraint with an opaque 500 ("Listing failed"). The JWT's shopId claim
        // is authoritative for shop-owner / shop-login sessions, so derive it from
        // the token when the body doesn't carry one — mirrors the sellerUserId
        // fallback above.
        UUID shopId = req.getShopId();
        Object s = httpReq.getAttribute("shopId");
        if (shopId == null && s != null) {
            try { shopId = UUID.fromString(s.toString()); } catch (IllegalArgumentException ignored) {}
        }
        if (shopId == null) {
            throw new IllegalArgumentException(
                    "A shop is required to create a listing. Please re-open the app so your shop loads, then try again.");
        }
        // The body's shopId is only trusted for a shop the caller may manage
        // (the token's active shop, another shop they own, or any shop for admins).
        shopAccess.requireManage(httpReq, shopId);
        MarketplaceProduct p = MarketplaceProduct.builder()
                .shopId(shopId)
                .sellerUserId(sellerUserId)
                .brandId(req.getBrandId())
                .modelId(req.getModelId())
                .ramOptionId(req.getRamOptionId())
                .storageOptionId(req.getStorageOptionId())
                .title(req.getTitle())
                .description(req.getDescription())
                .type(req.getType() != null ? req.getType().toUpperCase() : "SELL")
                .price(req.getPrice())
                .status(req.getStatus() != null ? req.getStatus().toUpperCase() : "ACTIVE")
                .conditionLabel(req.getConditionLabel())
                .color(req.getColor())
                .ramLabel(req.getRamLabel())
                .storageLabel(req.getStorageLabel())
                .network(req.getNetwork())
                .imei(req.getImei())
                .workingCondition(req.getWorkingCondition())
                .descriptionType(req.getDescriptionType())
                .imageUrl(req.getImageUrl())
                .extraImageUrls(ProductMapper.serializeExtraImages(req.getExtraImageUrls()))
                .assessmentJson(req.getAssessmentJson())
                .build();
        return ResponseEntity.ok(ProductMapper.toResponse(productRepo.save(p)));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ProductResponse> updateProduct(HttpServletRequest httpReq, @PathVariable UUID id,
                                                         @RequestBody ProductRequest req) {
        MarketplaceProduct p = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        shopAccess.requireManage(httpReq, p.getShopId());
        if ("DELETED".equalsIgnoreCase(String.valueOf(p.getStatus()))) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
        if (req.getShopId() != null && !req.getShopId().equals(p.getShopId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A listing can't be moved to another shop.");
        }
        if (req.getBrandId() != null) p.setBrandId(req.getBrandId());
        if (req.getModelId() != null) p.setModelId(req.getModelId());
        if (req.getTitle() != null) p.setTitle(req.getTitle());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        if (req.getType() != null) p.setType(req.getType().toUpperCase());
        if (req.getPrice() != null) p.setPrice(req.getPrice());
        if (req.getStatus() != null) {
            String st = req.getStatus().trim().toUpperCase();
            if (!LEGACY_STATUSES.contains(st)) throw new IllegalArgumentException("Unsupported status: " + req.getStatus());
            p.setStatus(st);
        }
        if (req.getConditionLabel() != null) p.setConditionLabel(req.getConditionLabel());
        if (req.getColor() != null) p.setColor(req.getColor());
        if (req.getRamLabel() != null) p.setRamLabel(req.getRamLabel());
        if (req.getStorageLabel() != null) p.setStorageLabel(req.getStorageLabel());
        if (req.getRamOptionId() != null) p.setRamOptionId(req.getRamOptionId());
        if (req.getStorageOptionId() != null) p.setStorageOptionId(req.getStorageOptionId());
        if (req.getNetwork() != null) p.setNetwork(req.getNetwork());
        if (req.getImei() != null) p.setImei(req.getImei());
        if (req.getWorkingCondition() != null) p.setWorkingCondition(req.getWorkingCondition());
        if (req.getDescriptionType() != null) p.setDescriptionType(req.getDescriptionType());
        if (req.getImageUrl() != null) p.setImageUrl(req.getImageUrl());
        if (req.getExtraImageUrls() != null) p.setExtraImageUrls(ProductMapper.serializeExtraImages(req.getExtraImageUrls()));
        if (req.getAssessmentJson() != null) p.setAssessmentJson(req.getAssessmentJson());
        return ResponseEntity.ok(ProductMapper.toResponse(productRepo.save(p)));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(HttpServletRequest httpReq, @PathVariable UUID id) {
        Optional<MarketplaceProduct> row = productRepo.findById(id);
        if (row.isEmpty()) return ResponseEntity.notFound().build();
        shopAccess.requireManage(httpReq, row.get().getShopId());
        // Soft delete (status DELETED, open enquiries cancelled): enquiries and
        // order history reference the row, so it is never removed outright.
        sellListings.delete(row.get().getShopId(), id);
        return ResponseEntity.noContent().build();
    }
}
