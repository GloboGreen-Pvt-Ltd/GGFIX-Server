package com.repairshop.saas.subscription.controller;

import com.repairshop.saas.subscription.dto.v2.V2Requests.ExtendRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.ManualActivateRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.PlanRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.PlanStatusRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.PricingRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.ReasonRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.RefundRequest;
import com.repairshop.saas.subscription.dto.v2.V2Views.AdminPaymentRow;
import com.repairshop.saas.subscription.dto.v2.V2Views.AdminSettingsView;
import com.repairshop.saas.subscription.dto.v2.V2Views.AdminSubscriptionDetail;
import com.repairshop.saas.subscription.dto.v2.V2Views.AdminSubscriptionRow;
import com.repairshop.saas.subscription.dto.v2.V2Views.AuditLogView;
import com.repairshop.saas.subscription.dto.v2.V2Views.PlanView;
import com.repairshop.saas.subscription.security.CurrentUser;
import com.repairshop.saas.subscription.service.AdminSubscriptionService;
import com.repairshop.saas.subscription.service.AuditService;
import com.repairshop.saas.subscription.service.PlanCatalogService;
import com.repairshop.saas.subscription.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Management routes (contract "Management routes"). Every handler first
 * requires an admin JWT (SUPER_ADMIN or ADMIN): 401 without a token, 403
 * without the role. Every write is audit-logged by the service it calls.
 */
@RestController
@RequestMapping("/subscriptions/admin")
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private final CurrentUser currentUser;
    private final PlanCatalogService plans;
    private final SettingsService settings;
    private final AdminSubscriptionService admin;
    private final AuditService audit;

    // ── plans & pricing ─────────────────────────────────────────────────────

    @GetMapping("/plans")
    public List<PlanView> plans() {
        currentUser.requireAdmin();
        return plans.allPlans();
    }

    @PostMapping("/plans")
    public PlanView createPlan(@RequestBody PlanRequest req) {
        return plans.create(req, currentUser.requireAdmin());
    }

    @PutMapping("/plans/{id}")
    public PlanView updatePlan(@PathVariable UUID id, @RequestBody PlanRequest req) {
        return plans.update(id, req, currentUser.requireAdmin());
    }

    @PatchMapping("/plans/{id}/status")
    public PlanView planStatus(@PathVariable UUID id, @RequestBody PlanStatusRequest req) {
        CurrentUser.Actor actor = currentUser.requireAdmin();
        return plans.setStatus(id, req == null ? null : req.status(), actor);
    }

    @PutMapping("/pricing")
    public List<PlanView> pricing(@RequestBody PricingRequest req) {
        CurrentUser.Actor actor = currentUser.requireAdmin();
        return plans.updatePricing(req == null ? null : req.items(), actor);
    }

    // ── subscriptions ──────────────────────────────────────────────────────

    @GetMapping("/subscriptions")
    public List<AdminSubscriptionRow> subscriptions(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String q) {
        currentUser.requireAdmin();
        return admin.list(status, q);
    }

    @GetMapping("/subscriptions/{id}")
    public AdminSubscriptionDetail subscription(@PathVariable UUID id) {
        currentUser.requireAdmin();
        return admin.detail(id);
    }

    @PostMapping("/subscriptions/{id}/extend")
    public AdminSubscriptionDetail extend(@PathVariable UUID id, @RequestBody ExtendRequest req) {
        return admin.extend(id, req, currentUser.requireAdmin());
    }

    @PostMapping("/subscriptions/{id}/cancel")
    public AdminSubscriptionDetail cancel(@PathVariable UUID id, @RequestBody ReasonRequest req) {
        CurrentUser.Actor actor = currentUser.requireAdmin();
        return admin.cancel(id, req == null ? null : req.reason(), actor);
    }

    @PostMapping("/subscriptions/manual-activate")
    public AdminSubscriptionDetail manualActivate(@RequestBody ManualActivateRequest req) {
        return admin.manualActivate(req, currentUser.requireAdmin());
    }

    // ── payments ───────────────────────────────────────────────────────────

    @GetMapping("/payments")
    public List<AdminPaymentRow> payments(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String q) {
        currentUser.requireAdmin();
        return admin.payments(status, q);
    }

    @PostMapping("/payments/{paymentId}/refund")
    public AdminPaymentRow refund(@PathVariable UUID paymentId, @RequestBody RefundRequest req) {
        return admin.refund(paymentId, req, currentUser.requireAdmin());
    }

    // ── settings & audit ───────────────────────────────────────────────────

    @GetMapping("/settings")
    public AdminSettingsView settings() {
        currentUser.requireAdmin();
        return settings.adminView();
    }

    @PutMapping("/settings")
    public AdminSettingsView updateSettings(@RequestBody Map<String, Object> body) {
        return settings.update(body, currentUser.requireAdmin());
    }

    @GetMapping("/audit-logs")
    public List<AuditLogView> auditLogs(@RequestParam(required = false) Integer limit) {
        currentUser.requireAdmin();
        return audit.recent(limit);
    }
}
