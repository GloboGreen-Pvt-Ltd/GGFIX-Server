package com.repairshop.saas.subscription.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.repairshop.saas.subscription.billing.GstCalculator.PlaceOfSupply;
import com.repairshop.saas.subscription.dto.v2.V2Views.GstBreakdown;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Invoice contract §2: inclusive split, intra vs inter state, HALF_UP paise, not-applicable rule. */
class GstCalculatorTest {

    private static final BigDecimal RATE_18 = new BigDecimal("18");
    private static final PlaceOfSupply TAMIL_NADU = GstCalculator.placeOfSupply(null, "Tamil Nadu");
    private static final PlaceOfSupply KARNATAKA = GstCalculator.placeOfSupply(null, "Karnataka");

    @Test
    void inclusiveIntraStateSplitMatchesContractExample() {
        GstBreakdown g = GstCalculator.compute(120000, 120000, 0, RATE_18, true, "33", TAMIL_NADU);

        assertTrue(g.applicable());
        assertEquals("TAX_INVOICE", g.documentType());
        assertEquals(new BigDecimal("18.00"), g.ratePercent());
        assertTrue(g.inclusive());
        assertEquals(101695, g.taxablePaise());          // round(120000 × 100 / 118)
        assertEquals(18305, g.taxPaise());
        assertFalse(g.interState());
        assertEquals(new BigDecimal("9.00"), g.cgstPercent());
        assertEquals(new BigDecimal("9.00"), g.sgstPercent());
        assertEquals(9153, g.cgstPaise());               // round(18305 / 2), HALF_UP
        assertEquals(9152, g.sgstPaise());               // remainder
        assertEquals(0, g.igstPaise());
        assertEquals(0, g.igstPercent().signum());
        assertEquals(120000, g.totalPaise());
        assertEquals(g.totalPaise(), g.taxablePaise() + g.cgstPaise() + g.sgstPaise());
        assertEquals("Tamil Nadu (33)", g.placeOfSupply());
        assertEquals("33", g.supplierStateCode());
    }

    @Test
    void interStateUsesIgstOnly() {
        GstBreakdown g = GstCalculator.compute(120000, 120000, 0, RATE_18, true, "33", KARNATAKA);

        assertTrue(g.interState());
        assertEquals(18305, g.igstPaise());
        assertEquals(new BigDecimal("18.00"), g.igstPercent());
        assertEquals(0, g.cgstPaise());
        assertEquals(0, g.sgstPaise());
        assertEquals(0, g.cgstPercent().signum());
        assertEquals("Karnataka (29)", g.placeOfSupply());
    }

    @Test
    void unknownPlaceOfSupplyFallsBackToIgst() {
        GstBreakdown g = GstCalculator.compute(120000, 120000, 0, RATE_18, true, "33", null);
        assertTrue(g.interState());
        assertEquals(g.taxPaise(), g.igstPaise());
        assertNull(g.placeOfSupply());

        // Unknown supplier state behaves the same way.
        GstBreakdown noSupplier = GstCalculator.compute(120000, 120000, 0, RATE_18, true, null, TAMIL_NADU);
        assertTrue(noSupplier.interState());
        assertEquals(18305, noSupplier.igstPaise());
    }

    @Test
    void roundingIsHalfUpToThePaisa() {
        // ₹1.00 at 18%: taxable = round(84.745…) = 85, tax 15 → CGST round(7.5) = 8, SGST 7.
        GstBreakdown one = GstCalculator.compute(100, 100, 0, RATE_18, true, "33", TAMIL_NADU);
        assertEquals(85, one.taxablePaise());
        assertEquals(15, one.taxPaise());
        assertEquals(8, one.cgstPaise());
        assertEquals(7, one.sgstPaise());

        // 500.01: taxable = round(42373.728…) = 42374, tax 7627 (odd paise) → CGST 3814 + SGST 3813.
        GstBreakdown odd = GstCalculator.compute(50001, 50001, 0, RATE_18, true, "33", TAMIL_NADU);
        assertEquals(42374, odd.taxablePaise());
        assertEquals(7627, odd.taxPaise());
        assertEquals(3814, odd.cgstPaise());
        assertEquals(3813, odd.sgstPaise());
        assertEquals(50001, odd.taxablePaise() + odd.cgstPaise() + odd.sgstPaise());

        // 999.99: taxable = round(84744.915…) = 84745 (rounds up), tax 15254 → 7627 + 7627.
        GstBreakdown up = GstCalculator.compute(99999, 99999, 0, RATE_18, true, "33", TAMIL_NADU);
        assertEquals(84745, up.taxablePaise());
        assertEquals(7627, up.cgstPaise());
        assertEquals(7627, up.sgstPaise());
    }

