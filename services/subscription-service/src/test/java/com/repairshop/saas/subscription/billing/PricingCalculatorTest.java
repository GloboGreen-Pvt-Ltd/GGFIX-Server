package com.repairshop.saas.subscription.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Contract "Pricing": Basic 1..5 shops = 100,195,290,385,480; Premium = 100,190,280,370,460 (paise). */
class PricingCalculatorTest {

    // The seeded plans (migration 106): price 100 paise; Basic 5 % / Premium 10 % off each extra shop.
    static Stream<Arguments> seededPlans() {
        long[] basic = {100, 195, 290, 385, 480};
        long[] premium = {100, 190, 280, 370, 460};
        Stream.Builder<Arguments> out = Stream.builder();
        for (String code : new String[]{"BASIC_6M", "BASIC_12M", "PREMIUM_6M", "PREMIUM_12M"}) {
            boolean isBasic = code.startsWith("BASIC");
            for (int shops = 1; shops <= 5; shops++) {
                out.add(Arguments.of(code, 100L, new BigDecimal(isBasic ? "5" : "10"), shops,
                        (isBasic ? basic : premium)[shops - 1]));
            }
        }
        return out.build();
    }

    @ParameterizedTest(name = "{0} × {3} shops = {4} paise")
    @MethodSource("seededPlans")
    void totalsMatchTheContractTable(String code, long pricePaise, BigDecimal pct, int shops, long expectedTotal) {
        PricingCalculator.Breakdown b = PricingCalculator.price(pricePaise, pct, shops, BigDecimal.ZERO);

        assertEquals(expectedTotal, b.totalPaise());
        assertEquals(pricePaise, b.firstShopPaise());
        assertEquals(shops - 1, b.additionalShopsCount());
        assertEquals(b.firstShopPaise() + b.additionalShopsTotalPaise(), b.totalPaise());
        assertEquals(pricePaise * shops, b.subtotalPaise());
        assertEquals(b.subtotalPaise() - b.discountPaise(), b.totalPaise());
        assertEquals(0L, b.taxPaise());
    }

    @Test
    void additionalUnitRoundsHalfUp() {
        // 999 × 95 / 100 = 949.05 → 949 ; 999 × 90 / 100 = 899.1 → 899 ; 5 × 90/100 = 4.5 → 5
        assertEquals(949, PricingCalculator.additionalUnitPaise(999, new BigDecimal("5")));
        assertEquals(899, PricingCalculator.additionalUnitPaise(999, new BigDecimal("10")));
        assertEquals(5, PricingCalculator.additionalUnitPaise(5, new BigDecimal("10")));
    }

    @Test
    void taxIsSplitOutOfAnInclusiveTotal() {
        // ₹118.00 at 18 % inclusive → ₹100.00 net + ₹18.00 tax; payable unchanged.
        PricingCalculator.Breakdown b = PricingCalculator.price(11800, BigDecimal.ZERO, 1, new BigDecimal("18"));
        assertEquals(11800, b.totalPaise());
        assertEquals(1800, b.taxPaise());

        // 480 paise at 18 %: net = round_half_up(480 × 100 / 118) = 407 → tax 73.
        assertEquals(73, PricingCalculator.inclusiveTaxPaise(480, new BigDecimal("18")));
        // 0 % → no tax.
        assertEquals(0, PricingCalculator.inclusiveTaxPaise(480, BigDecimal.ZERO));

        PricingCalculator.Breakdown withTax = PricingCalculator.price(100, new BigDecimal("5"), 5, new BigDecimal("18"));
        assertEquals(480, withTax.totalPaise());
        assertEquals(73, withTax.taxPaise());
    }

    @Test
    void rejectsZeroShops() {
        assertThrows(IllegalArgumentException.class,
                () -> PricingCalculator.price(100, BigDecimal.ZERO, 0, BigDecimal.ZERO));
    }
}
