package com.repairshop.saas.subscription.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Server-side price maths, BigDecimal in, integer paise out (contract
 * "Pricing"). The plan price covers the whole term for ONE shop; each further
 * shop costs the price less the plan's additional-shop discount.
 *
 * <pre>
 * additionalUnitPaise = round_half_up(pricePaise × (100 − pct) / 100)
 * totalPaise          = pricePaise + (shopCount − 1) × additionalUnitPaise
 * discountPaise       = (shopCount − 1) × (pricePaise − additionalUnitPaise)
 * subtotalPaise       = pricePaise × shopCount            (list price, before discount)
 * taxPaise            = totalPaise − round_half_up(totalPaise × 100 / (100 + rate))   (inclusive)
 * </pre>
 * The payable amount is always {@code totalPaise}; tax is only split out of it.
 */
public final class PricingCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PricingCalculator() {}

    public record Breakdown(
            long firstShopPaise,
            int additionalShopsCount,
            long additionalShopUnitPaise,
            long additionalShopsTotalPaise,
            BigDecimal additionalShopDiscountPct,
            long discountPaise,
            long subtotalPaise,
            BigDecimal taxPercent,
            long taxPaise,
            long totalPaise
    ) {}

    public static long additionalUnitPaise(long pricePaise, BigDecimal discountPct) {
        BigDecimal pct = discountPct == null ? BigDecimal.ZERO : discountPct;
        return BigDecimal.valueOf(pricePaise)
                .multiply(HUNDRED.subtract(pct))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** Tax contained in an inclusive amount; 0 when the rate is 0/null. */
    public static long inclusiveTaxPaise(long totalPaise, BigDecimal taxPercent) {
        if (taxPercent == null || taxPercent.signum() <= 0) return 0L;
        long net = BigDecimal.valueOf(totalPaise)
                .multiply(HUNDRED)
                .divide(HUNDRED.add(taxPercent), 0, RoundingMode.HALF_UP)
                .longValueExact();
        return totalPaise - net;
    }

    public static Breakdown price(long pricePaise, BigDecimal discountPct, int shopCount, BigDecimal taxPercent) {
        if (shopCount < 1) throw new IllegalArgumentException("shopCount must be at least 1");
        if (pricePaise < 0) throw new IllegalArgumentException("pricePaise must not be negative");
        BigDecimal pct = discountPct == null ? BigDecimal.ZERO : discountPct;
        BigDecimal rate = taxPercent == null ? BigDecimal.ZERO : taxPercent;

        long unit = additionalUnitPaise(pricePaise, pct);
        int extra = shopCount - 1;
        long additionalTotal = extra * unit;
        long total = pricePaise + additionalTotal;
        long discount = extra * (pricePaise - unit);
        long subtotal = pricePaise * shopCount;
        long tax = inclusiveTaxPaise(total, rate);

        return new Breakdown(pricePaise, extra, unit, additionalTotal, pct, discount, subtotal, rate, tax, total);
    }
}
