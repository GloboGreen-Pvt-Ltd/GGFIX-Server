package com.repairshop.saas.marketplace.controller;

import com.repairshop.saas.marketplace.dto.BulkProductRequest;
import com.repairshop.saas.marketplace.dto.BulkResult;
import com.repairshop.saas.marketplace.dto.PageResponse;
import com.repairshop.saas.marketplace.dto.ProductRequest;
import com.repairshop.saas.marketplace.dto.ProductResponse;
import com.repairshop.saas.marketplace.dto.SellSummaryResponse;
import com.repairshop.saas.marketplace.dto.StatusChangeRequest;
import com.repairshop.saas.marketplace.security.CallerContext;
import com.repairshop.saas.marketplace.service.SellListingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Partner Sell module: the shop's own listings (SELL-MODULE-API.md). Needs a
 * shopId claim (403 otherwise); other shops' rows are 404.
 */
@RestController
@RequestMapping("/shop/marketplace")
@RequiredArgsConstructor
public class ShopMarketplaceController {

    public static final String IDEMPOTENT_REPLAY = "Idempotent-Replay";

    private final SellListingService listings;

    @GetMapping("/products")
    public ResponseEntity<PageResponse<ProductResponse>> list(
            HttpServletRequest req,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(listings.list(CallerContext.requireShopId(req), status, q, page, size));
    }

    @GetMapping("/summary")
    public ResponseEntity<SellSummaryResponse> summary(HttpServletRequest req) {
        return ResponseEntity.ok(listings.summary(CallerContext.requireShopId(req)));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponse> get(HttpServletRequest req, @PathVariable("id") UUID id) {
        return ResponseEntity.ok(listings.get(CallerContext.requireShopId(req), id));
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> create(HttpServletRequest req, @RequestBody ProductRequest body) {
        UUID shopId = CallerContext.requireShopId(req);
        SellListingService.CreateOutcome out = listings.create(shopId, CallerContext.uuidAttr(req, "userId"), body);
        ResponseEntity.BodyBuilder res = ResponseEntity.ok();
        if (out.replay()) res.header(IDEMPOTENT_REPLAY, "true");
        return res.body(out.product());
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ProductResponse> update(HttpServletRequest req, @PathVariable("id") UUID id,
                                                  @RequestBody ProductRequest body) {
        return ResponseEntity.ok(listings.update(CallerContext.requireShopId(req), id, body));
    }

    @PatchMapping("/products/{id}/status")
    public ResponseEntity<ProductResponse> changeStatus(HttpServletRequest req, @PathVariable("id") UUID id,
                                                        @RequestBody StatusChangeRequest body) {
        return ResponseEntity.ok(listings.changeStatus(CallerContext.requireShopId(req), id, body));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest req, @PathVariable("id") UUID id) {
        listings.delete(CallerContext.requireShopId(req), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/products/bulk")
    public ResponseEntity<BulkResult> bulk(HttpServletRequest req, @RequestBody BulkProductRequest body) {
        UUID shopId = CallerContext.requireShopId(req);
        return ResponseEntity.ok(listings.bulk(shopId, CallerContext.uuidAttr(req, "userId"), body));
    }
}
