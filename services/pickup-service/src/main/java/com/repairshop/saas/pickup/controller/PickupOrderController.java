package com.repairshop.saas.pickup.controller;

import com.repairshop.saas.pickup.dto.PickupOrderRequest;
import com.repairshop.saas.pickup.dto.PickupOrderResponse;
import com.repairshop.saas.pickup.dto.RescheduleRequest;
import com.repairshop.saas.pickup.service.PickupOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pickups")
@RequiredArgsConstructor
public class PickupOrderController {

    private final PickupOrderService service;

    @PostMapping
    public ResponseEntity<PickupOrderResponse> create(HttpServletRequest req, @RequestBody PickupOrderRequest body) {
        return ResponseEntity.ok(service.create(callerId(req), body));
    }

    @GetMapping
    public ResponseEntity<List<PickupOrderResponse>> list(HttpServletRequest req, @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(service.listMine(callerId(req), status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PickupOrderResponse> getById(HttpServletRequest req, @PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(callerId(req), id));
    }

    @GetMapping("/shop")
    public ResponseEntity<List<PickupOrderResponse>> listForShop(HttpServletRequest req, @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(service.listForShop(callerShopId(req), status));
    }

    @GetMapping("/shop/{id}")
    public ResponseEntity<PickupOrderResponse> getByShop(HttpServletRequest req, @PathVariable UUID id) {
        return ResponseEntity.ok(service.getByShop(callerShopId(req), id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PickupOrderResponse> setStatus(HttpServletRequest req, @PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(service.updateStatus(callerId(req), id, status));
    }

    @PostMapping("/{id}/reschedule")
    public ResponseEntity<PickupOrderResponse> reschedule(HttpServletRequest req, @PathVariable UUID id, @RequestBody RescheduleRequest body) {
        return ResponseEntity.ok(service.reschedule(callerId(req), id, body));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PickupOrderResponse> cancel(HttpServletRequest req, @PathVariable UUID id) {
        return ResponseEntity.ok(service.cancel(callerId(req), id));
    }

    private UUID callerId(HttpServletRequest req) {
        Object u = req.getAttribute("userId");
        if (u == null) throw new IllegalStateException("Missing userId in request");
        return UUID.fromString(u.toString());
    }

    private UUID callerShopId(HttpServletRequest req) {
        Object s = req.getAttribute("shopId");
        if (s == null) throw new IllegalStateException("Missing shopId in token — sign in as a shop owner");
        return UUID.fromString(s.toString());
    }
}
