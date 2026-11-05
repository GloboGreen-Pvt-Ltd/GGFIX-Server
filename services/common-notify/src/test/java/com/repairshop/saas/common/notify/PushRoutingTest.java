package com.repairshop.saas.common.notify;

import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PushRoutingTest {

    private static final String FCM_TOKEN =
            "dGVzdC1pbnN0YWxs:APA91bH" + "x".repeat(140);

    @Test
    void tokenProviderIsDetectedFromItsShape() {
        assertEquals(PushTokenRegistry.Provider.EXPO, PushTokenRegistry.providerOf("ExponentPushToken[abc123]"));
        assertEquals(PushTokenRegistry.Provider.EXPO, PushTokenRegistry.providerOf("ExpoPushToken[abc123]"));
        assertEquals(PushTokenRegistry.Provider.FCM, PushTokenRegistry.providerOf(FCM_TOKEN));
        assertNull(PushTokenRegistry.providerOf("short"));
        assertNull(PushTokenRegistry.providerOf("has spaces " + "x".repeat(120)));
        assertNull(PushTokenRegistry.providerOf(null));
        assertTrue(ShopPushService.isExpoToken("ExponentPushToken[abc123]"));
        assertFalse(ShopPushService.isExpoToken(FCM_TOKEN));
    }

    @Test
    void appMustMatchTheCallersRole() {
        assertTrue(PushApp.PARTNER.allows(List.of("SHOP_OWNER")));
        assertFalse(PushApp.PARTNER.allows(List.of("CUSTOMER")));
        assertTrue(PushApp.CUSTOMER.allows(List.of("CUSTOMER")));
        assertTrue(PushApp.EMPLOYEE.allows(List.of("TECHNICIAN")));
        assertTrue(PushApp.EMPLOYEE.allows(List.of("PICKUP_PERSON")));
        assertFalse(PushApp.EMPLOYEE.allows(List.of("CUSTOMER")));
        assertEquals(PushApp.PARTNER, PushApp.inferFrom(List.of("SHOP_OWNER")));
        assertEquals(PushApp.EMPLOYEE, PushApp.inferFrom(List.of("STAFF")));
        assertEquals(PushApp.CUSTOMER, PushApp.inferFrom(List.of("CUSTOMER")));
        assertNull(PushApp.inferFrom(List.of("SUPER_ADMIN")));
        assertEquals(PushApp.EMPLOYEE, PushApp.parse(" employee "));
        assertNull(PushApp.parse("admin"));
    }

    @Test
    void fcmDataIsFlatStringsWithoutKeysExpoWouldRemap() {
        Map<String, String> data = FcmSender.stringData(Map.of(
                "kind", "TICKET_STATUS", "ticketId", java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "body", "{\"x\":1}", "title", "t", "google.c.a", "x", "count", 3));
        assertEquals("TICKET_STATUS", data.get("kind"));
        assertEquals("00000000-0000-0000-0000-000000000001", data.get("ticketId"));
        assertEquals("3", data.get("count"));
        assertFalse(data.containsKey("body"));
        assertFalse(data.containsKey("title"));
        assertFalse(data.containsKey("google.c.a"));
    }

    @Test
    void fcmMessageBuildsWithChannelAndData() {
        Message m = FcmSender.toMessage(new FcmSender.FcmMessage(FCM_TOKEN, "New pickup request", "Ravi · iPhone 13",
                Map.of("kind", "PICKUP", "bookingId", "b1")));
        assertNotNull(m);
    }
}
