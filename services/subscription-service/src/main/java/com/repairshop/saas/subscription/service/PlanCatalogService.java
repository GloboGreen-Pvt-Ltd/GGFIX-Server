package com.repairshop.saas.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.common.subscription.StoredPlan;
import com.repairshop.saas.common.subscription.SubscriptionPlan;
import com.repairshop.saas.subscription.billing.SubscriptionState;
import com.repairshop.saas.subscription.billing.SubscriptionTerms;
import com.repairshop.saas.subscription.entity.Subscription;
import com.repairshop.saas.subscription.dto.v2.V2Requests.PlanRequest;
import com.repairshop.saas.subscription.dto.v2.V2Requests.PricingItem;
import com.repairshop.saas.subscription.dto.v2.V2Views.PlanView;
import com.repairshop.saas.subscription.entity.SubscriptionPlanMaster;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.repository.SubscriptionPlanMasterRepository;
import com.repairshop.saas.subscription.security.CurrentUser.Actor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Plan master: reads for shops, CRUD + pricing for Management, and Plan JSON snapshots. */
@Service
@RequiredArgsConstructor
public class PlanCatalogService {

    public static final String TRIAL_TYPE = "TRIAL";
    public static final String FREE_TRIAL_CODE = "FREE_TRIAL";

    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "COMING_SOON");
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_]{2,50}$");

    private final SubscriptionPlanMasterRepository repository;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final SettingsService settings;

    // ── reads ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PlanView> allPlans() {
        SettingsService.Settings s = settings.get();
        return repository.findAllByOrderByDisplayOrderAscCodeAsc().stream()
                .map(p -> view(withTrialSettings(p, s)))
                .toList();
    }

    /** Shop-facing catalog: INACTIVE omitted, sorted by displayOrder. */
    @Transactional(readOnly = true)
    public List<PlanView> shopPlans() {
        return allPlans().stream().filter(p -> !"INACTIVE".equals(p.status())).toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanMaster requireByCode(String code) {
        if (code == null || code.isBlank()) throw ApiException.badRequest("PLAN_REQUIRED", "planCode is required");
        return repository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Plan " + code + " does not exist"));
    }

    public java.util.Optional<SubscriptionPlanMaster> findByCode(String code) {
        if (code == null || code.isBlank()) return java.util.Optional.empty();
        try {
            return repository.findByCodeIgnoreCase(code.trim());
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    public static PlanView view(SubscriptionPlanMaster p) {
        int duration = p.getDurationValue() == null ? 0 : p.getDurationValue();
        return new PlanView(
                p.getId(), p.getCode(), p.getName(), p.getPlanType(), p.getDescription(),
                nz(p.getPricePaise()), p.getCurrency() == null ? "INR" : p.getCurrency(),
                duration, p.getDurationUnit(), SubscriptionTerms.durationLabel(duration, p.getDurationUnit()),
                p.getEmployeeLimit(), "PER_SHOP", p.getShopLimit(), p.getSellOrderLimit(), p.getBuyOrderLimit(),
                on(p.getServiceBookingEnabled()), on(p.getPickupEnabled()), on(p.getSmsEnabled()),
                on(p.getWhatsappEnabled()), on(p.getReportsEnabled()), on(p.getExportEnabled()),
                on(p.getPrioritySupportEnabled()),
                p.getAdditionalShopDiscountPct() == null ? BigDecimal.ZERO : p.getAdditionalShopDiscountPct(),
                p.getStatus(), p.getDisplayOrder() == null ? 0 : p.getDisplayOrder(), p.getColor(), p.getIcon(),
                p.getVersion() == null ? 1 : p.getVersion(), features(p), "ACTIVE".equals(p.getStatus()));
    }

    /** Server-built display lines, so every client shows the same wording. */
    public static List<String> features(SubscriptionPlanMaster p) {
        List<String> out = new ArrayList<>();
        if (on(p.getServiceBookingEnabled())) out.add("New Service Booking");
        if (on(p.getPickupEnabled())) out.add("Pickup Service");
        out.add(p.getBuyOrderLimit() == null ? "Buy Products — Unlimited"
                : "Buy Products — up to " + p.getBuyOrderLimit() + " orders");
        out.add(p.getSellOrderLimit() == null ? "Sell Products — Unlimited"
                : "Sell Products — up to " + p.getSellOrderLimit() + " orders");
        out.add(p.getEmployeeLimit() == null ? "Unlimited Employees"
                : "Up to " + p.getEmployeeLimit() + " Employees per Shop");
        if (p.getShopLimit() == null) {
            BigDecimal pct = p.getAdditionalShopDiscountPct();
            out.add(pct != null && pct.signum() > 0
                    ? "Multiple Shops — " + pct.stripTrailingZeros().toPlainString() + "% off each additional shop"
                    : "Multiple Shops");
        } else {
            out.add(p.getShopLimit() == 1 ? "1 Shop" : "Up to " + p.getShopLimit() + " Shops");
        }
        if (on(p.getWhatsappEnabled())) out.add("WhatsApp Notifications");
        if (on(p.getSmsEnabled())) out.add("SMS Notifications");
        if (on(p.getReportsEnabled())) out.add("Reports & Analytics");
        if (on(p.getExportEnabled())) out.add("Data Export");
        if (on(p.getPrioritySupportEnabled())) out.add("Priority Support");
        return out;
    }

    /**
     * The trial's term and shop cap are governed by the settings (trialDays,
     * trialMaxShops), so a TRIAL plan is presented with those values. Returns
     * a detached copy; the stored plan row is untouched.
     */
    public static SubscriptionPlanMaster withTrialSettings(SubscriptionPlanMaster p, SettingsService.Settings s) {
        if (p == null || !TRIAL_TYPE.equals(p.getPlanType())) return p;
        return p.toBuilder()
                .durationValue(s.trialDays())
                .durationUnit(SubscriptionTerms.DAYS)
                .shopLimit(s.trialMaxShops())
                .pricePaise(0L)
                .build();
    }

    /** Display facts about whatever plan a subscriptions row is on. */
    public record PlanInfo(String name, String planType, String durationLabel) {}

    /**
     * Name/type/duration for a row: the purchase-time snapshot for v2 rows,
     * the legacy enum for FREE_TRIAL / BASIC rows, else the live master.
     * Trial durations come from the row's own trial window.
     */
    public PlanInfo describe(Subscription s) {
        if (s == null) return new PlanInfo(null, null, null);
        String trialLabel = null;
        if (SubscriptionState.isTrial(s) && s.getTrialStartDate() != null && s.getTrialEndDate() != null) {
            long days = ChronoUnit.DAYS.between(
                    s.getTrialStartDate().atZone(SubscriptionTerms.IST).toLocalDate(),
                    s.getTrialEndDate().atZone(SubscriptionTerms.IST).toLocalDate());
            trialLabel = SubscriptionTerms.durationLabel((int) Math.max(days, 1), SubscriptionTerms.DAYS);
        }
        PlanView snap = readSnapshot(s.getPlanSnapshot());
        if (snap != null) {
            return new PlanInfo(snap.name(), snap.planType(), trialLabel != null ? trialLabel : snap.durationLabel());
        }
        SubscriptionPlan legacy = SubscriptionPlan.from(s.getSubscriptionType());
        if (legacy == null) legacy = SubscriptionPlan.from(s.getPlanCode());
        if (legacy == SubscriptionPlan.FREE_TRIAL) {
            return new PlanInfo(legacy.getDisplayName(), TRIAL_TYPE, trialLabel != null ? trialLabel
                    : SubscriptionTerms.durationLabel(legacy.getDurationDays(), SubscriptionTerms.DAYS));
        }
        if (legacy == SubscriptionPlan.BASIC) {
            // Legacy BASIC was sold as a 365-day year.
            return new PlanInfo(legacy.getDisplayName(), "BASIC", "12 Months");
        }
        return findByCode(s.getPlanCode())
                .map(p -> new PlanInfo(p.getName(), p.getPlanType(),
                        SubscriptionTerms.durationLabel(p.getDurationValue(), p.getDurationUnit())))
                .orElseGet(() -> new PlanInfo(StoredPlan.prettify(s.getPlanCode()), s.getPlanCode(), null));
    }

    public String snapshot(SubscriptionPlanMaster p) {
        try {
            return mapper.writeValueAsString(view(p));
        } catch (Exception e) {
            throw new IllegalStateException("Could not snapshot plan " + p.getCode(), e);
        }
    }

    public PlanView readSnapshot(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, PlanView.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Management writes ──────────────────────────────────────────────────

    @Transactional
    public PlanView create(PlanRequest req, Actor actor) {
        if (req == null) throw ApiException.badRequest("INVALID_PLAN", "Plan body is required");
        String code = req.code() == null ? null : req.code().trim().toUpperCase();
        if (code == null || !CODE.matcher(code).matches()) {
            throw ApiException.badRequest("INVALID_PLAN", "code must be 2–50 characters of A–Z, 0–9 or _");
        }
        if (repository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("PLAN_CODE_EXISTS", "A plan with code " + code + " already exists");
        }
        SubscriptionPlanMaster p = SubscriptionPlanMaster.builder()
                .code(code)
                .currency("INR")
                .serviceBookingEnabled(true).pickupEnabled(false).smsEnabled(false).whatsappEnabled(false)
                .reportsEnabled(false).exportEnabled(false).prioritySupportEnabled(false)
                .additionalShopDiscountPct(BigDecimal.ZERO)
                .status("INACTIVE")
                .displayOrder(100)
                .version(1)
                .build();
        apply(p, req, true);
        validate(p);
        p = repository.save(p);
        if (TRIAL_TYPE.equals(p.getPlanType())) settings.syncSettingsFromTrialPlan(p, actor);
        audit.log(actor, "PLAN_CREATED", "PLAN", p.getId().toString(), view(p));
        return view(p);
    }

    @Transactional
    public PlanView update(UUID id, PlanRequest req, Actor actor) {
        if (req == null) throw ApiException.badRequest("INVALID_PLAN", "Plan body is required");
        SubscriptionPlanMaster p = require(id);
        PlanView before = view(p);
        if (req.code() != null && !req.code().trim().equalsIgnoreCase(p.getCode())) {
            String code = req.code().trim().toUpperCase();
            if (!CODE.matcher(code).matches()) {
                throw ApiException.badRequest("INVALID_PLAN", "code must be 2–50 characters of A–Z, 0–9 or _");
            }
            if (repository.existsByCodeIgnoreCase(code)) {
                throw ApiException.conflict("PLAN_CODE_EXISTS", "A plan with code " + code + " already exists");
            }
            p.setCode(code);
        }
        apply(p, req, false);
        validate(p);
        p.setVersion((p.getVersion() == null ? 1 : p.getVersion()) + 1);
        p = repository.save(p);
        if (TRIAL_TYPE.equals(p.getPlanType())) settings.syncSettingsFromTrialPlan(p, actor);
        audit.log(actor, "PLAN_UPDATED", "PLAN", id.toString(), Map.of("before", before, "after", view(p)));
        return view(p);
    }

    @Transactional
    public PlanView setStatus(UUID id, String status, Actor actor) {
        String s = status == null ? null : status.trim().toUpperCase();
        if (s == null || !STATUSES.contains(s)) {
            throw ApiException.badRequest("INVALID_STATUS", "status must be ACTIVE, INACTIVE or COMING_SOON");
        }
        SubscriptionPlanMaster p = require(id);
        String before = p.getStatus();
        p.setStatus(s);
        p.setVersion((p.getVersion() == null ? 1 : p.getVersion()) + 1);
        p = repository.save(p);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", p.getCode());
        details.put("from", before);
        details.put("to", s);
        audit.log(actor, "PLAN_STATUS_CHANGED", "PLAN", id.toString(), details);
        return view(p);
    }

    @Transactional
    public List<PlanView> updatePricing(List<PricingItem> items, Actor actor) {
        if (items == null || items.isEmpty()) throw ApiException.badRequest("INVALID_PRICING", "items are required");
        for (PricingItem item : items) {
            if (item == null || item.planId() == null) {
                throw ApiException.badRequest("INVALID_PRICING", "Every item needs a planId");
            }
            SubscriptionPlanMaster p = require(item.planId());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("code", p.getCode());
            details.put("fromPricePaise", p.getPricePaise());
            details.put("fromDiscountPct", p.getAdditionalShopDiscountPct());
            if (item.pricePaise() != null) p.setPricePaise(item.pricePaise());
            if (item.additionalShopDiscountPct() != null) p.setAdditionalShopDiscountPct(item.additionalShopDiscountPct());
            validate(p);
            p.setVersion((p.getVersion() == null ? 1 : p.getVersion()) + 1);
            repository.save(p);
            details.put("toPricePaise", p.getPricePaise());
            details.put("toDiscountPct", p.getAdditionalShopDiscountPct());
            audit.log(actor, "PLAN_PRICING_UPDATED", "PLAN", p.getId().toString(), details);
        }
        return allPlans();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private SubscriptionPlanMaster require(UUID id) {
        if (id == null) throw ApiException.badRequest("PLAN_REQUIRED", "plan id is required");
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Plan not found"));
    }

    /**
     * Copy request fields onto the plan. The four limits are always taken as
     * given, because null is meaningful there (= unlimited) — PUT sends the
     * whole Plan JSON. For every other field null means "keep".
     */
    private static void apply(SubscriptionPlanMaster p, PlanRequest r, boolean create) {
        if (r.name() != null) p.setName(r.name().trim());
        if (r.planType() != null) p.setPlanType(r.planType().trim().toUpperCase());
        if (r.description() != null || create) p.setDescription(r.description());
        if (r.pricePaise() != null) p.setPricePaise(r.pricePaise());
        if (r.durationValue() != null) p.setDurationValue(r.durationValue());
        if (r.durationUnit() != null) p.setDurationUnit(r.durationUnit().trim().toUpperCase());
        p.setEmployeeLimit(r.employeeLimit());
        p.setShopLimit(r.shopLimit());
        p.setSellOrderLimit(r.sellOrderLimit());
        p.setBuyOrderLimit(r.buyOrderLimit());
        if (r.serviceBookingEnabled() != null) p.setServiceBookingEnabled(r.serviceBookingEnabled());
        if (r.pickupEnabled() != null) p.setPickupEnabled(r.pickupEnabled());
        if (r.smsEnabled() != null) p.setSmsEnabled(r.smsEnabled());
        if (r.whatsappEnabled() != null) p.setWhatsappEnabled(r.whatsappEnabled());
        if (r.reportsEnabled() != null) p.setReportsEnabled(r.reportsEnabled());
        if (r.exportEnabled() != null) p.setExportEnabled(r.exportEnabled());
        if (r.prioritySupportEnabled() != null) p.setPrioritySupportEnabled(r.prioritySupportEnabled());
        if (r.additionalShopDiscountPct() != null) p.setAdditionalShopDiscountPct(r.additionalShopDiscountPct());
        if (r.status() != null) p.setStatus(r.status().trim().toUpperCase());
        if (r.displayOrder() != null) p.setDisplayOrder(r.displayOrder());
        if (r.color() != null) p.setColor(r.color());
        if (r.icon() != null) p.setIcon(r.icon());
    }

    private static void validate(SubscriptionPlanMaster p) {
        if (p.getName() == null || p.getName().isBlank()) throw invalid("name is required");
        if (p.getPlanType() == null || p.getPlanType().isBlank()) throw invalid("planType is required");
        if (p.getPricePaise() == null || p.getPricePaise() < 0) throw invalid("pricePaise must be 0 or more");
        if (TRIAL_TYPE.equals(p.getPlanType()) && p.getPricePaise() != 0) throw invalid("A TRIAL plan must be free");
        if (TRIAL_TYPE.equals(p.getPlanType())
                && (p.getShopLimit() == null || !SubscriptionTerms.DAYS.equals(p.getDurationUnit()))) {
            throw invalid("A TRIAL plan needs a shopLimit and a duration in DAYS");
        }
        if (p.getDurationValue() == null || p.getDurationValue() < 1 || p.getDurationValue() > 120) {
            throw invalid("durationValue must be between 1 and 120");
        }
        if (!SubscriptionTerms.DAYS.equals(p.getDurationUnit()) && !SubscriptionTerms.MONTHS.equals(p.getDurationUnit())) {
            throw invalid("durationUnit must be DAYS or MONTHS");
        }
        BigDecimal pct = p.getAdditionalShopDiscountPct();
        if (pct == null || pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0 || pct.scale() > 2) {
            throw invalid("additionalShopDiscountPct must be between 0 and 100 (2 decimals)");
        }
        if (!STATUSES.contains(p.getStatus())) throw invalid("status must be ACTIVE, INACTIVE or COMING_SOON");
        if (p.getEmployeeLimit() != null && p.getEmployeeLimit() < 0) throw invalid("employeeLimit must be 0 or more");
        if (p.getShopLimit() != null && p.getShopLimit() < 1) throw invalid("shopLimit must be 1 or more");
        if (p.getSellOrderLimit() != null && p.getSellOrderLimit() < 0) throw invalid("sellOrderLimit must be 0 or more");
        if (p.getBuyOrderLimit() != null && p.getBuyOrderLimit() < 0) throw invalid("buyOrderLimit must be 0 or more");
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("INVALID_PLAN", message);
    }

    private static boolean on(Boolean b) {
        return Boolean.TRUE.equals(b);
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }
}
