package com.repairshop.saas.marketplace.controller;

import com.repairshop.saas.marketplace.security.CallerContext;
import com.repairshop.saas.marketplace.service.FixedPriceOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fixed-price Buy orders for the Customer, Partner (shop) and Employee apps. */
@RestController
@RequiredArgsConstructor
public class FixedPriceOrderController {

    private final FixedPriceOrderService svc;

    @PostMapping("/customer/buy-orders")
    public Map<String, Object> place(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        return svc.place(CallerContext.requireUserId(req), body);
    }

    @GetMapping("/customer/buy-orders")
    public List<Map<String, Object>> mine(HttpServletRequest req) { return svc.customerOrders(CallerContext.requireUserId(req)); }

    @GetMapping("/customer/buy-orders/{id}")
    public Map<String, Object> mineOne(HttpServletRequest req, @PathVariable UUID id) {
        return svc.customerOrder(CallerContext.requireUserId(req), id);
    }

    @PostMapping("/customer/buy-orders/{id}/cancel")
    public Map<String, Object> cancel(HttpServletRequest req, @PathVariable UUID id) {
        return svc.customerCancel(CallerContext.requireUserId(req), id);
    }

    @GetMapping("/shop/buy-orders")
    public List<Map<String, Object>> shopList(HttpServletRequest req) { return svc.shopOrders(CallerContext.requireShopId(req)); }

    @GetMapping("/shop/buy-orders/{id}")
    public Map<String, Object> shopOne(HttpServletRequest req, @PathVariable UUID id) {
        return svc.shopOrder(CallerContext.requireShopId(req), id);
    }

    /** action: confirm | reject | assign | handover | return-received */
    @PostMapping("/shop/buy-orders/{id}/{action}")
    public Map<String, Object> shopAction(HttpServletRequest req, @PathVariable UUID id, @PathVariable String action,
                                          @RequestBody(required = false) Map<String, Object> body) {
        return svc.shopAction(CallerContext.requireShopId(req), CallerContext.requireUserId(req), id, action, body == null ? Map.of() : body);
    }

    @GetMapping("/staff/buy-orders")
    public List<Map<String, Object>> staffList(HttpServletRequest req) { return svc.staffOrders(CallerContext.requireUserId(req)); }

    @GetMapping("/staff/buy-orders/{id}")
    public Map<String, Object> staffOne(HttpServletRequest req, @PathVariable UUID id) {
        return svc.staffOrder(CallerContext.requireUserId(req), CallerContext.uuidAttr(req, "shopId"), id);
    }

    /** action: accept | decline | pickup | start-delivery | deliver | fail */
    @PostMapping("/staff/buy-orders/{id}/{action}")
    public Map<String, Object> staffAction(HttpServletRequest req, @PathVariable UUID id, @PathVariable String action,
                                           @RequestBody(required = false) Map<String, Object> body) {
        return svc.staffAction(CallerContext.requireUserId(req), CallerContext.uuidAttr(req, "shopId"), id, action, body == null ? Map.of() : body);
    }
}
