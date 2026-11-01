package com.repairshop.saas.ticket.whatsapp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** E.164 numbers, the 8 template variables, and the receipt image. */
class WhatsAppFormattingTest {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    static BookingSnapshot.Device device(String tracking, String name, String summary, String estimate, String paid,
                                         String balance, Instant delivery) {
        return new BookingSnapshot.Device(tracking, name, null, summary,
                List.of(new BookingSnapshot.PriceItem("Display Replacement", new BigDecimal("4500"), "3 Months"),
                        new BookingSnapshot.PriceItem("Battery", new BigDecimal("1500"), null)),
                "Charger, Back cover", new BigDecimal(estimate), paid == null ? null : new BigDecimal(paid),
                balance == null ? null : new BigDecimal(balance), delivery);
    }

    static BookingSnapshot booking(List<BookingSnapshot.Device> devices) {
        return new BookingSnapshot("GTS-014", "Sri Mobile Care", "+91 81241 23452", "12 Gandhi Road, Chennai",
                "Priya", "9876543210", Instant.parse("2026-11-01T05:30:00Z"), devices);
    }

    @ParameterizedTest
    @CsvSource({
            "9876543210, +919876543210",
            "98765 43210, +919876543210",
            "09876543210, +919876543210",
            "919876543210, +919876543210",
            "+91 98765-43210, +919876543210",
            "+14155550123, +14155550123"
    })
    void formatsE164(String raw, String expected) {
        assertEquals(expected, PhoneNumbers.toE164(raw, "91"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "12345", "0442345678", "+915876543210", "91987654321"})
    void rejectsNonMobileNumbers(String raw) {
        assertNull(PhoneNumbers.toE164(raw, "91"));
    }

    @Test
    void masksPhone() {
        assertEquals("+91******3210", PhoneNumbers.mask("+919876543210"));
        assertEquals("—", PhoneNumbers.mask(null));
    }

    @Test
    void mapsTheEightTemplateVariablesInOrder() {
        Instant delivery = Instant.parse("2026-11-02T12:30:00Z"); // 18:00 IST
        List<String> v = BookingTemplateVariables.of(booking(List.of(
                device("GTS-014", "Samsung Galaxy S23 (8 GB / 256 GB)", "Display Replacement, Battery", "6000", "1000", "5000", delivery))), IST);
        assertEquals(List.of(
                "Priya",
                "Sri Mobile Care",
                "GTS-014",
                "Samsung Galaxy S23 (8 GB / 256 GB)",
                "Display Replacement, Battery",
                "6,000",
                "02 Nov 2026, 06:00 PM",
                "+91 81241 23452"), v);
    }

    @Test
    void multiDeviceBookingIsOneMessageWithSummedCostAndLatestDelivery() {
        List<String> v = BookingTemplateVariables.of(booking(List.of(
                device("GTS-014-A", "iPhone 13", "Battery", "150000", null, null, Instant.parse("2026-11-02T12:30:00Z")),
                device("GTS-014-B", "Redmi Note 12", null, "2500", null, null, Instant.parse("2026-11-03T06:30:00Z")))), IST);
        assertEquals("iPhone 13, Redmi Note 12", v.get(3));
        assertEquals("Battery; Display Replacement, Battery", v.get(4));
        assertEquals("1,52,500", v.get(5));
        assertEquals("03 Nov 2026, 12:00 PM", v.get(6));
    }

    @Test
    void variablesNeverEmptyAndHaveNoNewlines() {
        BookingSnapshot b = new BookingSnapshot("GTS-1", null, null, null, "  Ravi\nKumar\t ", null, null,
                List.of(new BookingSnapshot.Device("GTS-1", null, null, null, List.of(), null, null, null, null, null)));
        List<String> v = BookingTemplateVariables.of(b, IST);
        assertEquals(8, v.size());
        assertEquals("Ravi Kumar", v.get(0));
        assertEquals("NearGFix Partner", v.get(1));
        assertEquals("0", v.get(5));
        assertEquals("Will be updated soon", v.get(6));
        assertEquals("Contact the shop", v.get(7));
        v.forEach(s -> assertFalse(s.isBlank() || s.contains("\n") || s.contains("\t"), s));
    }

    @Test
    void indianMoneyGrouping() {
        assertEquals("999", BookingTemplateVariables.money(new BigDecimal("999")));
        assertEquals("1,000", BookingTemplateVariables.money(new BigDecimal("1000")));
        assertEquals("12,34,567", BookingTemplateVariables.money(new BigDecimal("1234567")));
        assertEquals("1,499.50", BookingTemplateVariables.money(new BigDecimal("1499.5")));
    }

    @Test
    void balanceFallsBackToTotalMinusPaid() {
        BookingSnapshot b = booking(List.of(device("GTS-014", "X", "S", "6000", "1500", null, null)));
        assertEquals(0, new BigDecimal("4500").compareTo(b.balance()));
    }

    @Test
    void rendersReceiptPngThatGrowsWithDevices() throws Exception {
        BookingReceiptRenderer r = new BookingReceiptRenderer(url -> null);
        Instant d = Instant.parse("2026-11-02T12:30:00Z");
        byte[] one = r.renderPng(booking(List.of(device("GTS-014", "Samsung Galaxy S23", "Display", "6000", "1000", "5000", d))), IST);
        byte[] two = r.renderPng(booking(List.of(
                device("GTS-014-A", "Samsung Galaxy S23", "Display", "6000", "1000", "5000", d),
                device("GTS-014-B", "iPhone 13 Pro Max with a very long model name that must wrap onto more lines", "Battery", "2500", null, null, d))), IST);
        assertPng(one);
        assertPng(two);
        java.awt.image.BufferedImage a = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(one));
        java.awt.image.BufferedImage b = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(two));
        assertEquals(1080, a.getWidth());
        assertTrue(b.getHeight() > a.getHeight());
        assertTrue(one.length < 1_000_000, "receipt should stay small: " + one.length);
    }

    private static void assertPng(byte[] bytes) {
        assertTrue(bytes.length > 1000);
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals('P', bytes[1]);
        assertEquals('N', bytes[2]);
        assertEquals('G', bytes[3]);
    }
}
