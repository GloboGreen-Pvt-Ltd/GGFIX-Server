package com.repairshop.saas.marketplace.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.repairshop.saas.marketplace.dto.BulkProductRequest;
import com.repairshop.saas.marketplace.dto.BulkResult;
import com.repairshop.saas.marketplace.dto.BulkResult.ItemResult;
import com.repairshop.saas.marketplace.dto.PageResponse;
import com.repairshop.saas.marketplace.dto.ProductRequest;
import com.repairshop.saas.marketplace.dto.ProductResponse;
import com.repairshop.saas.marketplace.dto.SellSummaryResponse;
import com.repairshop.saas.marketplace.dto.StatusChangeRequest;
import com.repairshop.saas.marketplace.entity.MarketplaceProduct;
import com.repairshop.saas.marketplace.exception.BadRequestException;
import com.repairshop.saas.marketplace.exception.ConflictException;
import com.repairshop.saas.marketplace.exception.ResourceNotFoundException;
import com.repairshop.saas.marketplace.repository.MarketplaceEnquiryRepository;
import com.repairshop.saas.marketplace.repository.MarketplaceProductRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.repairshop.saas.marketplace.service.SellRules.*;

/**
 * Shop-scoped listing management for the Partner Sell module
 * (/shop/marketplace/products/**, contract SELL-MODULE-API.md). Every method
 * takes the caller's shopId and only touches that shop's rows; another shop's
 * id is reported as 404.
 */
@Service
@RequiredArgsConstructor
public class SellListingService {

    private static final Logger log = LoggerFactory.getLogger(SellListingService.class);

    public static final int MAX_BULK_ITEMS = 100;
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");
    private static final List<String> SEARCH_FIELDS = List.of(
            "title", "description", "imei", "color", "storageLabel", "ramLabel", "conditionLabel");

    private final MarketplaceProductRepository productRepo;
    private final MarketplaceEnquiryRepository enquiryRepo;
    private final EnquiryService enquiryService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager txManager;

