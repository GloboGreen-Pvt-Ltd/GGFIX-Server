package com.repairshop.saas.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.repairshop.saas.subscription.entity.PaymentWebhookEvent;
import com.repairshop.saas.subscription.entity.Subscription;
import com.repairshop.saas.subscription.entity.SubscriptionOrder;
import com.repairshop.saas.subscription.entity.SubscriptionPayment;
import com.repairshop.saas.subscription.entity.SubscriptionPlanMaster;
import com.repairshop.saas.subscription.entity.SubscriptionShop;
import com.repairshop.saas.subscription.razorpay.RazorpayClient;
import com.repairshop.saas.subscription.razorpay.RazorpayProperties;
import com.repairshop.saas.subscription.repository.PaymentWebhookEventRepository;
import com.repairshop.saas.subscription.repository.SubscriptionOrderRepository;
import com.repairshop.saas.subscription.repository.SubscriptionPaymentRepository;
import com.repairshop.saas.subscription.repository.SubscriptionPlanMasterRepository;
import com.repairshop.saas.subscription.repository.SubscriptionRepository;
import com.repairshop.saas.subscription.repository.SubscriptionShopRepository;
import com.repairshop.saas.subscription.service.OwnerDirectory.ShopInfo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real Subscription v2 services wired to Mockito repositories that behave like
 * a tiny in-memory database. Only the external edges (Razorpay, the JDBC owner
 * directory, sequences, audit) are pure mocks.
 */
class TestStore {

    static final Instant NOW = Instant.parse("2026-10-31T06:30:00Z");   // 12:00 IST

    final Map<UUID, SubscriptionOrder> orders = new LinkedHashMap<>();
    final Map<UUID, SubscriptionPayment> payments = new LinkedHashMap<>();
    final Map<UUID, Subscription> subs = new LinkedHashMap<>();
    final Map<UUID, SubscriptionShop> subShops = new LinkedHashMap<>();
    final Map<UUID, PaymentWebhookEvent> events = new LinkedHashMap<>();
    final Map<String, SubscriptionPlanMaster> plansByCode = new LinkedHashMap<>();

    final SubscriptionOrderRepository orderRepo = mock(SubscriptionOrderRepository.class);
    final SubscriptionPaymentRepository paymentRepo = mock(SubscriptionPaymentRepository.class);
    final SubscriptionRepository subRepo = mock(SubscriptionRepository.class);
    final SubscriptionShopRepository shopRepo = mock(SubscriptionShopRepository.class);
    final PaymentWebhookEventRepository eventRepo = mock(PaymentWebhookEventRepository.class);
    final SubscriptionPlanMasterRepository planRepo = mock(SubscriptionPlanMasterRepository.class);

    final ObjectMapper mapper = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final SettingsService settings = mock(SettingsService.class);
    final AuditService audit = mock(AuditService.class);
    final NumberSequence numbers = mock(NumberSequence.class);
    final OwnerDirectory directory = mock(OwnerDirectory.class);
    final RazorpayClient razorpay = mock(RazorpayClient.class);
    final RazorpayProperties props;
    final MySubscriptionService mineMock = mock(MySubscriptionService.class);
    /** Application events published by the services (e.g. PaymentActivatedEvent → invoice issuance). */
    final List<Object> publishedEvents = new ArrayList<>();

    final PlanCatalogService plans;
    final OrderViews views;
    final OrderLifecycleService lifecycle;
    final QuoteService quotes;
    final CheckoutService checkout;
    final WebhookService webhooks;
    final TrialPolicy trialPolicy;
    final TrialService trials;
    final MySubscriptionService mine;

    private final AtomicInteger seq = new AtomicInteger();

    TestStore() {
        this("rzp_test_key123", "TEST");
    }

