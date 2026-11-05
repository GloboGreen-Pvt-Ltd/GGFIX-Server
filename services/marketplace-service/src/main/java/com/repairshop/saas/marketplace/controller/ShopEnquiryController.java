package com.repairshop.saas.marketplace.controller;

import com.repairshop.saas.marketplace.dto.EnquiryConfirmRequest;
import com.repairshop.saas.marketplace.dto.EnquiryOfferRequest;
import com.repairshop.saas.marketplace.dto.EnquiryRejectRequest;
import com.repairshop.saas.marketplace.dto.EnquiryResponse;
import com.repairshop.saas.marketplace.dto.PageResponse;
import com.repairshop.saas.marketplace.security.CallerContext;
import com.repairshop.saas.marketplace.service.EnquiryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Shop side of buyer enquiries / quotations on its listings (SELL-MODULE-API.md). */
@RestController
@RequestMapping("/shop/marketplace/enquiries")
@RequiredArgsConstructor
public class ShopEnquiryController {

    private final EnquiryService enquiries;

    @GetMapping
    public ResponseEntity<PageResponse<EnquiryResponse>> list(
            HttpServletRequest req,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "productId", required = false) UUID productId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(enquiries.shopList(CallerContext.requireShopId(req), status, kind, productId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnquiryResponse> get(HttpServletRequest req, @PathVariable("id") UUID id) {
        return ResponseEntity.ok(enquiries.shopDetail(CallerContext.requireShopId(req), id));
    }

    @PostMapping("/{id}/offers")
    public ResponseEntity<EnquiryResponse> offer(HttpServletRequest req, @PathVariable("id") UUID id,
                                                 @RequestBody(required = false) EnquiryOfferRequest body) {
        return ResponseEntity.ok(enquiries.shopOffer(CallerContext.requireShopId(req), id, body));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<EnquiryResponse> accept(HttpServletRequest req, @PathVariable("id") UUID id) {
        return ResponseEntity.ok(enquiries.shopAccept(CallerContext.requireShopId(req), id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<EnquiryResponse> reject(HttpServletRequest req, @PathVariable("id") UUID id,
                                                  @RequestBody(required = false) EnquiryRejectRequest body) {
        return ResponseEntity.ok(enquiries.shopReject(CallerContext.requireShopId(req), id,
                body == null ? null : body.getReason()));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<EnquiryResponse> confirm(HttpServletRequest req, @PathVariable("id") UUID id,
                                                   @RequestBody(required = false) EnquiryConfirmRequest body) {
        return ResponseEntity.ok(enquiries.shopConfirm(CallerContext.requireShopId(req), id,
                body == null ? null : body.getFulfilment()));
    }
}
