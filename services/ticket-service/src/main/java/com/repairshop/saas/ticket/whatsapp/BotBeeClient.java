package com.repairshop.saas.ticket.whatsapp;

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
 * BotBee Nexa Workspace API (OpenAPI "Developer API" 1.0.0, server https://nexa.botbee.io).
 * Auth: X-API-Key. Every response is the envelope { success, data?, message?, error? }.
 *
 * <ul>
 *   <li>POST /api/workspace/v1/whatsapp/templates/send — flat params, no component arrays:
 *       channelId (optional, first active channel when omitted), to (digits), templateName,
 *       languageCode, body1..body20, mediaUrl + mediaType for an image header.
 *       Success: data.messages[0].id is the WAMID.</li>
 *   <li>POST /api/workspace/v1/whatsapp/message/status — { wamid } → data.status
 *       (sent / delivered / read / failed).</li>
 * </ul>
 * Rate limits per API user: api_messaging 20/min, api_read 120/min (429 + Retry-After).
 */
@Component
public class BotBeeClient {

    static final String SEND_PATH = "/api/workspace/v1/whatsapp/templates/send";
    static final String STATUS_PATH = "/api/workspace/v1/whatsapp/message/status";

    private final WhatsAppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public BotBeeClient(WhatsAppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public record SendResult(boolean ok, boolean retryable, int httpStatus, String messageId, String body, String error) {}

    /** status: lower-case provider status ("delivered", "read", …) or null; error set when the lookup failed. */
    public record StatusResult(String status, String error) {}

    public SendResult sendTemplate(String toE164, String template, String language, List<String> bodyParams, String headerImageUrl) {
        HttpResponse<String> res;
        try {
            res = post(SEND_PATH, buildBody(toE164, template, language, bodyParams, headerImageUrl));
        } catch (IOException e) {
            return new SendResult(false, true, 0, null, null, "WhatsApp provider unreachable: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SendResult(false, true, 0, null, null, "Interrupted while sending.");
        }
        int status = res.statusCode();
        String body = res.body();
        JsonNode node = parse(body);
        if (status >= 200 && status < 300 && !declined(node)) {
            return new SendResult(true, false, status, messageId(node), body, null);
        }
        boolean retryable = status == 408 || status == 429 || status >= 500;
        return new SendResult(false, retryable, status, null, body, providerError(node, status));
    }

    public StatusResult fetchStatus(String wamid) {
        try {
            ObjectNode req = mapper.createObjectNode().put("wamid", wamid);
            HttpResponse<String> res = post(STATUS_PATH, req);
            JsonNode node = parse(res.body());
            if (res.statusCode() >= 200 && res.statusCode() < 300 && !declined(node) && node != null) {
                JsonNode s = node.path("data").path("status");
                return new StatusResult(s.isTextual() ? s.asText().toLowerCase() : null, null);
            }
            return new StatusResult(null, providerError(node, res.statusCode()));
        } catch (IOException e) {
            return new StatusResult(null, "WhatsApp provider unreachable: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new StatusResult(null, "Interrupted.");
        }
    }

    /** templates/send body: flat params; bodyN avoids the comma-splitting of "variables". */
    ObjectNode buildBody(String toE164, String template, String language, List<String> bodyParams, String headerImageUrl) {
        ObjectNode root = mapper.createObjectNode();
        if (props.getChannelId() != null && !props.getChannelId().isBlank()) root.put("channelId", props.getChannelId());
        root.put("to", toE164.startsWith("+") ? toE164.substring(1) : toE164);
        root.put("templateName", template);
        root.put("languageCode", language);
        for (int i = 0; i < bodyParams.size(); i++) root.put("body" + (i + 1), bodyParams.get(i));
        if (headerImageUrl != null) {
            root.put("mediaUrl", headerImageUrl);
            root.put("mediaType", "image");
        }
        return root;
    }

    private HttpResponse<String> post(String path, ObjectNode body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(props.getBaseUrl().replaceAll("/+$", "") + path))
                .timeout(Duration.ofSeconds(Math.max(5, props.getRequestTimeoutSeconds())))
                .header("X-API-Key", props.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
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

    /** "WhatsApp provider returned 403: API access not included in your plan". */
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