    /** keyId + RAZORPAY_MODE, to exercise the TEST/LIVE isolation. */
    TestStore(String keyId, String mode) {
        props = new RazorpayProperties(keyId, "test_key_secret", "whsec_test", mode);
        when(settings.get()).thenReturn(SettingsService.Settings.defaults());
        when(numbers.nextOrderNo()).thenAnswer(i -> "NGF-SUB-TEST-" + seq.incrementAndGet());
        when(numbers.nextReceiptNo()).thenAnswer(i -> "NGF-RCPT-TEST-" + seq.incrementAndGet());
        wireRepositories();

        plans = new PlanCatalogService(planRepo, audit, mapper, settings);
        views = new OrderViews(mapper, paymentRepo);
        lifecycle = new OrderLifecycleService(orderRepo, paymentRepo, subRepo, shopRepo, plans, settings, numbers,
                audit, views, clock, props, publishedEvents::add);
        quotes = new QuoteService(plans, settings, directory, subRepo, clock);
        checkout = new CheckoutService(quotes, lifecycle, orderRepo, razorpay, props, settings, directory, views,
                mineMock, audit, mapper, clock);
        webhooks = new WebhookService(props, eventRepo, orderRepo, lifecycle, checkout, mapper, clock);
        trialPolicy = new TrialPolicy(subRepo, orderRepo, plans, settings, directory, clock);
        mine = new MySubscriptionService(subRepo, shopRepo, orderRepo, paymentRepo, plans, settings, directory,
                trialPolicy, views, props, clock);
        trials = new TrialService(trialPolicy, quotes, settings, lifecycle, mineMock, directory);
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    SubscriptionPlanMaster plan(String code, String type, long pricePaise, String pct, int duration, String unit,
                                Integer employees, Integer shopLimit, Integer sell) {
        SubscriptionPlanMaster p = SubscriptionPlanMaster.builder()
                .id(UUID.nameUUIDFromBytes(code.getBytes()))
                .code(code).name(type.equals("TRIAL") ? "Free Trial" : type.charAt(0) + type.substring(1).toLowerCase())
                .planType(type).pricePaise(pricePaise).currency("INR").durationValue(duration).durationUnit(unit)
                .employeeLimit(employees).shopLimit(shopLimit).sellOrderLimit(sell).buyOrderLimit(null)
                .serviceBookingEnabled(true).pickupEnabled(true).smsEnabled(false).whatsappEnabled(true)
                .reportsEnabled(false).exportEnabled(false).prioritySupportEnabled(false)
                .additionalShopDiscountPct(new BigDecimal(pct)).status("ACTIVE").displayOrder(10).version(1)
                .build();
        plansByCode.put(code, p);
        return p;
    }

    SubscriptionPlanMaster basic6m() {
        return plan("BASIC_6M", "BASIC", 100, "5", 6, "MONTHS", 4, null, null);
    }

    SubscriptionPlanMaster freeTrial() {
        return plan("FREE_TRIAL", "TRIAL", 0, "0", 15, "DAYS", 3, 2, 5);
    }

    List<ShopInfo> ownShops(UUID owner, int count) {
        List<ShopInfo> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new ShopInfo(UUID.randomUUID(), owner, "Shop " + (i + 1), "shop-" + i, "Chennai", true,
                    "98400000" + (10 + i)));
        }
        when(directory.shopsOf(owner)).thenReturn(list);
        return list;
    }

    Subscription putSubscription(Subscription s) {
        if (s.getId() == null) s.setId(UUID.randomUUID());
        if (s.getCreatedAt() == null) s.setCreatedAt(NOW.minusSeconds(86_400));
        subs.put(s.getId(), s);
        return s;
    }

    List<SubscriptionPayment> paymentsFor(UUID orderId) {
        return payments.values().stream().filter(p -> p.getOrderId().equals(orderId)).toList();
    }

    // ── repository behaviour ────────────────────────────────────────────────

    private void wireRepositories() {
        when(planRepo.findByCodeIgnoreCase(anyString()))
                .thenAnswer(i -> Optional.ofNullable(plansByCode.get(((String) i.getArgument(0)).toUpperCase())));

        when(orderRepo.save(any(SubscriptionOrder.class))).thenAnswer(i -> putOrder(i.getArgument(0)));
        when(orderRepo.saveAndFlush(any(SubscriptionOrder.class))).thenAnswer(i -> putOrder(i.getArgument(0)));
        when(orderRepo.lockById(any())).thenAnswer(i -> Optional.ofNullable(orders.get((UUID) i.getArgument(0))));
        when(orderRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(orders.get((UUID) i.getArgument(0))));
        when(orderRepo.findByRazorpayOrderId(anyString())).thenAnswer(i -> orders.values().stream()
                .filter(o -> i.getArgument(0).equals(o.getRazorpayOrderId())).findFirst());
        when(orderRepo.findByOwnerUserIdAndStatusInOrderByCreatedAtDesc(any(), any())).thenAnswer(i -> {
            UUID owner = i.getArgument(0);
            Collection<String> statuses = i.getArgument(1);
            return orders.values().stream()
                    .filter(o -> o.getOwnerUserId().equals(owner) && statuses.contains(o.getStatus())).toList();
        });
        when(orderRepo.existsByOwnerUserIdAndPurpose(any(), anyString())).thenAnswer(i -> orders.values().stream()
                .anyMatch(o -> o.getOwnerUserId().equals(i.getArgument(0)) && i.getArgument(1).equals(o.getPurpose())));

        when(paymentRepo.save(any(SubscriptionPayment.class))).thenAnswer(i -> {
            SubscriptionPayment p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            boolean dup = payments.values().stream().anyMatch(x -> x != p
                    && x.getRazorpayPaymentId().equals(p.getRazorpayPaymentId()));
            if (dup) throw new org.springframework.dao.DataIntegrityViolationException("uq_subscription_payments_rzp_payment");
            payments.put(p.getId(), p);
            return p;
        });
        when(paymentRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(payments.get((UUID) i.getArgument(0))));
        when(paymentRepo.findByRazorpayPaymentId(anyString())).thenAnswer(i -> payments.values().stream()
                .filter(p -> i.getArgument(0).equals(p.getRazorpayPaymentId())).findFirst());
        when(paymentRepo.findByOwnerUserIdOrderByPaidAtDesc(any())).thenAnswer(i -> payments.values().stream()
                .filter(p -> p.getOwnerUserId().equals(i.getArgument(0))).toList());

        when(subRepo.save(any(Subscription.class))).thenAnswer(i -> putSubscription(i.getArgument(0)));
        when(subRepo.lockByOwner(any())).thenAnswer(i -> subsOf(i.getArgument(0)));
        when(subRepo.findByOwnerUserIdOrderByCreatedAtDesc(any())).thenAnswer(i -> subsOf(i.getArgument(0)));

        when(shopRepo.findBySubscriptionId(any())).thenAnswer(i -> subShops.values().stream()
                .filter(s -> s.getSubscriptionId().equals(i.getArgument(0))).toList());
        when(shopRepo.deleteAllForSubscription(any())).thenAnswer(i -> {
            List<UUID> gone = subShops.values().stream()
                    .filter(s -> s.getSubscriptionId().equals(i.getArgument(0))).map(SubscriptionShop::getId).toList();
            gone.forEach(subShops::remove);
            return gone.size();
        });
        when(shopRepo.saveAll(anyIterable())).thenAnswer(i -> {
            List<SubscriptionShop> out = new ArrayList<>();
            for (Object o : (Iterable<?>) i.getArgument(0)) {
                SubscriptionShop s = (SubscriptionShop) o;
                if (s.getId() == null) s.setId(UUID.randomUUID());
                subShops.put(s.getId(), s);
                out.add(s);
            }
            return out;
        });

        when(eventRepo.findByEventId(anyString())).thenAnswer(i -> events.values().stream()
                .filter(e -> i.getArgument(0).equals(e.getEventId())).findFirst());
        when(eventRepo.save(any(PaymentWebhookEvent.class))).thenAnswer(i -> putEvent(i.getArgument(0)));
        when(eventRepo.saveAndFlush(any(PaymentWebhookEvent.class))).thenAnswer(i -> putEvent(i.getArgument(0)));
    }

    private List<Subscription> subsOf(UUID owner) {
        return subs.values().stream().filter(s -> owner.equals(s.getOwnerUserId()))
                .sorted(Comparator.comparing(Subscription::getCreatedAt).reversed()).toList();
    }

    private SubscriptionOrder putOrder(SubscriptionOrder o) {
        if (o.getId() == null) o.setId(UUID.randomUUID());
        if (o.getCreatedAt() == null) o.setCreatedAt(NOW);
        if (o.getPaymentAttempts() == null) o.setPaymentAttempts(0);
        orders.put(o.getId(), o);
        return o;
    }

    private PaymentWebhookEvent putEvent(PaymentWebhookEvent e) {
        if (e.getId() == null) e.setId(UUID.randomUUID());
        events.put(e.getId(), e);
        return e;
    }
}
