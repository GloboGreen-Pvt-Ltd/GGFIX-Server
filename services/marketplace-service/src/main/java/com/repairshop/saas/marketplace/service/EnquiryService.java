package com.repairshop.saas.marketplace.service;

import com.repairshop.saas.marketplace.dto.EnquiryCreateRequest;
import com.repairshop.saas.marketplace.dto.EnquiryOfferRequest;
import com.repairshop.saas.marketplace.dto.EnquiryResponse;
import com.repairshop.saas.marketplace.dto.PageResponse;
import com.repairshop.saas.marketplace.entity.CustomerChatThread;
import com.repairshop.saas.marketplace.entity.MarketplaceEnquiry;
import com.repairshop.saas.marketplace.entity.MarketplaceEnquiryOffer;
import com.repairshop.saas.marketplace.entity.MarketplaceProduct;
import com.repairshop.saas.marketplace.exception.BadRequestException;
import com.repairshop.saas.marketplace.exception.ConflictException;
import com.repairshop.saas.marketplace.exception.ResourceNotFoundException;
import com.repairshop.saas.marketplace.repository.CustomerChatThreadRepository;
import com.repairshop.saas.marketplace.repository.MarketplaceEnquiryOfferRepository;
import com.repairshop.saas.marketplace.repository.MarketplaceEnquiryRepository;
import com.repairshop.saas.marketplace.repository.MarketplaceProductRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Buyer <-> shop enquiries and quotations on a listing (SELL-MODULE-API.md).
 *
 * <pre>
 * OPEN ──shop offer──▶ QUOTED ──buyer counter──▶ COUNTERED ──shop offer──▶ QUOTED …
 * QUOTED ──buyer accept──▶ ACCEPTED        COUNTERED/OPEN(buyer offer) ──shop accept──▶ ACCEPTED
 * ACCEPTED ──shop confirm──▶ CONFIRMED (stock decremented; at 0 the listing is SOLD)
 * any non-terminal ──shop reject──▶ REJECTED, ──buyer cancel──▶ CANCELLED
 * </pre>
 * Invalid transitions throw {@link ConflictException} (409); bad input
 * {@link BadRequestException} (400); another shop's / buyer's row 404.
 */
@Service
@RequiredArgsConstructor
public class EnquiryService {

    public static final String OPEN = "OPEN";
    public static final String QUOTED = "QUOTED";
    public static final String COUNTERED = "COUNTERED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    public static final String ENQUIRY = "ENQUIRY";
    public static final String PURCHASE_REQUEST = "PURCHASE_REQUEST";

    public static final String SHOP = "SHOP";
    public static final String BUYER = "BUYER";
    public static final String SYSTEM = "SYSTEM";

    public static final List<String> ACTIVE_STATUSES = List.of(OPEN, QUOTED, COUNTERED, ACCEPTED);
    public static final List<String> HISTORY_STATUSES = List.of(CONFIRMED, REJECTED, CANCELLED);
    private static final Set<String> NEGOTIABLE = Set.of(OPEN, QUOTED, COUNTERED);
    private static final Set<String> KINDS = Set.of(ENQUIRY, PURCHASE_REQUEST);
    private static final int MAX_TEXT = 2000;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    public enum Viewer { SHOP, BUYER }

    /** {@code created=false}: the buyer already had a non-terminal enquiry on the listing. */
    public record CreateResult(EnquiryResponse enquiry, boolean created) {}

    private final MarketplaceEnquiryRepository enquiryRepo;
    private final MarketplaceEnquiryOfferRepository offerRepo;
    private final MarketplaceProductRepository productRepo;
    private final CustomerChatThreadRepository threadRepo;
    private final ChatSnapshotService snapshots;
    private final PlatformTransactionManager txManager;

    // =============================================================== customer