    /** {@code replay} = the clientRef already existed; nothing was created. */
    public record CreateOutcome(ProductResponse product, boolean replay) {}

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(UUID shopId, String status, String q, Integer page, Integer size) {
        String filter = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase();
        if (!LIST_FILTERS.contains(filter)) {
            throw new BadRequestException("status must be one of ALL, ACTIVE, DRAFT, SOLD, INACTIVE");
        }
        int pg = page == null || page < 0 ? 0 : page;
        int sz = size == null || size < 1 ? 20 : Math.min(size, 100);
        Page<MarketplaceProduct> result = productRepo.findAll(listingSpec(shopId, filter, q),
                PageRequest.of(pg, sz, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("createdAt"))));
        return PageResponse.<ProductResponse>builder()
                .content(result.getContent().stream().map(ProductMapper::toResponse).toList())
                .page(pg)
                .size(sz)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .counts(listingCounts(shopId))
                .build();
    }

    @Transactional(readOnly = true)
    public SellSummaryResponse summary(UUID shopId) {
        Map<String, Long> c = listingCounts(shopId);
        long enquiriesOpen = 0;
        long purchaseRequestsOpen = 0;
        for (Object[] row : enquiryRepo.countByStatusAndKind(shopId)) {
            if (!EnquiryService.ACTIVE_STATUSES.contains(String.valueOf(row[0]))) continue;
            long n = ((Number) row[2]).longValue();
            if (EnquiryService.PURCHASE_REQUEST.equals(row[1])) purchaseRequestsOpen += n;
            else enquiriesOpen += n;
        }
        return SellSummaryResponse.builder()
                .active(c.get(ACTIVE))
                .draft(c.get(DRAFT))
                .sold(c.get(SOLD))
                .inactive(c.get(INACTIVE))
                .enquiriesOpen(enquiriesOpen)
                .purchaseRequestsOpen(purchaseRequestsOpen)
                .enquiriesUnread(enquiryRepo.countByShopIdAndShopUnreadTrue(shopId))
                .build();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID shopId, UUID id) {
        MarketplaceProduct p = productRepo.findByIdAndShopId(id, shopId)
                .filter(x -> !DELETED.equals(normalizeStatus(x.getStatus())))
                .orElseThrow(() -> notFound(id));
        return ProductMapper.toResponse(p);
    }

    /** {ALL, ACTIVE, DRAFT, SOLD, INACTIVE} over the shop's SELL listings (DELETED excluded). */
    Map<String, Long> listingCounts(UUID shopId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String k : List.of("ALL", ACTIVE, DRAFT, SOLD, INACTIVE)) counts.put(k, 0L);
        for (Object[] row : productRepo.countSellByStatus(shopId)) {
            String s = normalizeStatus((String) row[0]);
            if (DELETED.equals(s)) continue;
            long n = ((Number) row[1]).longValue();
            counts.merge("ALL", n, Long::sum);
            if (!"ALL".equals(s) && counts.containsKey(s)) counts.merge(s, n, Long::sum);
        }
        return counts;
    }

    // ----------------------------------------------------------------- create

    /**
     * Creates a listing (status DRAFT or ACTIVE). A clientRef that already
     * exists for this shop returns that row with {@code replay=true}.
     */
    public CreateOutcome create(UUID shopId, UUID userId, ProductRequest req) {
        if (req == null) throw new BadRequestException("Request body is required");
        String clientRef = trimToNull(req.getClientRef());
        if (clientRef != null && clientRef.length() > MAX_CLIENT_REF) {
            throw new BadRequestException("clientRef must be at most " + MAX_CLIENT_REF + " characters");
        }
        if (clientRef != null) {
            Optional<MarketplaceProduct> existing = productRepo.findByShopIdAndClientRef(shopId, clientRef);
            if (existing.isPresent()) return replay(existing.get());
        }
        String status = req.getStatus() == null || req.getStatus().isBlank() ? ACTIVE : req.getStatus().trim().toUpperCase();
        if (!DRAFT.equals(status) && !ACTIVE.equals(status)) {
            throw new BadRequestException("status must be DRAFT or ACTIVE");
        }
        List<String> errors = validate(req, false);
        if (!errors.isEmpty()) throw new BadRequestException(String.join("; ", errors));
        try {
            MarketplaceProduct saved = tx(TransactionDefinition.PROPAGATION_REQUIRED).execute(s -> {
                MarketplaceProduct p = newProduct(shopId, userId, req, clientRef, null);
                List<String> stored = checkStored(p, null);
                if (!stored.isEmpty()) throw new BadRequestException(String.join("; ", stored));
                p.setStatus(status);
                if (ACTIVE.equals(status)) requirePublishable(p);
                return productRepo.saveAndFlush(p);
            });
            return new CreateOutcome(ProductMapper.toResponse(saved), false);
        } catch (DataIntegrityViolationException e) {
            // Lost a race on uq_marketplace_products_shop_client_ref: replay the winner.
            if (clientRef != null) {
                Optional<MarketplaceProduct> existing = productRepo.findByShopIdAndClientRef(shopId, clientRef);
                if (existing.isPresent()) return replay(existing.get());
            }
            throw e;
        }
    }

    private CreateOutcome replay(MarketplaceProduct p) {
        if (DELETED.equals(normalizeStatus(p.getStatus()))) {
            throw new ConflictException("This listing was already created with the same clientRef and has since been deleted.");
        }
        return new CreateOutcome(ProductMapper.toResponse(p), true);
    }

    // ----------------------------------------------------------------- update

    /** Partial update (null = unchanged). 409 for SOLD/DELETED; an ACTIVE result must stay publishable. */
    @Transactional
    public ProductResponse update(UUID shopId, UUID id, ProductRequest req) {
        if (req == null) throw new BadRequestException("Request body is required");
        MarketplaceProduct p = lockOwned(shopId, id);
        String cur = normalizeStatus(p.getStatus());
        if (SOLD.equals(cur)) throw new ConflictException("A sold listing can't be edited.");
        if (DELETED.equals(cur)) throw new ConflictException("This listing was deleted.");

        List<String> errors = validate(req, true);
        String target = null;
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            target = req.getStatus().trim().toUpperCase();
            if (!PATCHABLE.contains(target)) errors.add("status must be one of ACTIVE, INACTIVE, DRAFT, SOLD");
        }
        if (!errors.isEmpty()) throw new BadRequestException(String.join("; ", errors));
        boolean statusChange = target != null && !target.equals(cur);
        if (statusChange) requireTransition(cur, target);

        apply(p, req);
        List<String> stored = checkStored(p, p.getId());
        if (!stored.isEmpty()) throw new BadRequestException(String.join("; ", stored));

        boolean soldOut = false;
        if (statusChange) {
            if (SOLD.equals(target)) soldOut = applySale(p, null);
            else p.setStatus(target);
        }
        if (ACTIVE.equals(normalizeStatus(p.getStatus()))) requirePublishable(p);
        MarketplaceProduct saved = productRepo.save(p);
        if (soldOut) enquiryService.cancelOpenForProduct(saved.getId(), null, "The listing was sold.");
        return ProductMapper.toResponse(saved);
    }

    /** PATCH status with the contract's transition rules. */
    @Transactional
    public ProductResponse changeStatus(UUID shopId, UUID id, StatusChangeRequest body) {
        if (body == null || body.getStatus() == null || body.getStatus().isBlank()) {
            throw new BadRequestException("status is required");
        }
        String target = body.getStatus().trim().toUpperCase();
        if (!PATCHABLE.contains(target) && !DELETED.equals(target)) {
            throw new BadRequestException("status must be one of ACTIVE, INACTIVE, DRAFT, SOLD");
        }
        Integer quantitySold = body.getQuantitySold();
        if (quantitySold != null && quantitySold < 1) throw new BadRequestException("quantitySold must be at least 1");

        MarketplaceProduct p = lockOwned(shopId, id);
        requireTransition(p.getStatus(), target);
        boolean soldOut = false;
        switch (target) {
            case ACTIVE -> {
                requirePublishable(p);
                p.setStatus(ACTIVE);
            }
            case SOLD -> soldOut = applySale(p, quantitySold);
            default -> p.setStatus(target);
        }
        MarketplaceProduct saved = productRepo.save(p);
        if (soldOut) enquiryService.cancelOpenForProduct(saved.getId(), null, "The listing was sold.");
        return ProductMapper.toResponse(saved);
    }

    /** Soft delete (status DELETED); cancels the listing's open enquiries. Idempotent. */
    @Transactional
    public void delete(UUID shopId, UUID id) {
        MarketplaceProduct p = lockOwned(shopId, id);
        if (DELETED.equals(normalizeStatus(p.getStatus()))) return;
        p.setStatus(DELETED);
        productRepo.save(p);
        enquiryService.cancelOpenForProduct(p.getId(), null, "The listing was removed.");
    }

    // ------------------------------------------------------------------- bulk

    /** Each item runs in its own transaction; see SELL-MODULE-API.md "Bulk result". */
    public BulkResult bulk(UUID shopId, UUID userId, BulkProductRequest body) {
        if (body == null) throw new BadRequestException("Request body is required");
        UUID batchId;
        if (body.getBatchId() == null || body.getBatchId().isBlank()) {
            batchId = UUID.randomUUID();
        } else {
            try {
                batchId = UUID.fromString(body.getBatchId().trim());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("batchId must be a valid UUID");
            }
        }
        List<JsonNode> items = body.getItems();
        if (items == null || items.isEmpty()) throw new BadRequestException("items is required");
        if (items.size() > MAX_BULK_ITEMS) throw new BadRequestException("At most " + MAX_BULK_ITEMS + " items per upload");
        boolean publish = Boolean.TRUE.equals(body.getPublish());

        List<ItemResult> results = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            results.add(bulkItem(i, items.get(i), shopId, userId, batchId, publish));
        }
        int created = 0, existing = 0, failed = 0, published = 0, drafts = 0;
        for (ItemResult r : results) {
            switch (r.getOutcome()) {
                case "CREATED" -> created++;
                case "EXISTING" -> existing++;
                default -> failed++;
            }
            if (!"FAILED".equals(r.getOutcome())) {
                if (ACTIVE.equals(r.getStatus())) published++;
                else if (DRAFT.equals(r.getStatus())) drafts++;
            }
        }
        return BulkResult.builder()
                .batchId(batchId)
                .created(created).existing(existing).failed(failed)
                .published(published).drafts(drafts)
                .results(results)
                .build();
    }

    private ItemResult bulkItem(int index, JsonNode node, UUID shopId, UUID userId, UUID batchId, boolean publish) {
        if (node == null || !node.isObject()) return failed(index, null, List.of("item must be an object"));
        String clientRef = node.hasNonNull("clientRef") ? trimToNull(node.get("clientRef").asText()) : null;
        ProductRequest req;
        try {
            req = objectMapper.treeToValue(node, ProductRequest.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return failed(index, clientRef, List.of(describeParseError(e)));
        }
        if (clientRef != null && clientRef.length() > MAX_CLIENT_REF) {
            return failed(index, clientRef, List.of("clientRef must be at most " + MAX_CLIENT_REF + " characters"));
        }
        try {
            return tx(TransactionDefinition.PROPAGATION_REQUIRES_NEW).execute(s -> {
                if (clientRef != null) {
                    Optional<MarketplaceProduct> existing = productRepo.findByShopIdAndClientRef(shopId, clientRef);
                    if (existing.isPresent()) return existingResult(index, clientRef, existing.get());
                }
                List<String> errors = validate(req, false);
                if (!errors.isEmpty()) return failed(index, clientRef, errors);
                MarketplaceProduct p = newProduct(shopId, userId, req, clientRef, batchId);
                errors = checkStored(p, null);
                if (!errors.isEmpty()) return failed(index, clientRef, errors);

                List<String> warnings = new ArrayList<>();
                String status = DRAFT;
                if (publish) {
                    List<String> problems = publishProblems(p);
                    if (problems.isEmpty()) status = ACTIVE;
                    else problems.forEach(pr -> warnings.add("Saved as draft: " + pr));
                }
                p.setStatus(status);
                MarketplaceProduct saved = productRepo.saveAndFlush(p);
                return ItemResult.builder()
                        .index(index).clientRef(clientRef).outcome("CREATED")
                        .status(status).productId(saved.getId())
                        .errors(List.of()).warnings(warnings)
                        .build();
            });
        } catch (DataIntegrityViolationException e) {
            if (clientRef != null) {
                Optional<MarketplaceProduct> existing = productRepo.findByShopIdAndClientRef(shopId, clientRef);
                if (existing.isPresent()) return existingResult(index, clientRef, existing.get());
            }
            log.warn("bulk item {} of batch {} rejected by the database: {}", index, batchId, e.getMostSpecificCause().getMessage());
            return failed(index, clientRef, List.of("Could not save this item (invalid or duplicate data)"));
        } catch (BadRequestException | ConflictException e) {
            return failed(index, clientRef, List.of(e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("bulk item {} of batch {} failed: {}", index, batchId, e.getMessage(), e);
            return failed(index, clientRef, List.of("Could not save this item"));
        }
    }

    private static ItemResult existingResult(int index, String clientRef, MarketplaceProduct p) {
        String status = normalizeStatus(p.getStatus());
        if (DELETED.equals(status)) {
            return failed(index, clientRef, List.of("This item was uploaded before and has since been deleted"));
        }
        return ItemResult.builder()
                .index(index).clientRef(clientRef).outcome("EXISTING")
                .status(status).productId(p.getId())
                .errors(List.of()).warnings(List.of())
                .build();
    }

    private static ItemResult failed(int index, String clientRef, List<String> errors) {
        return ItemResult.builder()
                .index(index).clientRef(clientRef).outcome("FAILED")
                .status(null).productId(null)
                .errors(errors).warnings(List.of())
                .build();
    }

    private static String describeParseError(Exception e) {
        if (e instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            String field = jme.getPath().stream()
                    .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                    .collect(Collectors.joining("."));
            if (e instanceof InvalidFormatException ife && ife.getTargetType() == UUID.class) {
                return field + " must be a valid UUID";
            }
            return field + " has an invalid value";
        }
        return "item is not a valid listing";
    }

    // ---------------------------------------------------------------- helpers

    /** Row of this shop, locked FOR UPDATE (stock changes race with /confirm). 404 otherwise. */
    private MarketplaceProduct lockOwned(UUID shopId, UUID id) {
        return productRepo.findByIdForUpdate(id)
                .filter(p -> shopId.equals(p.getShopId()))
                .orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Listing not found: " + id);
    }

    private static void requirePublishable(MarketplaceProduct p) {
        List<String> problems = publishProblems(p);
        if (!problems.isEmpty()) {
            throw new BadRequestException("This listing can't be published yet: " + String.join("; ", problems));
        }
    }

    private static MarketplaceProduct newProduct(UUID shopId, UUID userId, ProductRequest req, String clientRef, UUID batchId) {
        MarketplaceProduct p = MarketplaceProduct.builder()
                .shopId(shopId)
                .sellerUserId(userId != null ? userId : req.getSellerUserId())
                .type("SELL")
                .listingType("DEVICE")
                .quantity(1)
                .soldQuantity(0)
                .negotiable(false)
                .fulfilmentOptions("PICKUP")
                .clientRef(clientRef)
                .batchId(batchId)
                .build();
        apply(p, req);
        return p;
    }

    /** Copies the non-null request fields. Never touches shop, seller, status, clientRef or batchId. */
    static void apply(MarketplaceProduct p, ProductRequest r) {
        if (r.getBrandId() != null) p.setBrandId(r.getBrandId());
        if (r.getModelId() != null) p.setModelId(r.getModelId());
        if (r.getRamOptionId() != null) p.setRamOptionId(r.getRamOptionId());
        if (r.getStorageOptionId() != null) p.setStorageOptionId(r.getStorageOptionId());
        if (r.getTitle() != null) p.setTitle(r.getTitle().trim());
        if (r.getDescription() != null) p.setDescription(r.getDescription());
        if (r.getType() != null) p.setType(r.getType().trim().toUpperCase());
        if (r.getPrice() != null) p.setPrice(r.getPrice());
        if (r.getConditionLabel() != null) p.setConditionLabel(blankToNull(r.getConditionLabel()));
        if (r.getColor() != null) p.setColor(blankToNull(r.getColor()));
        if (r.getRamLabel() != null) p.setRamLabel(blankToNull(r.getRamLabel()));
        if (r.getStorageLabel() != null) p.setStorageLabel(blankToNull(r.getStorageLabel()));
        if (r.getNetwork() != null) p.setNetwork(blankToNull(r.getNetwork()));
        if (r.getImei() != null) p.setImei(blankToNull(r.getImei()));
        if (r.getWorkingCondition() != null) p.setWorkingCondition(blankToNull(r.getWorkingCondition()));
        if (r.getDescriptionType() != null) p.setDescriptionType(blankToNull(r.getDescriptionType()));
        if (r.getImageUrl() != null) p.setImageUrl(blankToNull(r.getImageUrl()));
        if (r.getExtraImageUrls() != null) p.setExtraImageUrls(ProductMapper.serializeExtraImages(r.getExtraImageUrls()));
        if (r.getAssessmentJson() != null) p.setAssessmentJson(r.getAssessmentJson());
        if (r.getListingType() != null) p.setListingType(r.getListingType().trim().toUpperCase());
        if (r.getItemCondition() != null) p.setItemCondition(r.getItemCondition().trim().toUpperCase());
        if (r.getQuantity() != null) p.setQuantity(r.getQuantity());
        if (r.getNegotiable() != null) p.setNegotiable(r.getNegotiable());
        if (r.getFulfilmentOptions() != null) p.setFulfilmentOptions(ProductMapper.serializeFulfilment(r.getFulfilmentOptions()));
        if (r.getVideoUrl() != null) p.setVideoUrl(blankToNull(r.getVideoUrl()));
    }

    /** Request-level validation (400). {@code partial}: null fields are "unchanged". */
    static List<String> validate(ProductRequest r, boolean partial) {
        List<String> e = new ArrayList<>();
        if (!partial || r.getTitle() != null) {
            if (r.getTitle() == null || r.getTitle().isBlank()) e.add("title is required");
            else if (r.getTitle().trim().length() > 255) e.add("title must be at most 255 characters");
        }
        if (r.getType() != null && !Set.of("SELL", "BUY").contains(r.getType().trim().toUpperCase())) {
            e.add("type must be SELL or BUY");
        }
        if (r.getPrice() != null) {
            if (r.getPrice().signum() < 0) e.add("price can't be negative");
            else if (r.getPrice().compareTo(MAX_PRICE) > 0) e.add("price is too large");
        }
        if (r.getQuantity() != null && r.getQuantity() < 0) e.add("quantity can't be negative");
        oneOf(e, "listingType", r.getListingType(), LISTING_TYPES);
        oneOf(e, "itemCondition", r.getItemCondition(), ITEM_CONDITIONS);
        if (r.getFulfilmentOptions() != null) {
            String joined = ProductMapper.serializeFulfilment(r.getFulfilmentOptions());
            if (joined.isEmpty()) {
                e.add("fulfilmentOptions needs at least one of PICKUP, SHIPPING");
            } else if (!FULFILMENT_OPTIONS.containsAll(List.of(joined.split(",")))) {
                e.add("fulfilmentOptions may only contain PICKUP and SHIPPING");
            }
        }
        maxLen(e, "videoUrl", r.getVideoUrl(), 500);
        maxLen(e, "imageUrl", r.getImageUrl(), 500);
        maxLen(e, "conditionLabel", r.getConditionLabel(), 50);
        maxLen(e, "color", r.getColor(), 100);
        maxLen(e, "ramLabel", r.getRamLabel(), 50);
        maxLen(e, "storageLabel", r.getStorageLabel(), 50);
        maxLen(e, "network", r.getNetwork(), 50);
        maxLen(e, "imei", r.getImei(), 50);
        maxLen(e, "workingCondition", r.getWorkingCondition(), 30);
        maxLen(e, "descriptionType", r.getDescriptionType(), 30);
        return e;
    }

    /** Checks on the merged row: photo cap and IMEI uniqueness among the shop's live listings. */
    private List<String> checkStored(MarketplaceProduct p, UUID excludeId) {
        List<String> e = new ArrayList<>();
        if (imageCount(p) > MAX_IMAGES) e.add("at most " + MAX_IMAGES + " photos are allowed");
        String imei = trimToNull(p.getImei());
        if (imei != null) {
            boolean dup = excludeId == null
                    ? productRepo.existsByShopIdAndImeiAndStatusIn(p.getShopId(), imei, LIVE_STATUSES)
                    : productRepo.existsByShopIdAndImeiAndStatusInAndIdNot(p.getShopId(), imei, LIVE_STATUSES, excludeId);
            if (dup) e.add("IMEI " + imei + " is already used by another live listing of this shop");
        }
        return e;
    }

    static Specification<MarketplaceProduct> listingSpec(UUID shopId, String filter, String q) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("shopId"), shopId));
            ps.add(cb.equal(cb.upper(root.<String>get("type")), "SELL"));
            if ("ALL".equals(filter)) ps.add(cb.notEqual(root.get("status"), DELETED));
            else ps.add(root.get("status").in(rawStatuses(filter)));
            String needle = trimToNull(q);
            if (needle != null) {
                String like = "%" + needle.toLowerCase()
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                List<Predicate> any = new ArrayList<>();
                for (String f : SEARCH_FIELDS) any.add(cb.like(cb.lower(root.<String>get(f)), like, '\\'));
                ps.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    private TransactionTemplate tx(int propagation) {
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setPropagationBehavior(propagation);
        return t;
    }

    private static void oneOf(List<String> errors, String field, String value, Set<String> allowed) {
        if (value != null && !allowed.contains(value.trim().toUpperCase())) {
            errors.add(field + " must be one of " + String.join(", ", allowed.stream().sorted().toList()));
        }
    }

    private static void maxLen(List<String> errors, String field, String value, int max) {
        if (value != null && value.length() > max) errors.add(field + " must be at most " + max + " characters");
    }

    static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
