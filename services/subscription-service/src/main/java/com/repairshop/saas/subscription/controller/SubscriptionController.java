package com.repairshop.saas.subscription.controller;

import com.repairshop.saas.subscription.dto.ActivateRequest;
import com.repairshop.saas.subscription.dto.PlanCatalog;
import com.repairshop.saas.subscription.dto.QuoteResponse;
import com.repairshop.saas.subscription.dto.SubscriptionResponse;
import com.repairshop.saas.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService service;

    /** All subscriptions (admin list). */
    @GetMapping("")
    public List<SubscriptionResponse> listAll() {
        return service.listAll();
    }

    /** The owner's subscription, or 200 with an empty body when none exists. */
    @GetMapping("/owner/{ownerUserId}")
    public ResponseEntity<SubscriptionResponse> getByOwner(@PathVariable UUID ownerUserId) {
        return ResponseEntity.ok(service.getByOwner(ownerUserId));
    }

    /** Static plan catalog. */
    @GetMapping("/plans")
    public List<PlanCatalog.Plan> plans() {
        return service.plans();
    }

    /** Price quote for BASIC at the given shop count (default 1). */
    @GetMapping("/quote")
    public QuoteResponse quote(@RequestParam(name = "shops", defaultValue = "1") int shops) {
        return service.quote(shops);
    }

    /** Record-only BASIC activation (no payment gateway). */
    @PostMapping("/activate")
    public SubscriptionResponse activate(@RequestBody ActivateRequest request) {
        return service.activateBasic(request.getOwnerUserId(), request.getShopCount());
    }

    /** Record-only phase: no payments captured. Kept so the admin payments tab loads. */
    @GetMapping("/payments")
    public List<Object> payments() {
        return List.of();
    }
}
