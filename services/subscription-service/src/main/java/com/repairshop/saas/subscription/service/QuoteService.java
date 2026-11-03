package com.repairshop.saas.subscription.service;

import com.repairshop.saas.subscription.billing.GstCalculator;
import com.repairshop.saas.subscription.billing.PricingCalculator;
import com.repairshop.saas.subscription.billing.SubscriptionState;
import com.repairshop.saas.subscription.billing.SubscriptionTerms;
import com.repairshop.saas.subscription.dto.v2.V2Views.Line;
import com.repairshop.saas.subscription.dto.v2.V2Views.QuoteView;
import com.repairshop.saas.subscription.entity.Subscription;
import com.repairshop.saas.subscription.entity.SubscriptionPlanMaster;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.repository.SubscriptionRepository;
import com.repairshop.saas.subscription.service.OwnerDirectory.ShopInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the authoritative Quote. Every shop must belong to the caller (JWT
 * sub), appear once, and be active; the plan must exist and be purchasable;
 * the purpose/term follow the contract's renew/upgrade rule.
 */
@Service
@RequiredArgsConstructor
public class QuoteService {

    public static final String NEW = "NEW";
    public static final String RENEW = "RENEW";
    public static final String UPGRADE = "UPGRADE";
    public static final String TRIAL = "TRIAL";

    private static final int MAX_SHOPS = 100;

    private final PlanCatalogService plans;
    private final SettingsService settings;
    private final OwnerDirectory directory;
    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    /** A validated quote plus what was resolved to build it (for order creation). */
    public record QuoteContext(SubscriptionPlanMaster plan, QuoteView quote, List<ShopInfo> shops) {}

    public QuoteView quote(UUID ownerUserId, String planCode, List<UUID> shopIds, String forcedPurpose) {
        return build(ownerUserId, planCode, shopIds, forcedPurpose).quote();
    }

    public QuoteContext build(UUID ownerUserId, String planCode, List<UUID> shopIds, String forcedPurpose) {
        SubscriptionPlanMaster plan = plans.requireByCode(planCode);
        if (PlanCatalogService.TRIAL_TYPE.equals(plan.getPlanType())) {
            throw ApiException.badRequest("TRIAL_NOT_PURCHASABLE", "Start the free trial from the trial option instead");
        }
        if (!"ACTIVE".equals(plan.getStatus())) {
            throw ApiException.conflict("PLAN_NOT_PURCHASABLE", plan.getName() + " is not available for purchase");
        }
        List<ShopInfo> shops = validateShops(ownerUserId, shopIds, plan.getShopLimit(), null);
        QuoteView quote = compute(plan, shops.stream().map(ShopInfo::id).toList(), shops.size(),
                current(ownerUserId), forcedPurpose, settings.get(), placeOfSupply(shops));
        return new QuoteContext(plan, quote, shops);
    }

    /** Calculator only: no shops, no ownership; purpose uses the caller's row when there is one. */
    public QuoteView preview(UUID ownerUserIdOrNull, String planCode, Integer shopCount) {
        SettingsService.Settings s = settings.get();
        SubscriptionPlanMaster plan = PlanCatalogService.withTrialSettings(plans.requireByCode(planCode), s);
        int n = shopCount == null ? 1 : shopCount;
        if (n < 1 || n > MAX_SHOPS) {
            throw ApiException.badRequest("INVALID_SHOP_COUNT", "shopCount must be between 1 and " + MAX_SHOPS);
        }
        if (plan.getShopLimit() != null && n > plan.getShopLimit()) {
            throw ApiException.badRequest("SHOP_LIMIT_EXCEEDED",
                    plan.getName() + " allows up to " + plan.getShopLimit() + " shops");
        }
        Subscription current = ownerUserIdOrNull == null ? null : current(ownerUserIdOrNull);
        return compute(plan, List.of(), n, current, null, s, null);
    }

    /** The zero-price TRIAL "quote" snapshotted on a trial order. */
    public QuoteContext trial(SubscriptionPlanMaster trialPlan, List<ShopInfo> shops, SettingsService.Settings s) {
        SubscriptionPlanMaster plan = PlanCatalogService.withTrialSettings(trialPlan, s);
        QuoteView q = compute(plan, shops.stream().map(ShopInfo::id).toList(), shops.size(), null, TRIAL, s,
                placeOfSupply(shops));
        return new QuoteContext(plan, q, shops);
    }

    /**
     * Ownership + sanity checks on the selected shops. Fail-closed: an owner
     * whose shops cannot be read owns nothing.
     */
    public List<ShopInfo> validateShops(UUID ownerUserId, List<UUID> shopIds, Integer shopLimit, String limitMessage) {
        if (shopIds == null || shopIds.isEmpty()) {
            throw ApiException.badRequest("SHOPS_REQUIRED", "Select at least one shop");
        }
        if (shopIds.stream().anyMatch(id -> id == null)) {
            throw ApiException.badRequest("INVALID_SHOP", "Shop ids must not be empty");
        }
        Set<UUID> seen = new HashSet<>();
        for (UUID id : shopIds) {
            if (!seen.add(id)) throw ApiException.badRequest("DUPLICATE_SHOPS", "Each shop can be selected only once");
        }
        if (shopIds.size() > MAX_SHOPS) {
            throw ApiException.badRequest("SHOP_LIMIT_EXCEEDED", "Too many shops selected");
        }
        Map<UUID, ShopInfo> owned = directory.shopsOf(ownerUserId).stream()
                .collect(Collectors.toMap(ShopInfo::id, Function.identity(), (a, b) -> a));
        List<ShopInfo> out = new ArrayList<>();
        for (UUID id : shopIds) {
            ShopInfo shop = owned.get(id);
            if (shop == null) {
                throw ApiException.forbidden("SHOP_NOT_OWNED", "One or more selected shops are not yours");
            }
            if (!shop.active()) {
                throw ApiException.badRequest("SHOP_INACTIVE", (shop.name() == null ? "A selected shop" : shop.name())
                        + " is inactive and cannot be subscribed");
            }
            out.add(shop);
        }
        if (shopLimit != null && out.size() > shopLimit) {
            throw ApiException.badRequest("SHOP_LIMIT_EXCEEDED",
                    limitMessage != null ? limitMessage : "This plan allows up to " + shopLimit + " shops");
        }
        return out;
    }