    public CreateResult create(UUID buyerUserId, UUID callerShopId, UUID productId, EnquiryCreateRequest body) {
        EnquiryCreateRequest b = body != null ? body : new EnquiryCreateRequest();
        String kind = b.getKind() == null || b.getKind().isBlank() ? ENQUIRY : b.getKind().trim().toUpperCase();
        if (!KINDS.contains(kind)) throw new BadRequestException("kind must be ENQUIRY or PURCHASE_REQUEST");
        String message = text(b.getMessage(), "message");
        BigDecimal offer = b.getOfferAmount();
        if (offer != null) requireAmount(offer, "offerAmount");
        int quantity = b.getQuantity() == null ? 1 : b.getQuantity();
        if (quantity < 1) throw new BadRequestException("quantity must be at least 1");

        // Same source ChatService uses for counterpartName/counterpartPhone
        // (customer_users via ChatSnapshotService), snapshotted on the row. Read
        // before the transaction: the lookup is best-effort and must not abort it.
        ChatSnapshotService.CustomerSnapshot cs = snapshots.loadCustomer(buyerUserId);
        try {
            return new TransactionTemplate(txManager).execute(s ->
                    createInTx(buyerUserId, callerShopId, productId, kind, message, offer, quantity, cs));
        } catch (DataIntegrityViolationException e) {
            // Lost a race on uq_marketplace_enquiries_open_per_buyer: return the winner.
            return enquiryRepo.findFirstByProductIdAndBuyerUserIdAndStatusInOrderByCreatedAtDesc(
                            productId, buyerUserId, ACTIVE_STATUSES)
                    .map(x -> new CreateResult(detail(x, Viewer.BUYER, null, null), false))
                    .orElseThrow(() -> e);
        }
    }