    @Test
    void oddPaiseNeverLoseOrInventMoney() {
        for (long total : List.of(1L, 3L, 7L, 101L, 11801L, 123457L, 999999L)) {
            for (String rate : List.of("5", "12", "18", "28")) {
                GstBreakdown intra = GstCalculator.compute(total, total, 0, new BigDecimal(rate), true, "33", TAMIL_NADU);
                assertEquals(total, intra.taxablePaise() + intra.cgstPaise() + intra.sgstPaise(), total + "@" + rate);
                assertTrue(Math.abs(intra.cgstPaise() - intra.sgstPaise()) <= 1);
                GstBreakdown inter = GstCalculator.compute(total, total, 0, new BigDecimal(rate), true, "33", KARNATAKA);
                assertEquals(total, inter.taxablePaise() + inter.igstPaise());
            }
        }
    }

    @Test
    void notApplicableMeansReceiptWithNoTax() {
        GstBreakdown g = GstCalculator.compute(190000, 200000, 10000, RATE_18, false, "33", TAMIL_NADU);

        assertFalse(g.applicable());
        assertEquals("PAYMENT_RECEIPT", g.documentType());
        assertEquals(0, g.ratePercent().signum());
        assertEquals(200000, g.grossPaise());
        assertEquals(10000, g.discountPaise());
        assertEquals(190000, g.taxablePaise());
        assertEquals(0, g.taxPaise());
        assertEquals(0, g.cgstPaise() + g.sgstPaise() + g.igstPaise());
        assertFalse(g.interState());
        assertEquals(190000, g.totalPaise());
    }

    @Test
    void applicableOnlyWithGstinAndPositiveRate() {
        assertFalse(GstCalculator.applicable(null, RATE_18));
        assertFalse(GstCalculator.applicable("  ", RATE_18));
        assertFalse(GstCalculator.applicable("33AABCU9603R1ZM", BigDecimal.ZERO));
        assertFalse(GstCalculator.applicable("33AABCU9603R1ZM", null));
        assertTrue(GstCalculator.applicable("33AABCU9603R1ZM", RATE_18));
        // A zero rate is "not applicable" even when the caller says otherwise.
        assertFalse(GstCalculator.compute(1000, 1000, 0, BigDecimal.ZERO, true, "33", TAMIL_NADU).applicable());
    }

    @Test
    void placeOfSupplyFromShopGstinThenStateName() {
        assertEquals("33", GstCalculator.placeOfSupply("33aabcu9603r1zm", "Karnataka").code());   // GSTIN wins
        assertEquals("29", GstCalculator.placeOfSupply("NOT-A-GSTIN", "Karnataka").code());       // invalid → state
        assertEquals("21", GstCalculator.placeOfSupply(null, "Orissa").code());
        assertEquals("07", GstCalculator.placeOfSupply(null, "New Delhi").code());
        assertEquals("01", GstCalculator.placeOfSupply(null, "Jammu & Kashmir").code());
        assertEquals("37", GstCalculator.placeOfSupply(null, "ANDHRA PRADESH").code());
        assertEquals("Other Territory (97)", GstCalculator.placeOfSupply(null, "Other Territory").label());
        assertNull(GstCalculator.placeOfSupply(null, null));
        assertNull(GstCalculator.placeOfSupply("", "Atlantis"));
    }

    @Test
    void stateCodeTable() {
        assertEquals("Tamil Nadu", GstStateCodes.name("33"));
        assertEquals("Other Territory", GstStateCodes.name("97"));
        assertEquals("Ladakh", GstStateCodes.name("38"));
        assertEquals("Telangana", GstStateCodes.name("36"));
        assertNull(GstStateCodes.name("40"));
        assertNull(GstStateCodes.validGstin("40AABCU9603R1ZM"));   // unknown state code
        assertEquals("27AABCU9603R1ZX", GstStateCodes.validGstin(" 27aabcu9603r1zx "));
    }

    @Test
    void jsonFieldNamesFollowTheContract() throws Exception {
        GstBreakdown g = GstCalculator.compute(120000, 120000, 0, RATE_18, true, "33", TAMIL_NADU);
        JsonNode json = JsonMapper.builder().findAndAddModules().build().valueToTree(g);
        for (String f : List.of("applicable", "documentType", "ratePercent", "inclusive", "grossPaise", "discountPaise",
                "taxablePaise", "interState", "cgstPercent", "cgstPaise", "sgstPercent", "sgstPaise", "igstPercent",
                "igstPaise", "taxPaise", "totalPaise", "placeOfSupply", "supplierStateCode")) {
            assertTrue(json.has(f), f);
        }
        assertEquals(18, json.size());
    }
}
