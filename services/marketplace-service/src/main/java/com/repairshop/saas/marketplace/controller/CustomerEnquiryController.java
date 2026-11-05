package com.repairshop.saas.marketplace.controller;

import com.repairshop.saas.marketplace.dto.EnquiryCreateRequest;
import com.repairshop.saas.marketplace.dto.EnquiryOfferRequest;
import com.repairshop.saas.marketplace.dto.EnquiryResponse;
import com.repairshop.saas.marketplace.security.CallerContext;
import com.repairshop.saas.marketplace.service.EnquiryService;
import com.repairshop.saas.marketplace.service.ShopNotificationWriter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Buyer side of listing enquiries (SELL-MODULE-API.md). Shop notifications are
 * written here, after the service transaction has committed.
 */
@RestController
@RequestMapping("/customer/marketplace")
@RequiredArgsConstructor
public class CustomerEnquiryController {

    private final EnquiryService enquiries;
    private final ShopNotificationWriter notifications;

    @PostMapping("/products/{id}/enquiries")
    public ResponseEntity<EnquiryResponse> create(HttpServletRequest req, @PathVariable("id") UUID productId,
                                                  @RequestBody(required = false) EnquiryCreateRequest body) {
        UUID userId = CallerContext.requireUserId(req);
        EnquiryService.CreateResult out = enquiries.create(userId, CallerContext.uuidAttr(req, "shopId"), productId, body);
        if (out.created()) {
            notifications.enquiryCreated(out.enquiry());
            return ResponseEntity.ok(out.enquiry());
        }
        return ResponseEntity.ok().header(ShopMarketplaceController.IDEMPOTENT_REPLAY, "true").body(out.enquiry());
    }

    @GetMapping("/enquiries")
    public ResponseEntity<List<EnquiryResponse>> list(HttpServletRequest req) {
        return ResponseEntity.ok(enquiries.customerList(CallerContext.requireUserId(req)));
    }

    @GetMapping("/enquiries/{id}")
    public ResponseEntity<EnquiryResponse> get(HttpServletRequest req, @PathVariable("id") UUID id) {
        return ResponseEntity.ok(enquiries.customerDetail(CallerContext.requireUserId(req), id));
    }

    @PostMapping("/enquiries/{id}/offers")
    public ResponseEntity<EnquiryResponse> offer(HttpServletRequest req, @PathVariable("id") UUID id,
                                                 @RequestBody(required = false) EnquiryOfferRequest body) {
        EnquiryResponse r = enquiries.customerOffer(CallerContext.requireUserId(req), id, body);
        notifications.counterOffer(r);
        return ResponseEntity.ok(r);
    }

    @PostMapping("/enquiries/{id}/accept")
    public ResponseEntity<EnquiryResponse> accept(HttpServletRequest req, @PathVariable("id") UUID id) {
        EnquiryResponse r = enquiries.customerAccept(CallerContext.requireUserId(req), id);
        notifications.offerAccepted(r);
        return ResponseEntity.ok(r);
    }

    @PostMapping("/enquiries/{id}/cancel")
    public ResponseEntity<EnquiryResponse> cancel(HttpServletRequest req, @PathVariable("id") UUID id) {
        return ResponseEntity.ok(enquiries.customerCancel(CallerContext.requireUserId(req), id));
    }
}
