package com.repairshop.saas.subscription.invoice;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Invoice number format and the April–March financial year in Asia/Kolkata. */
class InvoiceNumbersTest {

    @Test
    void financialYearTurnsAtMidnightIstOnFirstApril() {
        // 31 Mar 2027 23:59:59 IST
        assertEquals("26-27", InvoiceNumbers.financialYear(Instant.parse("2027-03-31T18:29:59Z")));
        // 1 Apr 2027 00:00:00 IST (still 31 Mar in UTC)
        assertEquals("27-28", InvoiceNumbers.financialYear(Instant.parse("2027-03-31T18:30:00Z")));
        // 1 Apr 2026 01:30 IST although UTC is 31 Mar
        assertEquals("26-27", InvoiceNumbers.financialYear(Instant.parse("2026-03-31T20:00:00Z")));
        assertEquals("25-26", InvoiceNumbers.financialYear(Instant.parse("2026-03-31T18:00:00Z")));
        assertEquals("26-27", InvoiceNumbers.financialYear(Instant.parse("2026-11-02T06:30:00Z")));
        assertEquals("99-00", InvoiceNumbers.financialYear(Instant.parse("2099-12-01T00:00:00Z")));
    }

    @Test
    void numberFormatStaysWithinSixteenCharacters() {
        assertEquals("NGF/26-27/00001", InvoiceNumbers.format("NGF", "26-27", 1, false));
        assertEquals("NGF/26-27/12345", InvoiceNumbers.format("NGF", "26-27", 12345, false));
        assertEquals(16, InvoiceNumbers.format("ABCD", "26-27", 99999, false).length());
        assertEquals("NGF/26-27/T0001", InvoiceNumbers.format("NGF", "26-27", 1, true));
        assertTrue(InvoiceNumbers.format("ABCD", "26-27", 9999, true).length() <= 16);
    }

    @Test
    void liveAndTestUseSeparateSeries() {
        assertEquals("NGF/26-27", InvoiceNumbers.series("NGF", "26-27", false));
        assertEquals("TEST:NGF/26-27", InvoiceNumbers.series("NGF", "26-27", true));
        assertNotEquals(InvoiceNumbers.series("NGF", "26-27", false), InvoiceNumbers.series("NGF", "27-28", false));
    }

    @Test
    void prefixIsSanitised() {
        assertEquals("NGF", InvoiceNumbers.prefix(null));
        assertEquals("NGF", InvoiceNumbers.prefix(" "));
        assertEquals("ABC", InvoiceNumbers.prefix("abc"));
        assertEquals("NGF", InvoiceNumbers.prefix("TOOLONG"));
        assertEquals("NGF", InvoiceNumbers.prefix("N/G"));
    }

    @Test
    void downloadFileName() {
        assertEquals("NGF-26-27-00001.pdf", InvoiceNumbers.fileName("NGF/26-27/00001"));
    }
}
