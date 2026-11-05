package com.repairshop.saas.subscription.service;

import com.repairshop.saas.subscription.billing.SubscriptionState;
import com.repairshop.saas.subscription.entity.Subscription;
import com.repairshop.saas.subscription.entity.SubscriptionPlanMaster;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.repository.SubscriptionOrderRepository;
import com.repairshop.saas.subscription.repository.SubscriptionRepository;
import com.repairshop.saas.subscription.service.OwnerDirectory.ShopInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Who may start the free trial. With trialOncePerOrg (default) the trial is
 * for brand-new organisations only: an owner who already has a subscriptions
 * row (registration creates a FREE_TRIAL row; legacy BASIC owners have one
 * too), a TRIAL order, or another owner account sharing their mobile number
 * that had a trial, is refused.
 */
@Component
@RequiredArgsConstructor
public class TrialPolicy {

    private final SubscriptionRepository subscriptions;
    private final SubscriptionOrderRepository orders;
    private final PlanCatalogService plans;
    private final SettingsService settings;
    private final OwnerDirectory directory;
    private final Clock clock;

    /** The active FREE_TRIAL plan, or a 409. */
    public SubscriptionPlanMaster requireTrialPlan() {
        SubscriptionPlanMaster plan = plans.findByCode(PlanCatalogService.FREE_TRIAL_CODE).orElse(null);
        if (plan == null || !"ACTIVE".equals(plan.getStatus())) {
            throw ApiException.conflict("TRIAL_UNAVAILABLE", "The free trial is not available right now");
        }
        return plan;
    }

    /** The reason the owner cannot start a trial, if any (ignores the shop-mobile check). */
    public Optional<ApiException> blocker(UUID ownerUserId, List<ShopInfo> selectedShops) {
        SettingsService.Settings s = settings.get();
        List<Subscription> rows = subscriptions.findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId);
        Subscription current = rows.isEmpty() ? null : rows.get(0);

        if (!s.trialOncePerOrg()) {
            if (current != null && SubscriptionState.isRunning(SubscriptionState.status(current, clock.instant()))) {
                return Optional.of(ApiException.conflict("TRIAL_NOT_AVAILABLE",
                        "You already have an active subscription"));
            }
            return Optional.empty();
        }
        boolean hadTrial = rows.stream().anyMatch(r -> r.getTrialStartDate() != null || SubscriptionState.isTrial(r))
                || orders.existsByOwnerUserIdAndPurpose(ownerUserId, QuoteService.TRIAL);
        if (hadTrial) {
            return Optional.of(ApiException.conflict("TRIAL_ALREADY_USED",
                    "Your organisation has already used its free trial"));
        }
        if (current != null) {
            return Optional.of(ApiException.conflict("TRIAL_NOT_AVAILABLE",
                    "The free trial is only for new accounts"));
        }
        String phone = directory.owner(ownerUserId).map(OwnerDirectory.OwnerInfo::phone).orElse(null);
        List<String> mobiles = selectedShops == null ? List.of()
                : selectedShops.stream().map(ShopInfo::mobile).toList();
        if (directory.trialUsedByAnotherOwner(ownerUserId, phone, mobiles)) {
            return Optional.of(ApiException.conflict("TRIAL_ALREADY_USED",
                    "A free trial has already been used with this mobile number"));
        }
        return Optional.empty();
    }

    public boolean isAvailable(UUID ownerUserId) {
        try {
            requireTrialPlan();
            return blocker(ownerUserId, directory.shopsOf(ownerUserId)).isEmpty();
        } catch (ApiException e) {
            return false;
        }
    }
}
