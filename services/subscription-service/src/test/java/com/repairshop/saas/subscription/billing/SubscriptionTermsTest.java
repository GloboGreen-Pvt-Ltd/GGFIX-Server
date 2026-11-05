package com.repairshop.saas.subscription.billing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Terms are calendar months in Asia/Kolkata, never "180 / 365 days". */
class SubscriptionTermsTest {

    private static Instant ist(int y, int m, int d, int h, int min) {
        return LocalDateTime.of(y, m, d, h, min).atZone(SubscriptionTerms.IST).toInstant();
    }

    @Test
    void sixMonthsFromMonthEndClampsToShorterMonth() {
        Instant start = ist(2026, 8, 31, 10, 0);
        assertEquals(ist(2027, 2, 28, 10, 0), SubscriptionTerms.termEnd(start, 6, "MONTHS"));
        assertNotEquals(start.plus(180, ChronoUnit.DAYS), SubscriptionTerms.termEnd(start, 6, "MONTHS"));
    }

    @Test
    void twelveMonthsIsACalendarYearAcrossLeapDay() {
        assertEquals(ist(2029, 2, 28, 9, 30), SubscriptionTerms.termEnd(ist(2028, 2, 29, 9, 30), 12, "MONTHS"));
        assertEquals(ist(2027, 10, 31, 12, 0), SubscriptionTerms.termEnd(ist(2026, 10, 31, 12, 0), 12, "MONTHS"));
    }

    @Test
    void monthArithmeticHappensInIstNotUtc() {
        // 2026-10-31T20:00Z is already 1 Nov 01:30 in India → +6 months = 1 May 01:30 IST.
        Instant start = Instant.parse("2026-10-31T20:00:00Z");
        assertEquals(ist(2027, 5, 1, 1, 30), SubscriptionTerms.termEnd(start, 6, "MONTHS"));
    }

    @Test
    void daysAndLabels() {
        assertEquals(ist(2026, 11, 15, 10, 0), SubscriptionTerms.termEnd(ist(2026, 10, 31, 10, 0), 15, "DAYS"));
        assertEquals("6 Months", SubscriptionTerms.durationLabel(6, "MONTHS"));
        assertEquals("1 Month", SubscriptionTerms.durationLabel(1, "MONTHS"));
        assertEquals("15 Days", SubscriptionTerms.durationLabel(15, "DAYS"));
        assertEquals("₹1,234.50", SubscriptionTerms.rupees(123450));
        assertEquals("₹1,00,000.00", SubscriptionTerms.rupees(10_000_000));
    }
}
