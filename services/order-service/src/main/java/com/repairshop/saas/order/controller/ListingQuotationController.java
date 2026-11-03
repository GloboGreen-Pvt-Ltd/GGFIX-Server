package com.repairshop.saas.order.controller;

import com.repairshop.saas.common.subscription.SubscriptionFeature;
import com.repairshop.saas.common.subscription.SubscriptionLimitService;
import com.repairshop.saas.order.entity.CustomerNotification;
import com.repairshop.saas.order.entity.CustomerOrder;
import com.repairshop.saas.order.entity.SellOrder;
import com.repairshop.saas.order.entity.SellOrderQuotation;
import com.repairshop.saas.order.exception.ConflictException;
import com.repairshop.saas.order.exception.ForbiddenException;
import com.repairshop.saas.order.exception.ResourceNotFoundException;
import com.repairshop.saas.order.repository.CustomerNotificationRepository;
import com.repairshop.saas.order.repository.CustomerOrderRepository;
import com.repairshop.saas.order.repository.SellOrderQuotationRepository;
import com.repairshop.saas.order.repository.SellOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * A shop quotes on a customer's sell request seen in the Partner app's
 * "Customer Sell Leads" (a marketplace_listings row, sellerType CUSTOMER).
 *
 * <p>Quotes belong to a sell order (sell_order_quotations), which the customer
 * app shows and lets the customer accept. A listing is linked to its sell order
 * (marketplace_listings.sell_order_id, migration 107): the stored link, else the
 * customer's open sell order for the same model created around the same time,
 * else a sell order is created from the listing (and its customer order row,
 * so it appears in the customer's orders). One quote per shop per order: a
 * second submit revises it.
 */
@RestController
@RequestMapping("/sell-orders/listing-quotations")
@RequiredArgsConstructor
public class ListingQuotationController {

    private static final Set<String> SELLER_ROLES = Set.of("SHOP_OWNER", "OWNER", "ADMIN", "SUPER_ADMIN");
    private static final Set<String> QUOTABLE = Set.of("PENDING", "AWAITING_QUOTATION", "DRAFT");
    private static final Set<String> FULFILMENT = Set.of("PICKUP", "SHOP_VISIT");
    private static final Duration MATCH_WINDOW = Duration.ofHours(2);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final JdbcTemplate jdbc;
    private final SellOrderRepository sellRepo;
    private final SellOrderQuotationRepository quoteRepo;
    private final CustomerOrderRepository customerOrderRepo;
    private final CustomerNotificationRepository notificationRepo;
    private final SubscriptionLimitService subscriptionLimits;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager txManager;

    public record QuoteRequest(UUID listingId, BigDecimal quotationPrice, String note, String fulfilment) {}

    public record QuoteView(UUID id, UUID sellOrderId, UUID shopId, BigDecimal quotationPrice, String note,
                            String status, Instant createdAt, Instant updatedAt) {}

    /** { sellOrderId, orderStatus, quotation } — this shop's quote on the listing (quotation null when none). */
    @GetMapping("/{listingId}")
    public ResponseEntity<Map<String, Object>> mine(HttpServletRequest req, @PathVariable UUID listingId) {
        UUID shopId = sellerShop(req);
        Listing l = listing(listingId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("listingId", listingId);
        out.put("sellOrderId", l.sellOrderId);
        SellOrder so = l.sellOrderId == null ? null : sellRepo.findById(l.sellOrderId).orElse(null);
        out.put("orderStatus", so == null ? null : so.getStatus());
        out.put("quotation", so == null ? null : quoteRepo.findBySellOrderId(so.getId()).stream()
                .filter(q -> shopId.equals(q.getShopId())).findFirst().map(this::view).orElse(null));
        return ResponseEntity.ok(out);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(HttpServletRequest req, @RequestBody QuoteRequest body) {
        UUID shopId = sellerShop(req);
        if (body == null || body.listingId() == null) throw new IllegalArgumentException("listingId is required");
        BigDecimal price = body.quotationPrice();
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("Enter a quote above ₹0");
        if (price.compareTo(new BigDecimal("10000000")) > 0) throw new IllegalArgumentException("That quote looks too high");
        String fulfilment = body.fulfilment() == null ? "PICKUP" : body.fulfilment().trim().toUpperCase();
        if (!FULFILMENT.contains(fulfilment)) throw new IllegalArgumentException("fulfilment must be PICKUP or SHOP_VISIT");

        Listing l = listing(body.listingId());
        if (!"CUSTOMER".equalsIgnoreCase(l.sellerType)) throw new IllegalArgumentException("Only customer sell requests can be quoted");
        if (l.status != null && !"AVAILABLE".equalsIgnoreCase(l.status)) throw new ConflictException("This sell request is no longer open");
        if (Objects.equals(l.sellerId, callerId(req))) throw new ForbiddenException("You can't quote on your own sell request");

        // Plan limit first, outside the write transaction: its lookup fails open,
        // and a failed query inside the transaction would abort it.
        boolean quotedBefore = l.sellOrderId != null && quoteRepo.findBySellOrderId(l.sellOrderId).stream().anyMatch(q -> shopId.equals(q.getShopId()));
        if (!quotedBefore) subscriptionLimits.requireUsageCapacity(null, shopId, SubscriptionFeature.SELL_ORDERS);
        Map<String, Object> shop = shopInfo(shopId);

        return new TransactionTemplate(txManager).execute(tx -> ResponseEntity.ok(writeQuote(l, shopId, shop, price, fulfilment, body.note())));
    }

    private Map<String, Object> writeQuote(Listing first, UUID shopId, Map<String, Object> shop, BigDecimal price, String fulfilment, String rawNote) {
        // Lock the listing, then re-read it: a concurrent quote may have just
        // linked (or created) its sell order.
        jdbc.queryForList("SELECT id FROM marketplace_listings WHERE id = ? FOR UPDATE", first.id);
        Listing l = listing(first.id);
        SellOrder so = resolveSellOrder(l);
        // Serialise quotes on this order: no duplicate quote from a double tap.
        jdbc.queryForList("SELECT id FROM sell_orders WHERE id = ? FOR UPDATE", so.getId());
        so = sellRepo.findById(so.getId()).orElseThrow();
        if (!QUOTABLE.contains(String.valueOf(so.getStatus()).toUpperCase())) {
            throw new ConflictException("The customer has already chosen a quote for this device");
        }

        String note = noteFor(fulfilment, rawNote);
        Optional<SellOrderQuotation> existing = quoteRepo.findBySellOrderId(so.getId()).stream()
                .filter(q -> shopId.equals(q.getShopId())).findFirst();
        boolean revised = existing.isPresent();
        SellOrderQuotation q;
        if (revised) {
            q = existing.get();
            if (!"PROPOSED".equalsIgnoreCase(q.getStatus())) throw new ConflictException("This quote can't be changed any more");
            q.setQuotationPrice(price);
            q.setNote(note);
            q.setUpdatedAt(Instant.now());
        } else {
            q = SellOrderQuotation.builder()
                    .sellOrderId(so.getId()).shopId(shopId)
                    .shopName((String) shop.get("name")).shopPhone((String) shop.get("mobile")).shopCity((String) shop.get("city"))
                    .quotationPrice(price).note(note).status("PROPOSED").build();
        }
        q = quoteRepo.save(q);

        notificationRepo.save(CustomerNotification.builder()
                .customerUserId(so.getCustomerUserId())
                .bookingId(so.getId())
                .bookingNumber(so.getSellNumber())
                .statusKey(revised ? "SELL_QUOTE_UPDATED" : "SELL_QUOTE")
                .title(revised ? "Quote updated" : "New quote for your device")
                .body(String.format("%s quoted ₹%s for %s.", Optional.ofNullable(q.getShopName()).orElse("A shop"),
                        price.stripTrailingZeros().toPlainString(), Optional.ofNullable(l.productName).orElse("your device")))
                .type("offers")
                .build());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("listingId", l.id);
        out.put("sellOrderId", so.getId());
        out.put("orderStatus", so.getStatus());
        out.put("revised", revised);
        out.put("quotation", view(q));
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private record Listing(UUID id, String sellerType, UUID sellerId, UUID brandId, UUID modelId, String productName,
                           String productImage, String condition, String description, String status,
                           Instant createdAt, UUID sellOrderId) {}

    private Listing listing(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, seller_type, seller_id, brand_id, model_id, product_name, product_image, condition, description, "
                        + "status, created_at, sell_order_id FROM marketplace_listings WHERE id = ?", id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Sell request not found");
        Map<String, Object> r = rows.get(0);
        return new Listing(uuid(r.get("id")), str(r.get("seller_type")), uuid(r.get("seller_id")), uuid(r.get("brand_id")),
                uuid(r.get("model_id")), str(r.get("product_name")), str(r.get("product_image")), str(r.get("condition")),
                str(r.get("description")), str(r.get("status")), instant(r.get("created_at")), uuid(r.get("sell_order_id")));
    }

    private SellOrder resolveSellOrder(Listing l) {
        if (l.sellOrderId != null) {
            Optional<SellOrder> linked = sellRepo.findById(l.sellOrderId);
            if (linked.isPresent()) return linked.get();
        }
        SellOrder match = findMatchingOrder(l);
        SellOrder so = match != null ? match : createFromListing(l);
        jdbc.update("UPDATE marketplace_listings SET sell_order_id = ? WHERE id = ?", so.getId(), l.id);
        return so;
    }

    /** The customer's own open sell order for this device, created around the same time and not linked elsewhere. */
    private SellOrder findMatchingOrder(Listing l) {
        if (l.sellerId == null || l.createdAt == null) return null;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.id, s.model_id, s.created_at FROM sell_orders s WHERE s.customer_user_id = ? "
                        + "AND s.status IN ('PENDING', 'AWAITING_QUOTATION', 'DRAFT') AND s.created_at BETWEEN ? AND ? "
                        + "AND NOT EXISTS (SELECT 1 FROM marketplace_listings m WHERE m.sell_order_id = s.id AND m.id <> ?)",
                l.sellerId, Timestamp.from(l.createdAt.minus(MATCH_WINDOW)), Timestamp.from(l.createdAt.plus(MATCH_WINDOW)), l.id);
        return rows.stream()
                .filter(r -> l.modelId == null || r.get("model_id") == null || l.modelId.equals(uuid(r.get("model_id"))))
                .min(Comparator.comparingLong(r -> Math.abs(Duration.between(instant(r.get("created_at")), l.createdAt).toSeconds())))
                .flatMap(r -> sellRepo.findById(uuid(r.get("id"))))
                .orElse(null);
    }

    private SellOrder createFromListing(Listing l) {
        if (l.sellerId == null || jdbc.queryForObject("SELECT COUNT(*) FROM customer_users WHERE id = ?", Integer.class, l.sellerId) == 0) {
            throw new ConflictException("This sell request's customer account wasn't found, so it can't take quotes");
        }
        String sellNumber = uniqueSellNumber();
        SellOrder so = sellRepo.save(SellOrder.builder()
                .sellNumber(sellNumber)
                .customerUserId(l.sellerId)
                .brandId(l.brandId)
                .modelId(l.modelId)
                .deviceConditionSummary(String.join(" · ", Arrays.stream(new String[] { l.condition, l.description })
                        .filter(s -> s != null && !s.isBlank()).toList()))
                .frontImageUrl(l.productImage)
                .status("AWAITING_QUOTATION")
                .build());
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Sell Device");
        payload.put("sellOrderId", so.getId());
        payload.put("brandId", l.brandId);
        payload.put("modelId", l.modelId);
        payload.put("productName", l.productName);
        payload.put("source", "MARKETPLACE_LISTING");
        payload.put("listingId", l.id);
        String payloadJson;
        try { payloadJson = objectMapper.writeValueAsString(payload); } catch (Exception e) { payloadJson = null; }
        customerOrderRepo.save(CustomerOrder.builder()
                .orderNumber(sellNumber)
                .customerUserId(l.sellerId)
                .orderType("SELL")
                .referenceId(so.getId())
                .status("PENDING")
                .payloadJson(payloadJson)
                .build());
        return so;
    }

    private static String noteFor(String fulfilment, String note) {
        String how = "SHOP_VISIT".equals(fulfilment) ? "Visit the shop to sell" : "Free pickup from your location";
        String extra = note == null ? "" : note.trim();
        if (extra.length() > 500) extra = extra.substring(0, 500);
        return extra.isEmpty() ? how : how + " · " + extra;
    }

    private Map<String, Object> shopInfo(UUID shopId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT name, mobile, city FROM shops WHERE id = ?", shopId);
            if (!rows.isEmpty()) return rows.get(0);
        } catch (DataAccessException ignored) {
            // shop details are optional on the quote
        }
        return Map.of();
    }

    private QuoteView view(SellOrderQuotation q) {
        return new QuoteView(q.getId(), q.getSellOrderId(), q.getShopId(), q.getQuotationPrice(), q.getNote(),
                q.getStatus(), q.getCreatedAt(), q.getUpdatedAt());
    }

    private String uniqueSellNumber() {
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder("#CSPQX");
            for (int j = 0; j < 8; j++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            String c = sb.toString();
            if (sellRepo.findBySellNumber(c).isEmpty()) return c;
        }
        throw new IllegalStateException("Could not generate unique sell number");
    }

    /** The token's shop, for shop-owner (or admin) sessions only. */
    private UUID sellerShop(HttpServletRequest req) {
        Object s = req.getAttribute("shopId");
        if (s == null) throw new ForbiddenException("A shop session is required");
        Object roles = req.getAttribute("roles");
        boolean seller = roles instanceof Collection<?> c && c.stream().anyMatch(r -> SELLER_ROLES.contains(String.valueOf(r).toUpperCase()));
        if (!seller) throw new ForbiddenException("Only shop owners can send quotes");
        return UUID.fromString(s.toString());
    }

    private static UUID callerId(HttpServletRequest req) {
        Object u = req.getAttribute("userId");
        return u == null ? null : UUID.fromString(u.toString());
    }

    private static UUID uuid(Object o) {
        if (o == null) return null;
        if (o instanceof UUID u) return u;
        return UUID.fromString(o.toString());
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Instant instant(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (o instanceof Instant i) return i;
        return Instant.parse(o.toString());
    }
}
