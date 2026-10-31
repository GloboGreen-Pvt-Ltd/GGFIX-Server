package com.repairshop.saas.subscription.service;

import com.repairshop.saas.subscription.dto.v2.V2Views.AdminSettingsView;
import com.repairshop.saas.subscription.entity.SubscriptionSetting;
import com.repairshop.saas.subscription.exception.ApiException;
import com.repairshop.saas.subscription.razorpay.RazorpayProperties;
import com.repairshop.saas.subscription.repository.SubscriptionPlanMasterRepository;
import com.repairshop.saas.subscription.repository.SubscriptionSettingRepository;
import com.repairshop.saas.subscription.entity.SubscriptionPlanMaster;
import com.repairshop.saas.subscription.security.CurrentUser.Actor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * subscription_settings (key/value). Missing keys fall back to the contract
 * defaults, so the service works even before an admin has saved anything.
 * Razorpay keys are NOT settings: they come only from the environment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    public static final String DEFAULT_BUSINESS_NAME = "Globogreen System & Technology Private Limited";

    private static final Set<String> INT_KEYS = Set.of("trialDays", "trialMaxShops", "renewalReminderDays",
            "expiryAlertDays", "paymentRetryLimit", "gracePeriodDays", "quoteTtlMinutes");
    private static final Set<String> BOOL_KEYS = Set.of("taxInclusive", "trialOncePerOrg", "webCheckoutEnabled",
            "androidCheckoutEnabled", "iosCheckoutEnabled");
    private static final Set<String> TEXT_KEYS = Set.of("receiptBusinessName", "receiptAddress", "receiptGstin",
            "receiptEmail", "receiptPhone");

    private final SubscriptionSettingRepository repository;
    private final RazorpayProperties razorpay;
    private final AuditService audit;
    private final SubscriptionPlanMasterRepository planRepository;

    /** Typed view of the settings with defaults applied. */
    public record Settings(
            BigDecimal taxPercent, boolean taxInclusive, int trialDays, int trialMaxShops, boolean trialOncePerOrg,
            int gracePeriodDays, int quoteTtlMinutes, int paymentRetryLimit, int renewalReminderDays,
            int expiryAlertDays, boolean webCheckoutEnabled, boolean androidCheckoutEnabled,
            boolean iosCheckoutEnabled, String receiptBusinessName, String receiptAddress, String receiptGstin,
            String receiptEmail, String receiptPhone) {

        public static Settings defaults() {
            return new Settings(BigDecimal.ZERO, true, 15, 2, true, 0, 30, 3, 7, 7, true, false, false,
                    DEFAULT_BUSINESS_NAME, null, null, null, null);
        }

        public boolean channelEnabled(String channel) {
            return switch (channel) {
                case "WEB" -> webCheckoutEnabled;
                case "ANDROID" -> androidCheckoutEnabled;
                case "IOS" -> iosCheckoutEnabled;
                default -> false;
            };
        }
    }

    @Transactional(readOnly = true)
    public Settings get() {
        Map<String, String> raw = new HashMap<>();
        try {
            for (SubscriptionSetting s : repository.findAll()) raw.put(s.getKey(), s.getValue());
        } catch (Exception e) {
            log.warn("Could not read subscription_settings, using defaults: {}", e.toString());
        }
        Settings d = Settings.defaults();
        return new Settings(
                dec(raw, "taxPercent", d.taxPercent()),
                bool(raw, "taxInclusive", d.taxInclusive()),
                integer(raw, "trialDays", d.trialDays()),
                integer(raw, "trialMaxShops", d.trialMaxShops()),
                bool(raw, "trialOncePerOrg", d.trialOncePerOrg()),
                integer(raw, "gracePeriodDays", d.gracePeriodDays()),
                integer(raw, "quoteTtlMinutes", d.quoteTtlMinutes()),
                integer(raw, "paymentRetryLimit", d.paymentRetryLimit()),
                integer(raw, "renewalReminderDays", d.renewalReminderDays()),
                integer(raw, "expiryAlertDays", d.expiryAlertDays()),
                bool(raw, "webCheckoutEnabled", d.webCheckoutEnabled()),
                bool(raw, "androidCheckoutEnabled", d.androidCheckoutEnabled()),
                bool(raw, "iosCheckoutEnabled", d.iosCheckoutEnabled()),
                raw.containsKey("receiptBusinessName") && notBlank(raw.get("receiptBusinessName"))
                        ? raw.get("receiptBusinessName") : d.receiptBusinessName(),
                blankToNull(raw.get("receiptAddress")),
                blankToNull(raw.get("receiptGstin")),
                blankToNull(raw.get("receiptEmail")),
                blankToNull(raw.get("receiptPhone")));
    }

    public AdminSettingsView adminView() {
        Settings s = get();
        return new AdminSettingsView(
                razorpay.mode(), razorpay.isConfigured(), razorpay.isWebhookConfigured(), razorpay.keyIdMasked(),
                s.taxPercent(), s.taxInclusive(), s.trialDays(), s.trialMaxShops(), s.trialOncePerOrg(),
                s.renewalReminderDays(), s.expiryAlertDays(), s.paymentRetryLimit(), s.gracePeriodDays(),
                s.quoteTtlMinutes(), s.webCheckoutEnabled(), s.androidCheckoutEnabled(), s.iosCheckoutEnabled(),
                s.receiptBusinessName(), s.receiptAddress(), s.receiptGstin(), s.receiptEmail(), s.receiptPhone(),
                razorpay.isModeMismatch(), razorpay.keyMode(), razorpay.unavailableReason());
    }

    /**
     * Partial update: only recognised keys present in the body are written.
     * Read-only fields (razorpayMode, keyIdMasked, …) are ignored.
     */
    @Transactional
    public AdminSettingsView update(Map<String, Object> body, Actor actor) {
        if (body == null) body = Map.of();
        Map<String, String> changes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : body.entrySet()) {
            String key = e.getKey();
            Object v = e.getValue();
            if ("taxPercent".equals(key)) {
                BigDecimal rate = parseDecimal(key, v);
                if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw ApiException.badRequest("INVALID_SETTING", "taxPercent must be between 0 and 100");
                }
                changes.put(key, rate.stripTrailingZeros().toPlainString());
            } else if (INT_KEYS.contains(key)) {
                int n = parseInt(key, v);
                int min = ("trialDays".equals(key) || "trialMaxShops".equals(key) || "quoteTtlMinutes".equals(key)
                        || "paymentRetryLimit".equals(key)) ? 1 : 0;
                if (n < min || n > 3650) {
                    throw ApiException.badRequest("INVALID_SETTING", key + " must be between " + min + " and 3650");
                }
                changes.put(key, Integer.toString(n));
            } else if (BOOL_KEYS.contains(key)) {
                boolean b = parseBool(key, v);
                if ("taxInclusive".equals(key) && !b) {
                    throw ApiException.badRequest("INVALID_SETTING", "Only tax-inclusive pricing is supported");
                }
                changes.put(key, Boolean.toString(b));
            } else if (TEXT_KEYS.contains(key)) {
                String text = v == null ? null : v.toString().trim();
                if (text != null && text.length() > 500) {
                    throw ApiException.badRequest("INVALID_SETTING", key + " is too long");
                }
                changes.put(key, text == null || text.isEmpty() ? null : text);
            }
        }
        for (Map.Entry<String, String> c : changes.entrySet()) {
            SubscriptionSetting row = repository.findById(c.getKey())
                    .orElseGet(() -> SubscriptionSetting.builder().key(c.getKey()).build());
            row.setValue(c.getValue());
            row.setUpdatedBy(actor != null ? actor.userId() : null);
            repository.save(row);
        }
        if (!changes.isEmpty()) {
            audit.log(actor, "SETTINGS_UPDATED", "SETTINGS", "subscription", new LinkedHashMap<>(changes));
        }
        if (changes.containsKey("trialDays") || changes.containsKey("trialMaxShops")) {
            syncTrialPlan(actor);
        }
        return adminView();
    }

    // ── trial plan ⇄ settings sync ──────────────────────────────────────────
    // The settings (trialDays, trialMaxShops) are authoritative for the trial
    // term and shop cap; the FREE_TRIAL plan row carries the same numbers so
    // clients that read plan.shopLimit / durationValue see identical values.

    /** Settings → FREE_TRIAL plan row. */
    void syncTrialPlan(Actor actor) {
        Settings s = get();
        planRepository.findByCodeIgnoreCase(PlanCatalogService.FREE_TRIAL_CODE).ifPresent(p -> {
            boolean changed = !Integer.valueOf(s.trialMaxShops()).equals(p.getShopLimit())
                    || !Integer.valueOf(s.trialDays()).equals(p.getDurationValue())
                    || !"DAYS".equals(p.getDurationUnit());
            if (!changed) return;
            p.setShopLimit(s.trialMaxShops());
            p.setDurationValue(s.trialDays());
            p.setDurationUnit("DAYS");
            p.setVersion((p.getVersion() == null ? 1 : p.getVersion()) + 1);
            planRepository.save(p);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("shopLimit", s.trialMaxShops());
            d.put("durationDays", s.trialDays());
            audit.log(actor, "TRIAL_PLAN_SYNCED", "PLAN", p.getId().toString(), d);
        });
    }

    /** FREE_TRIAL plan row → settings (after an admin edits the trial plan). */
    void syncSettingsFromTrialPlan(SubscriptionPlanMaster p, Actor actor) {
        Map<String, String> changes = new LinkedHashMap<>();
        if (p.getShopLimit() != null) changes.put("trialMaxShops", Integer.toString(p.getShopLimit()));
        if ("DAYS".equals(p.getDurationUnit()) && p.getDurationValue() != null) {
            changes.put("trialDays", Integer.toString(p.getDurationValue()));
        }
        for (Map.Entry<String, String> c : changes.entrySet()) {
            SubscriptionSetting row = repository.findById(c.getKey())
                    .orElseGet(() -> SubscriptionSetting.builder().key(c.getKey()).build());
            if (c.getValue().equals(row.getValue())) continue;
            row.setValue(c.getValue());
            row.setUpdatedBy(actor != null ? actor.userId() : null);
            repository.save(row);
        }
    }

    // ── parsing ─────────────────────────────────────────────────────────────

    private static BigDecimal dec(Map<String, String> raw, String key, BigDecimal def) {
        try {
            String v = raw.get(key);
            return notBlank(v) ? new BigDecimal(v.trim()) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int integer(Map<String, String> raw, String key, int def) {
        try {
            String v = raw.get(key);
            return notBlank(v) ? Integer.parseInt(v.trim()) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean bool(Map<String, String> raw, String key, boolean def) {
        String v = raw.get(key);
        return notBlank(v) ? Boolean.parseBoolean(v.trim()) : def;
    }

    private static BigDecimal parseDecimal(String key, Object v) {
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_SETTING", key + " must be a number");
        }
    }

    private static int parseInt(String key, Object v) {
        try {
            return new BigDecimal(String.valueOf(v).trim()).intValueExact();
        } catch (Exception e) {
            throw ApiException.badRequest("INVALID_SETTING", key + " must be a whole number");
        }
    }

    private static boolean parseBool(String key, Object v) {
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim().toLowerCase();
        if ("true".equals(s) || "false".equals(s)) return Boolean.parseBoolean(s);
        throw ApiException.badRequest("INVALID_SETTING", key + " must be true or false");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s.trim() : null;
    }
}
