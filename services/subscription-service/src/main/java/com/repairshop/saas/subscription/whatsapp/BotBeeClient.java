package com.repairshop.saas.subscription.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Minimal BotBee Nexa sender (same API as ticket-service): POST
 * /api/workspace/v1/whatsapp/templates/send, X-API-Key, flat params —
 * channelId (optional), to (digits, no +), templateName, languageCode,
 * body1..bodyN, mediaUrl + mediaType ("document" for the invoice PDF header).
 * Success: data.messages[0].id is the WAMID. The API key never leaves the header.
 */
@Component
public class BotBeeClient {

    static final String SEND_PATH = "/api/workspace/v1/whatsapp/templates/send";

    private final WhatsAppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public BotBeeClient(WhatsAppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public record SendResult(boolean ok, int httpStatus, String messageId, String error) {}

    public SendResult sendDocumentTemplate(String toE164, String template, List<String> bodyParams, String documentUrl) {
        HttpResponse<String> res;
        try {
            ObjectNode body = buildBody(toE164, template, props.getLanguage(), bodyParams, documentUrl);
            HttpRequest req = HttpRequest.newBuilder(URI.create(props.getBaseUrl().replaceAll("/+$", "") + SEND_PATH))
                    .timeout(Duration.ofSeconds(Math.max(5, props.getRequestTimeoutSeconds())))
                    .header("X-API-Key", props.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return new SendResult(false, 0, null, "WhatsApp provider unreachable: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SendResult(false, 0, null, "Interrupted while sending");
        } catch (IllegalArgumentException e) {
            return new SendResult(false, 0, null, "WhatsApp provider URL is invalid");
        }
        int status = res.statusCode();
        JsonNode node = parse(res.body());
        if (status >= 200 && status < 300 && !declined(node)) {
            return new SendResult(true, status, messageId(node), null);
        }
        return new SendResult(false, status, null, providerError(node, status));
    }

    ObjectNode buildBody(String toE164, String template, String language, List<String> bodyParams, String documentUrl) {
        ObjectNode root = mapper.createObjectNode();
        if (props.getChannelId() != null && !props.getChannelId().isBlank()) root.put("channelId", props.getChannelId());
        root.put("to", toE164.startsWith("+") ? toE164.substring(1) : toE164);
        root.put("templateName", template);
        root.put("languageCode", language);
        for (int i = 0; i < bodyParams.size(); i++) root.put("body" + (i + 1), bodyParams.get(i));
        if (documentUrl != null) {
            root.put("mediaUrl", documentUrl);
            root.put("mediaType", "document");
        }
        return root;
    }

    /** The WAMID: data.messages[0].id per the spec; a few flat spellings tolerated. */
    static String messageId(JsonNode n) {
        if (n == null) return null;
        for (JsonNode c : List.of(n.path("data"), n)) {
            JsonNode msgs = c.path("messages");
            if (msgs.isArray() && !msgs.isEmpty() && msgs.get(0).path("id").isTextual()) return msgs.get(0).path("id").asText();
            for (String f : List.of("messageId", "message_id", "wamid")) {
                if (c.path(f).isTextual() && !c.path(f).asText().isBlank()) return c.path(f).asText();
            }
        }
        return null;
    }

    private static boolean declined(JsonNode n) {
        return n != null && n.path("success").isBoolean() && !n.path("success").asBoolean();
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }

    private static String providerError(JsonNode n, int status) {
        String msg = null;
        if (n != null) {
            for (String f : List.of("error", "message")) {
                JsonNode v = n.path(f);
                if (v.isTextual() && !v.asText().isBlank()) { msg = v.asText(); break; }
            }
        }
        String text = "WhatsApp provider returned " + status + (msg == null ? "" : ": " + msg);
        return text.length() > 480 ? text.substring(0, 480) : text;
    }
}
