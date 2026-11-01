package com.repairshop.saas.ticket.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** BotBee request/response handling against a local mock server (no real API calls). */
class BotBeeClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastKey = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "{\"success\":true,\"data\":{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.HBgM123\"}]},\"message\":\"Template sent successfully\"}";
    private BotBeeClient client;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastPath.set(ex.getRequestURI().getPath());
            lastKey.set(ex.getRequestHeaders().getFirst("X-API-Key"));
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        });
        server.start();
        WhatsAppProperties p = new WhatsAppProperties();
        p.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        p.setApiKey("test-key");
        p.setChannelId("chan-1");
        client = new BotBeeClient(p, mapper);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static final List<String> VARS = List.of("Priya", "Sri Mobile Care", "GTS-014", "Galaxy S23", "Display", "6,000",
            "02 Nov 2026, 06:00 PM", "+91 81241 23452");

    @Test
    void sendsTemplateWithApiKeyImageHeaderAndEightParams() throws Exception {
        BotBeeClient.SendResult r = client.sendTemplate("+919876543210", "neargfix_booking_confirmed", "en", VARS, "https://img/x.png");

        assertTrue(r.ok());
        assertEquals("wamid.HBgM123", r.messageId());
        assertEquals("/api/workspace/v1/whatsapp/templates/send", lastPath.get());
        assertEquals("test-key", lastKey.get());
        // Flat params per BotBee's OpenAPI: no component arrays, bodyN (not the comma-split "variables").
        JsonNode body = mapper.readTree(lastBody.get());
        assertEquals("chan-1", body.path("channelId").asText());
        assertEquals("919876543210", body.path("to").asText());
        assertEquals("neargfix_booking_confirmed", body.path("templateName").asText());
        assertEquals("en", body.path("languageCode").asText());
        assertEquals("https://img/x.png", body.path("mediaUrl").asText());
        assertEquals("image", body.path("mediaType").asText());
        for (int i = 0; i < 8; i++) assertEquals(VARS.get(i), body.path("body" + (i + 1)).asText());
        assertFalse(body.has("body9"));
        assertFalse(body.has("variables"));
        assertTrue(body.path("body6").asText().contains(","), "commas inside a value stay intact");
    }

    @Test
    void readsDeliveryStatusByWamid() throws Exception {
        reply = "{\"success\":true,\"data\":{\"wamid\":\"wamid.1\",\"status\":\"delivered\",\"direction\":\"outbound\"}}";
        BotBeeClient.StatusResult r = client.fetchStatus("wamid.1");
        assertEquals("delivered", r.status());
        assertNull(r.error());
        assertEquals("/api/workspace/v1/whatsapp/message/status", lastPath.get());
        assertEquals("wamid.1", mapper.readTree(lastBody.get()).path("wamid").asText());

        status = 404;
        reply = "{\"success\":false,\"error\":\"Message not found\"}";
        BotBeeClient.StatusResult missing = client.fetchStatus("wamid.x");
        assertNull(missing.status());
        assertTrue(missing.error().contains("Message not found"));
    }

    @Test
    void serverErrorAndRateLimitAreRetryable() {
        status = 503;
        reply = "{\"message\":\"busy\"}";
        BotBeeClient.SendResult r = client.sendTemplate("+919876543210", "t", "en", VARS, "https://img/x.png");
        assertFalse(r.ok());
        assertTrue(r.retryable());
        assertTrue(r.error().contains("503"));

        status = 429;
        assertTrue(client.sendTemplate("+919876543210", "t", "en", VARS, null).retryable());
    }

    @Test
    void planOrKeyRejectionIsFinalWithProviderMessage() {
        status = 403;
        reply = "{\"success\":false,\"error\":\"API access not included in your plan\"}";
        BotBeeClient.SendResult r = client.sendTemplate("+919876543210", "t", "en", VARS, null);
        assertFalse(r.ok());
        assertFalse(r.retryable());
        assertTrue(r.error().contains("API access not included in your plan"));
    }

    @Test
    void declinedWith200IsAFailure() {
        reply = "{\"success\":false,\"message\":\"Template not approved\"}";
        BotBeeClient.SendResult r = client.sendTemplate("+919876543210", "t", "en", VARS, null);
        assertFalse(r.ok());
        assertTrue(r.error().contains("Template not approved"));
    }

    @Test
    void unreachableProviderIsRetryable() {
        server.stop(0);
        BotBeeClient.SendResult r = client.sendTemplate("+919876543210", "t", "en", VARS, null);
        assertFalse(r.ok());
        assertTrue(r.retryable());
    }

    @Test
    void findsMessageIdInCommonShapes() throws Exception {
        assertEquals("a", BotBeeClient.messageId(mapper.readTree("{\"messageId\":\"a\"}")));
        assertEquals("b", BotBeeClient.messageId(mapper.readTree("{\"data\":{\"message_id\":\"b\"}}")));
        assertEquals("c", BotBeeClient.messageId(mapper.readTree("{\"data\":{\"messages\":[{\"id\":\"c\"}]}}")));
        assertNull(BotBeeClient.messageId(mapper.readTree("{\"ok\":true}")));
    }
}
