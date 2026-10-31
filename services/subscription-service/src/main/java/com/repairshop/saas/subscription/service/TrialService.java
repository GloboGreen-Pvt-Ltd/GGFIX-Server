package com.repairshop.saas.subscription.service;

import com.repairshop.saas.subscription.dto.v2.V2Requests.TrialRequest;
import com.repairshop.saas.subscription.dto.v2.V2Views.MeView;
import com.repairshop.saas.subscription.entity.SubscriptionPlanMaster;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.security.CurrentUser.Actor;
import com.repairshop.saas.subscription.service.OwnerDirectory.ShopInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** POST /subscriptions/trial/activate — free, no payment, ≤ trialMaxShops, once per organisation. */
@Service
@RequiredArgsConstructor
public class TrialService {

    private final TrialPolicy policy;
    private final QuoteService quotes;
    private final SettingsService settings;
    private final OrderLifecycleService lifecycle;
    private final MySubscriptionService mine;
    private final OwnerDirectory directory;

    public MeView activate(Actor owner, TrialRequest req) {
        SettingsService.Settings s = settings.get();
        SubscriptionPlanMaster plan = policy.requireTrialPlan();
        policy.blocker(owner.userId(), directory.shopsOf(owner.userId())).ifPresent(e -> { throw e; });
        List<ShopInfo> shops = quotes.validateShops(owner.userId(), req == null ? null : req.shopIds(),
                s.trialMaxShops(), "You can choose up to " + s.trialMaxShops() + " shops for the free trial");

        String channel = req.channel() == null ? "WEB" : req.channel().trim().toUpperCase();
        if (!List.of("WEB", "ANDROID", "IOS").contains(channel)) {
            throw ApiException.badRequest("INVALID_CHANNEL", "channel must be WEB, ANDROID or IOS");
        }
        lifecycle.activateWithoutPayment(owner.userId(), quotes.trial(plan, shops, s), channel,
                OrderLifecycleService.SOURCE_TRIAL, owner, null);
        return mine.me(owner.userId());
    }
}