    private CreateResult createInTx(UUID buyerUserId, UUID callerShopId, UUID productId, String kind,
                                    String message, BigDecimal offer, int quantity,
                                    ChatSnapshotService.CustomerSnapshot cs) {
        MarketplaceProduct p = productRepo.findById(productId)
                .filter(x -> {
                    String st = SellRules.normalizeStatus(x.getStatus());
                    return !SellRules.DELETED.equals(st) && !SellRules.DRAFT.equals(st);
                })
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found: " + productId));
        if ((callerShopId != null && callerShopId.equals(p.getShopId())) || buyerUserId.equals(p.getSellerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't send an enquiry on your own listing.");
        }
        Optional<MarketplaceEnquiry> existing = enquiryRepo
                .findFirstByProductIdAndBuyerUserIdAndStatusInOrderByCreatedAtDesc(productId, buyerUserId, ACTIVE_STATUSES);
        if (existing.isPresent()) return new CreateResult(detail(existing.get(), Viewer.BUYER, p, null), false);
        if (!SellRules.ACTIVE.equals(SellRules.normalizeStatus(p.getStatus()))) {
            throw new ConflictException("This listing is not available right now.");
        }
        int stock = p.getQuantity() == null ? 1 : p.getQuantity();
        if (quantity > stock) throw new BadRequestException("Only " + stock + " available.");

        MarketplaceEnquiry e = enquiryRepo.saveAndFlush(MarketplaceEnquiry.builder()
                .productId(p.getId())
                .shopId(p.getShopId())
                .buyerUserId(buyerUserId)
                .kind(kind)
                .status(OPEN)
                .message(message)
                .quantity(quantity)
                .lastOfferAmount(offer)
                .lastOfferBy(offer != null ? BUYER : null)
                .buyerName(cut(cs == null ? null : cs.name(), 255))
                .buyerPhone(cut(cs == null ? null : cs.mobile(), 20))
                .shopUnread(true)
                .buyerUnread(false)
                .build());
        addOffer(e, BUYER, "ENQUIRY", offer, message);
        return new CreateResult(detail(e, Viewer.BUYER, p, null), true);
    }

    @Transactional(readOnly = true)
    public List<EnquiryResponse> customerList(UUID buyerUserId) {
        return toListResponses(enquiryRepo.findByBuyerUserIdOrderByUpdatedAtDesc(buyerUserId), Viewer.BUYER);
    }

    @Transactional
    public EnquiryResponse customerDetail(UUID buyerUserId, UUID id) {
        MarketplaceEnquiry e = requireBuyer(buyerUserId, id);
        if (Boolean.TRUE.equals(e.getBuyerUnread())) enquiryRepo.markBuyerRead(e.getId());
        return detail(e, Viewer.BUYER, null, false);
    }

    /** Buyer counter-offer: allowed while negotiating and the latest offer is not the buyer's. */
    @Transactional
    public EnquiryResponse customerOffer(UUID buyerUserId, UUID id, EnquiryOfferRequest body) {
        MarketplaceEnquiry e = requireBuyer(buyerUserId, id);
        BigDecimal amount = body == null ? null : body.getAmount();
        requireAmount(amount, "amount");
        String note = text(body.getNote(), "note");
        requireNegotiable(e, "send an offer on");
        if (BUYER.equals(e.getLastOfferBy())) {
            throw new ConflictException("Wait for the shop to reply to your last offer.");
        }
        e.setStatus(COUNTERED);
        e.setLastOfferAmount(amount);
        e.setLastOfferBy(BUYER);
        e.setShopUnread(true);
        e.setBuyerUnread(false);
        return saveWithStep(e, BUYER, "COUNTER", amount, note, Viewer.BUYER);
    }

    @Transactional
    public EnquiryResponse customerAccept(UUID buyerUserId, UUID id) {
        MarketplaceEnquiry e = requireBuyer(buyerUserId, id);
        if (!NEGOTIABLE.contains(e.getStatus()) || !SHOP.equals(e.getLastOfferBy()) || e.getLastOfferAmount() == null) {
            throw new ConflictException("There is no shop offer to accept.");
        }
        e.setStatus(ACCEPTED);
        e.setAgreedAmount(e.getLastOfferAmount());
        e.setShopUnread(true);
        e.setBuyerUnread(false);
        return saveWithStep(e, BUYER, "ACCEPT", e.getAgreedAmount(), null, Viewer.BUYER);
    }

    @Transactional
    public EnquiryResponse customerCancel(UUID buyerUserId, UUID id) {
        MarketplaceEnquiry e = requireBuyer(buyerUserId, id);
        requireOpen(e);
        e.setStatus(CANCELLED);
        e.setShopUnread(true);
        e.setBuyerUnread(false);
        return saveWithStep(e, BUYER, "CANCEL", null, null, Viewer.BUYER);
    }

    // =================================================================== shop

    @Transactional(readOnly = true)
    public PageResponse<EnquiryResponse> shopList(UUID shopId, String status, String kind, UUID productId,
                                                  Integer page, Integer size) {
        final String bucket = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
        if (!Set.of("ACTIVE", "HISTORY", "ALL").contains(bucket)) {
            throw new BadRequestException("status must be ACTIVE, HISTORY or ALL");
        }
        final String k = kind == null || kind.isBlank() ? null : kind.trim().toUpperCase();
        if (k != null && !KINDS.contains(k)) throw new BadRequestException("kind must be ENQUIRY or PURCHASE_REQUEST");
        int pg = page == null || page < 0 ? 0 : page;
        int sz = size == null || size < 1 ? 20 : Math.min(size, 100);

        Specification<MarketplaceEnquiry> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("shopId"), shopId));
            if (!"ALL".equals(bucket)) ps.add(root.get("status").in(statusesOf(bucket)));
            if (k != null) ps.add(cb.equal(root.get("kind"), k));
            if (productId != null) ps.add(cb.equal(root.get("productId"), productId));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<MarketplaceEnquiry> result = enquiryRepo.findAll(spec,
                PageRequest.of(pg, sz, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("createdAt"))));
        return PageResponse.<EnquiryResponse>builder()
                .content(toListResponses(result.getContent(), Viewer.SHOP))
                .page(pg)
                .size(sz)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .counts(enquiryCounts(shopId, productId, bucket, k))
                .build();
    }

    /** Detail with offers; marks the enquiry read for the shop. */
    @Transactional
    public EnquiryResponse shopDetail(UUID shopId, UUID id) {
        MarketplaceEnquiry e = requireShop(shopId, id);
        if (Boolean.TRUE.equals(e.getShopUnread())) enquiryRepo.markShopRead(e.getId());
        return detail(e, Viewer.SHOP, null, false);
    }

    /** Shop quote: OPEN / COUNTERED (or QUOTED, revising its own quote) -> QUOTED. */
    @Transactional
    public EnquiryResponse shopOffer(UUID shopId, UUID id, EnquiryOfferRequest body) {
        MarketplaceEnquiry e = requireShop(shopId, id);
        BigDecimal amount = body == null ? null : body.getAmount();
        requireAmount(amount, "amount");
        String note = text(body.getNote(), "note");
        requireNegotiable(e, "send an offer on");
        e.setStatus(QUOTED);
        e.setLastOfferAmount(amount);
        e.setLastOfferBy(SHOP);
        e.setBuyerUnread(true);
        e.setShopUnread(false);
        return saveWithStep(e, SHOP, "OFFER", amount, note, Viewer.SHOP);
    }

    /** Needs lastOfferBy=BUYER, or a PURCHASE_REQUEST with no offer (agreed = listing price). */
    @Transactional
    public EnquiryResponse shopAccept(UUID shopId, UUID id) {
        MarketplaceEnquiry e = requireShop(shopId, id);
        requireNegotiable(e, "accept");
        BigDecimal agreed;
        if (BUYER.equals(e.getLastOfferBy()) && e.getLastOfferAmount() != null) {
            agreed = e.getLastOfferAmount();
        } else if (PURCHASE_REQUEST.equals(e.getKind()) && e.getLastOfferAmount() == null) {
            MarketplaceProduct p = productRepo.findById(e.getProductId()).orElse(null);
            if (p == null || p.getPrice() == null || p.getPrice().signum() <= 0) {
                throw new ConflictException("The listing has no price to accept.");
            }
            agreed = p.getPrice();
        } else {
            throw new ConflictException("There is no buyer offer to accept.");
        }
        e.setStatus(ACCEPTED);
        e.setAgreedAmount(agreed);
        e.setBuyerUnread(true);
        e.setShopUnread(false);
        return saveWithStep(e, SHOP, "ACCEPT", agreed, null, Viewer.SHOP);
    }

    @Transactional
    public EnquiryResponse shopReject(UUID shopId, UUID id, String reason) {
        MarketplaceEnquiry e = requireShop(shopId, id);
        String note = text(reason, "reason");
        requireOpen(e);
        e.setStatus(REJECTED);
        e.setClosedReason(note);
        e.setBuyerUnread(true);
        e.setShopUnread(false);
        return saveWithStep(e, SHOP, "REJECT", null, note, Viewer.SHOP);
    }

    /**
     * ACCEPTED -> CONFIRMED. Locks the listing row (SELECT ... FOR UPDATE),
     * decrements its stock by the enquiry quantity; at 0 the listing becomes
     * SOLD and the other open enquiries on it are cancelled.
     */
    @Transactional
    public EnquiryResponse shopConfirm(UUID shopId, UUID id, String fulfilment) {
        MarketplaceEnquiry e = requireShop(shopId, id);
        String f = fulfilment == null || fulfilment.isBlank() ? null : fulfilment.trim().toUpperCase();
        if (f == null) throw new BadRequestException("fulfilment is required");
        if (!SellRules.FULFILMENT_OPTIONS.contains(f)) throw new BadRequestException("fulfilment must be PICKUP or SHIPPING");
        if (!ACCEPTED.equals(e.getStatus())) {
            throw new ConflictException("Only an accepted enquiry can be confirmed (this one is " + e.getStatus().toLowerCase() + ").");
        }
        MarketplaceProduct p = productRepo.findByIdForUpdate(e.getProductId())
                .orElseThrow(() -> new ConflictException("The listing no longer exists."));
        String ps = SellRules.normalizeStatus(p.getStatus());
        if (SellRules.DELETED.equals(ps)) throw new ConflictException("The listing was removed.");
        int stock = p.getQuantity() == null ? 0 : p.getQuantity();
        if (SellRules.SOLD.equals(ps) || stock <= 0) throw new ConflictException("This listing is sold out.");
        List<String> options = ProductMapper.parseFulfilment(p.getFulfilmentOptions());
        if (!options.contains(f)) {
            throw new BadRequestException("This listing offers " + String.join(" or ", options) + " only.");
        }
        int want = e.getQuantity() == null ? 1 : e.getQuantity();
        if (want > stock) throw new ConflictException("Only " + stock + " left in stock.");

        p.setQuantity(stock - want);
        p.setSoldQuantity((p.getSoldQuantity() == null ? 0 : p.getSoldQuantity()) + want);
        boolean soldOut = p.getQuantity() == 0;
        if (soldOut) p.setStatus(SellRules.SOLD);
        productRepo.save(p);

        e.setStatus(CONFIRMED);
        e.setFulfilment(f);
        e.setBuyerUnread(true);
        e.setShopUnread(false);
        MarketplaceEnquiry saved = enquiryRepo.saveAndFlush(e);
        addOffer(saved, SHOP, "CONFIRM", saved.getAgreedAmount(), null);
        if (soldOut) cancelOpenForProduct(p.getId(), saved.getId(), "Sold out");
        return detail(saved, Viewer.SHOP, p, null);
    }

    /** Cancels every non-terminal enquiry on a listing (except one), with a SYSTEM step. */
    @Transactional
    public int cancelOpenForProduct(UUID productId, UUID exceptEnquiryId, String note) {
        int n = 0;
        for (MarketplaceEnquiry e : enquiryRepo.findByProductIdAndStatusIn(productId, ACTIVE_STATUSES)) {
            if (e.getId() != null && e.getId().equals(exceptEnquiryId)) continue;
            e.setStatus(CANCELLED);
            e.setClosedReason(note);
            e.setBuyerUnread(true);
            enquiryRepo.save(e);
            addOffer(e, SYSTEM, "CANCEL", null, note);
            n++;
        }
        return n;
    }

    // ================================================================ mapping

    /** Actions the caller may take now (SEND_OFFER, ACCEPT, REJECT, CONFIRM, CANCEL). */
    public static List<String> allowedActions(MarketplaceEnquiry e, Viewer viewer) {
        String s = e.getStatus();
        boolean negotiable = NEGOTIABLE.contains(s);
        List<String> a = new ArrayList<>();
        if (viewer == Viewer.SHOP) {
            if (negotiable) a.add("SEND_OFFER");
            if (negotiable && (BUYER.equals(e.getLastOfferBy())
                    || (PURCHASE_REQUEST.equals(e.getKind()) && e.getLastOfferAmount() == null))) a.add("ACCEPT");
            if (ACTIVE_STATUSES.contains(s)) a.add("REJECT");
            if (ACCEPTED.equals(s)) a.add("CONFIRM");
        } else {
            if (negotiable && !BUYER.equals(e.getLastOfferBy())) a.add("SEND_OFFER");
            if (negotiable && SHOP.equals(e.getLastOfferBy())) a.add("ACCEPT");
            if (ACTIVE_STATUSES.contains(s)) a.add("CANCEL");
        }
        return a;
    }

    EnquiryResponse detail(MarketplaceEnquiry e, Viewer viewer, MarketplaceProduct product, Boolean unreadOverride) {
        MarketplaceProduct p = product != null ? product : productRepo.findById(e.getProductId()).orElse(null);
        CustomerChatThread t = threadRepo.findByCustomerUserIdAndShopId(e.getBuyerUserId(), e.getShopId()).orElse(null);
        List<MarketplaceEnquiryOffer> offers = offerRepo.findByEnquiryIdOrderByCreatedAtAsc(e.getId());
        EnquiryResponse r = toResponse(e, p, viewer, t, offers == null ? List.of() : offers);
        if (unreadOverride != null) r.setUnread(unreadOverride);
        return r;
    }

    private List<EnquiryResponse> toListResponses(List<MarketplaceEnquiry> list, Viewer viewer) {
        if (list.isEmpty()) return List.of();
        Map<UUID, MarketplaceProduct> products = productRepo.findAllById(
                        list.stream().map(MarketplaceEnquiry::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(MarketplaceProduct::getId, Function.identity(), (a, b) -> a));
        List<CustomerChatThread> threads = viewer == Viewer.SHOP
                ? threadRepo.findByShopIdAndCustomerUserIdIn(list.get(0).getShopId(),
                        list.stream().map(MarketplaceEnquiry::getBuyerUserId).distinct().toList())
                : threadRepo.findByCustomerUserIdAndShopIdIn(list.get(0).getBuyerUserId(),
                        list.stream().map(MarketplaceEnquiry::getShopId).distinct().toList());
        Map<String, CustomerChatThread> byPair = new HashMap<>();
        for (CustomerChatThread t : threads) byPair.putIfAbsent(t.getCustomerUserId() + ":" + t.getShopId(), t);
        return list.stream()
                .map(e -> toResponse(e, products.get(e.getProductId()), viewer,
                        byPair.get(e.getBuyerUserId() + ":" + e.getShopId()), null))
                .toList();
    }

    EnquiryResponse toResponse(MarketplaceEnquiry e, MarketplaceProduct p, Viewer viewer,
                               CustomerChatThread thread, List<MarketplaceEnquiryOffer> offers) {
        String name = e.getBuyerName() != null ? e.getBuyerName() : (thread != null ? thread.getCustomerName() : null);
        String phone = e.getBuyerPhone() != null ? e.getBuyerPhone() : (thread != null ? thread.getCustomerMobile() : null);
        boolean showPhone = viewer == Viewer.BUYER || ACCEPTED.equals(e.getStatus()) || CONFIRMED.equals(e.getStatus());
        return EnquiryResponse.builder()
                .id(e.getId())
                .productId(e.getProductId())
                .shopId(e.getShopId())
                .kind(e.getKind())
                .status(e.getStatus())
                .message(e.getMessage())
                .quantity(e.getQuantity())
                .lastOfferAmount(e.getLastOfferAmount())
                .lastOfferBy(e.getLastOfferBy())
                .agreedAmount(e.getAgreedAmount())
                .fulfilment(e.getFulfilment())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .unread(viewer == Viewer.SHOP ? Boolean.TRUE.equals(e.getShopUnread()) : Boolean.TRUE.equals(e.getBuyerUnread()))
                .buyer(EnquiryResponse.Buyer.builder()
                        .userId(e.getBuyerUserId())
                        .name(name)
                        .phone(showPhone ? phone : null)
                        .build())
                .product(p == null ? null : EnquiryResponse.ProductSummary.builder()
                        .id(p.getId())
                        .title(p.getTitle())
                        .imageUrl(p.getImageUrl())
                        .price(p.getPrice())
                        .status(p.getStatus())
                        .storageLabel(p.getStorageLabel())
                        .ramLabel(p.getRamLabel())
                        .conditionLabel(p.getConditionLabel())
                        .quantity(p.getQuantity())
                        .fulfilmentOptions(ProductMapper.parseFulfilment(p.getFulfilmentOptions()))
                        .build())
                .chatThreadId(thread != null ? thread.getId() : null)
                .allowedActions(allowedActions(e, viewer))
                .offers(offers == null ? null : offers.stream().map(o -> EnquiryResponse.Offer.builder()
                        .id(o.getId())
                        .author(o.getAuthor())
                        .action(o.getAction())
                        .amount(o.getAmount())
                        .note(o.getNote())
                        .createdAt(o.getCreatedAt())
                        .build()).toList())
                .build();
    }

    /** ACTIVE/HISTORY/ALL follow the kind filter; ENQUIRY/PURCHASE_REQUEST follow the status filter. */
    private Map<String, Long> enquiryCounts(UUID shopId, UUID productId, String bucket, String kind) {
        Map<String, Long> c = new LinkedHashMap<>();
        for (String key : List.of("ACTIVE", "HISTORY", "ALL", ENQUIRY, PURCHASE_REQUEST)) c.put(key, 0L);
        List<Object[]> rows = productId == null
                ? enquiryRepo.countByStatusAndKind(shopId)
                : enquiryRepo.countByStatusAndKindForProduct(shopId, productId);
        for (Object[] row : rows) {
            String st = String.valueOf(row[0]);
            String kd = String.valueOf(row[1]);
            long n = ((Number) row[2]).longValue();
            if (kind == null || kind.equals(kd)) {
                c.merge("ALL", n, Long::sum);
                if (ACTIVE_STATUSES.contains(st)) c.merge("ACTIVE", n, Long::sum);
                else if (HISTORY_STATUSES.contains(st)) c.merge("HISTORY", n, Long::sum);
            }
            if (KINDS.contains(kd) && ("ALL".equals(bucket) || statusesOf(bucket).contains(st))) {
                c.merge(kd, n, Long::sum);
            }
        }
        return c;
    }

    // ================================================================ helpers

    private static List<String> statusesOf(String bucket) {
        return "HISTORY".equals(bucket) ? HISTORY_STATUSES : ACTIVE_STATUSES;
    }

    private MarketplaceEnquiry requireShop(UUID shopId, UUID id) {
        return enquiryRepo.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry not found: " + id));
    }

    private MarketplaceEnquiry requireBuyer(UUID buyerUserId, UUID id) {
        return enquiryRepo.findByIdAndBuyerUserId(id, buyerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry not found: " + id));
    }

    private static void requireNegotiable(MarketplaceEnquiry e, String verb) {
        if (!NEGOTIABLE.contains(e.getStatus())) {
            throw new ConflictException("You can't " + verb + " an enquiry that is " + e.getStatus().toLowerCase() + ".");
        }
    }

    private static void requireOpen(MarketplaceEnquiry e) {
        if (!ACTIVE_STATUSES.contains(e.getStatus())) {
            throw new ConflictException("This enquiry is already " + e.getStatus().toLowerCase() + ".");
        }
    }

    private EnquiryResponse saveWithStep(MarketplaceEnquiry e, String author, String action,
                                         BigDecimal amount, String note, Viewer viewer) {
        MarketplaceEnquiry saved = enquiryRepo.saveAndFlush(e);
        addOffer(saved, author, action, amount, note);
        return detail(saved, viewer, null, null);
    }

    private void addOffer(MarketplaceEnquiry e, String author, String action, BigDecimal amount, String note) {
        offerRepo.save(MarketplaceEnquiryOffer.builder()
                .enquiryId(e.getId())
                .author(author)
                .action(action)
                .amount(amount)
                .note(note)
                .createdAt(Instant.now())
                .build());
    }

    private static void requireAmount(BigDecimal amount, String field) {
        if (amount == null) throw new BadRequestException(field + " is required");
        if (amount.signum() <= 0) throw new BadRequestException(field + " must be greater than 0");
        if (amount.compareTo(MAX_AMOUNT) > 0) throw new BadRequestException(field + " is too large");
    }

    private static String text(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        if (t.length() > MAX_TEXT) throw new BadRequestException(field + " must be at most " + MAX_TEXT + " characters");
        return t;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
