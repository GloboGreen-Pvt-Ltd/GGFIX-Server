package com.repairshop.saas.subscription.billing;

import com.repairshop.saas.subscription.dto.v2.V2Views.GstBreakdown;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * GST split of a GST-inclusive amount (invoice contract §2). BigDecimal in,
 * integer paise out, HALF_UP:
 *
 * <pre>
 * taxable = round(total × 100 / (100 + rate))      tax = total − taxable
 * intra-state (supplier code == place-of-supply code): cgst = round(tax / 2), sgst = tax − cgst
 * inter-state, or place of supply unknown:             igst = tax
 * not applicable (no supplier GSTIN / rate 0):         taxable = total, every tax 0
 * </pre>
 * The amount charged never changes; tax is only split out of it.
 */
public final class GstCalculator {

    public static final String TAX_INVOICE = "TAX_INVOICE";
    public static final String PAYMENT_RECEIPT = "PAYMENT_RECEIPT";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal ZERO_PCT = BigDecimal.ZERO.setScale(2);

    private GstCalculator() {}

    /** Where the service is supplied: a 2-digit GST state code and its "Tamil Nadu (33)" label. */
    public record PlaceOfSupply(String code, String label) {}

    /**
     * Place of supply of the billing shop: its GSTIN prefix when it has a valid
     * GSTIN, else its state name mapped through the official table; null when
     * neither is known (never guessed from a mobile number).
     */
    public static PlaceOfSupply placeOfSupply(String shopGstin, String shopState) {
        String code = GstStateCodes.codeFromGstin(shopGstin);
        if (code == null) code = GstStateCodes.codeForName(shopState);
        return code == null ? null : new PlaceOfSupply(code, GstStateCodes.label(code));
    }

    /** GST applies only when the supplier GSTIN is configured AND the rate is above 0. */
    public static boolean applicable(String supplierGstin, BigDecimal ratePercent) {
        return supplierGstin != null && !supplierGstin.isBlank() && ratePercent != null && ratePercent.signum() > 0;
    }

    /**
     * @param totalPaise        amount charged (after discount, GST-inclusive)
     * @param grossPaise        list price before discount
     * @param discountPaise     discount given
     * @param ratePercent       configured GST rate (e.g. 18)
     * @param applicable        see {@link #applicable}
     * @param supplierStateCode supplier's 2-digit state code (null when unknown)
     * @param pos               place of supply (null when unknown → IGST)
     */
    public static GstBreakdown compute(long totalPaise, long grossPaise, long discountPaise, BigDecimal ratePercent,
                                       boolean applicable, String supplierStateCode, PlaceOfSupply pos) {
        String placeLabel = pos == null ? null : pos.label();
        String supplierCode = GstStateCodes.isKnownCode(supplierStateCode) ? supplierStateCode.trim() : null;
        if (!applicable || ratePercent == null || ratePercent.signum() <= 0) {
            return new GstBreakdown(false, PAYMENT_RECEIPT, ZERO_PCT, true, grossPaise, discountPaise, totalPaise,
                    false, ZERO_PCT, 0, ZERO_PCT, 0, ZERO_PCT, 0, 0, totalPaise, placeLabel, supplierCode);
        }
        BigDecimal rate = ratePercent.setScale(2, RoundingMode.HALF_UP);
        long taxable = BigDecimal.valueOf(totalPaise).multiply(HUNDRED)
                .divide(HUNDRED.add(ratePercent), 0, RoundingMode.HALF_UP).longValueExact();
        long tax = totalPaise - taxable;

        boolean intra = supplierCode != null && pos != null && supplierCode.equals(pos.code());
        if (intra) {
            long cgst = BigDecimal.valueOf(tax).divide(TWO, 0, RoundingMode.HALF_UP).longValueExact();
            long sgst = tax - cgst;
            BigDecimal half = ratePercent.divide(TWO, 2, RoundingMode.HALF_UP);
            return new GstBreakdown(true, TAX_INVOICE, rate, true, grossPaise, discountPaise, taxable, false,
                    half, cgst, half, sgst, ZERO_PCT, 0, tax, totalPaise, placeLabel, supplierCode);
        }
        return new GstBreakdown(true, TAX_INVOICE, rate, true, grossPaise, discountPaise, taxable, true,
                ZERO_PCT, 0, ZERO_PCT, 0, rate, tax, tax, totalPaise, placeLabel, supplierCode);
    }
}
