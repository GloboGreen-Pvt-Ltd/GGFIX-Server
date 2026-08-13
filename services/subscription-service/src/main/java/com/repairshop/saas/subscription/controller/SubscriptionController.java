package com.repairshop.saas.subscription.controller;

import com.repairshop.saas.common.subscription.Entitlements;
import com.repairshop.saas.subscription.dto.ActivateRequest;
import com.repairshop.saas.subscription.dto.PlanCatalog;
import com.repairshop.saas.subscription.dto.QuoteResponse;
import com.repairshop.saas.subscription.dto.SubscriptionResponse;
import com.repairshop.saas.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
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

    /**
     * The owner's full entitlements: plan, window, every allowance with live
     * usage, and the on/off features. The single payload the Subscription
     * screen and all client-side capability checks read.
     *
     * <p>{@code shopId} selects which shop the per-shop counters (employees,
     * sell orders) are measured in. The app passes its active shop; omitting it
     * returns correct ceilings with those counters at zero.
     */
    @GetMapping("/entitlements/{ownerUserId}")
    public Entitlements entitlements(@PathVariable UUID ownerUserId,
                                     @RequestParam(required = false) UUID shopId) {
        return service.entitlements(ownerUserId, shopId);
    }

    /**
     * The owner's effective allowance per feature, resolved from their current
     * plan. Ceilings only, no usage — prefer /entitlements for anything that
     * needs to show or gate on actual usage.
     */
    @GetMapping("/limits/{ownerUserId}")
    public Map<String, Object> limits(@PathVariable UUID ownerUserId) {
        return service.limitsForOwner(ownerUserId);
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