    public Subscription current(UUID ownerUserId) {
        if (ownerUserId == null) return null;
        List<Subscription> rows = subscriptions.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Place of supply = the billing (first selected) shop: GSTIN prefix, else its state. Null when unknown. */
    GstCalculator.PlaceOfSupply placeOfSupply(List<ShopInfo> shops) {
        if (shops == null || shops.isEmpty()) return null;
        return directory.billingShop(shops.get(0).id())
                .map(b -> GstCalculator.placeOfSupply(b.gstNumber(), b.state()))
                .orElse(null);
    }

    QuoteView compute(SubscriptionPlanMaster plan, List<UUID> shopIds, int shopCount, Subscription current,
                      String forcedPurpose, SettingsService.Settings s, GstCalculator.PlaceOfSupply pos) {
        Instant now = clock.instant();
        String state = SubscriptionState.status(current, now);
        boolean activePaid = SubscriptionState.ACTIVE.equals(state);
        boolean sameCode = current != null && plan.getCode().equalsIgnoreCase(current.getPlanCode());
        boolean trialPlan = PlanCatalogService.TRIAL_TYPE.equals(plan.getPlanType());

        String purpose;
        if (TRIAL.equals(forcedPurpose) || trialPlan) {
            purpose = TRIAL;
        } else if (RENEW.equals(forcedPurpose)) {
            if (!sameCode) {
                throw ApiException.conflict("RENEW_NOT_AVAILABLE", "You can only renew the plan you are on");
            }
            purpose = RENEW;
        } else if (UPGRADE.equals(forcedPurpose)) {
            if (activePaid && sameCode) {
                throw ApiException.conflict("UPGRADE_SAME_PLAN", "You are already on this plan — renew it instead");
            }
            purpose = UPGRADE;
        } else if (activePaid && sameCode) {
            purpose = RENEW;
        } else if (SubscriptionState.isRunning(state)) {
            purpose = UPGRADE;
        } else {
            purpose = NEW;
        }

        boolean extend = !trialPlan && activePaid && sameCode;
        Instant currentEnd = SubscriptionState.endsAt(current);
        Instant startsAt = extend ? currentEnd : now;
        Instant endsAt = SubscriptionTerms.termEnd(startsAt, plan.getDurationValue(), plan.getDurationUnit());
        boolean replacesCurrent = !trialPlan && activePaid && !sameCode;

        String notice = null;
        if (replacesCurrent) {
            notice = "Your current " + plans.describe(current).name() + " plan (valid till "
                    + SubscriptionTerms.date(currentEnd) + ") ends as soon as this purchase is activated. "
                    + "The unused time is not refunded or credited.";
        } else if (extend) {
            notice = "This renewal starts when your current term ends on " + SubscriptionTerms.date(currentEnd) + ".";
        } else if (SubscriptionState.TRIAL.equals(state) && !trialPlan) {
            notice = "Your free trial ends as soon as this plan is activated.";
        }

        // Tax is split out only when GST applies (supplier GSTIN set AND rate > 0); otherwise 0 everywhere.
        PricingCalculator.Breakdown b = PricingCalculator.price(
                plan.getPricePaise(), plan.getAdditionalShopDiscountPct(), shopCount, s.effectiveTaxPercent());
        var gst = GstCalculator.compute(b.totalPaise(), b.subtotalPaise(), b.discountPaise(), s.taxPercent(),
                s.gstApplicable(), s.supplierStateCode(), pos);
        String durationLabel = SubscriptionTerms.durationLabel(plan.getDurationValue(), plan.getDurationUnit());

        List<Line> lines = new ArrayList<>();
        lines.add(new Line(plan.getName() + " · " + durationLabel + " — first shop", b.firstShopPaise()));
        if (b.additionalShopsCount() > 0) {
            lines.add(new Line("Additional shops (" + b.additionalShopsCount() + " × "
                    + SubscriptionTerms.rupees(b.additionalShopUnitPaise()) + ", "
                    + b.additionalShopDiscountPct().stripTrailingZeros().toPlainString() + "% off)",
                    b.additionalShopsTotalPaise()));
        }

        return new QuoteView(
                plan.getCode(), plan.getName(), durationLabel, purpose, shopCount, List.copyOf(shopIds),
                b.firstShopPaise(), b.additionalShopsCount(), b.additionalShopUnitPaise(),
                b.additionalShopsTotalPaise(), b.additionalShopDiscountPct(), b.discountPaise(), b.subtotalPaise(),
                b.taxPercent(), true, b.taxPaise(), b.totalPaise(), "INR", lines, startsAt, endsAt,
                b.totalPaise(), replacesCurrent, notice, gst);
    }
}
