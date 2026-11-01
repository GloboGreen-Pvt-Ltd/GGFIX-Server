package com.repairshop.saas.subscription.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** BotBee templates/send body: flat params, digits-only "to", document header. */
class BotBeeClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void documentTemplateBodyUsesFlatParams() {
        BotBeeClient client = new BotBeeClient(new WhatsAppProperties(true, "", "bb_key", "", "en", 15), mapper);
        ObjectNode body = client.buildBody("+919840012345", "neargfix_subscription_paid", "en",
                List.of("Ravi", "Shop", "Basic", "6 Months", "2", "1,900.00", "NGF/26-27/00001"),
                "https://api.ggfix.in/subscription/subscriptions/invoices/public/x.pdf?exp=1&sig=s");

        assertFalse(body.has("channelId"));
        assertEquals("919840012345", body.get("to").asText());
        assertEquals("neargfix_subscription_paid", body.get("templateName").asText());
        assertEquals("en", body.get("languageCode").asText());
        assertEquals("Ravi", body.get("body1").asText());
        assertEquals("NGF/26-27/00001", body.get("body7").asText());
        assertFalse(body.has("body8"));
        assertEquals("document", body.get("mediaType").asText());
        assertTrue(body.get("mediaUrl").asText().endsWith("sig=s"));
        assertFalse(body.toString().contains("bb_key"));
    }

    @Test
    void channelIdIsSentWhenConfigured() {
        BotBeeClient client = new BotBeeClient(new WhatsAppProperties(true, null, "k", "ch_1", null, 15), mapper);
        assertEquals("ch_1", client.buildBody("919840012345", "t", "en", List.of(), null).get("channelId").asText());
    }

    @Test
    void messageIdComesFromDataMessages() throws Exception {
        assertEquals("wamid.X", BotBeeClient.messageId(mapper.readTree(
                "{\"success\":true,\"data\":{\"messages\":[{\"id\":\"wamid.X\"}]}}")));
    }

    @Test
    void propertiesNeverPrintTheKey() {
        WhatsAppProperties p = new WhatsAppProperties(true, null, "secret-key", null, null, 15);
        assertTrue(p.isConfigured());
        assertEquals("https://nexa.botbee.io", p.getBaseUrl());
        assertFalse(p.toString().contains("secret-key"));
        assertFalse(new WhatsAppProperties(true, null, "", null, null, 15).isConfigured());
        assertFalse(new WhatsAppProperties(false, null, "k", null, null, 15).isConfigured());
    }

    @Test
    void phoneMasking() {
        assertEquals("+919840012345", PhoneNumbers.toE164("098400 12345", "91"));
        assertEquals("+91******2345", PhoneNumbers.mask("+919840012345"));
        assertNull(PhoneNumbers.toE164("12345", "91"));
    }
}
