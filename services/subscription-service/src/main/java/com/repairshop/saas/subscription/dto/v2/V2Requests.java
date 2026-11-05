package com.repairshop.saas.subscription.dto.v2;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Request bodies of the Subscription v2 contract (field names are binding). */
public final class V2Requests {

    private V2Requests() {}

    public record QuoteRequest(String planCode, List<UUID> shopIds) {}

    public record PreviewRequest(String planCode, Integer shopCount) {}

    public record PurchaseRequest(String planCode, List<UUID> shopIds, String channel, Boolean acceptTerms) {}

    public record PaymentOrderRequest(UUID orderId) {}

    public record VerifyRequest(UUID orderId, String razorpayOrderId, String razorpayPaymentId,
                                String razorpaySignature) {}

    public record CancelRequest(UUID orderId, String reason) {}

    /** {@code channel} is optional (not in the contract); defaults to WEB. */
    public record TrialRequest(List<UUID> shopIds, String channel) {}

    /** Admin create/update body — the Plan JSON; derived fields are ignored. */
    public record PlanRequest(
            String code, String name, String planType, String description,
            Long pricePaise, String currency, Integer durationValue, String durationUnit,
            Integer employeeLimit, Integer shopLimit, Integer sellOrderLimit, Integer buyOrderLimit,
            Boolean serviceBookingEnabled, Boolean pickupEnabled, Boolean smsEnabled, Boolean whatsappEnabled,
            Boolean reportsEnabled, Boolean exportEnabled, Boolean prioritySupportEnabled,
            BigDecimal additionalShopDiscountPct, String status, Integer displayOrder, String color, String icon) {}

    public record PlanStatusRequest(String status) {}

    public record PricingItem(UUID planId, Long pricePaise, BigDecimal additionalShopDiscountPct) {}

    public record PricingRequest(List<PricingItem> items) {}

    public record ExtendRequest(Integer days, Integer months, String reason) {}

    public record ReasonRequest(String reason) {}

    public record ManualActivateRequest(UUID ownerUserId, String planCode, List<UUID> shopIds, String reason) {}

    public record RefundRequest(Long amountPaise, String reason) {}
}
