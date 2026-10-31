package com.repairshop.saas.common.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends to Expo push tokens through the Expo push service — the path for iOS
 * installs and any build that registers an Expo token instead of a native FCM
 * one. {@code EXPO_ACCESS_TOKEN} is optional: Expo accepts unauthenticated
 * sends unless "enhanced push security" is switched on for the project.
 */
@Component
public class ExpoSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoSender.class);

    private static final URI EXPO_SEND = URI.create("https://exp.host/--/api/v2/push/send");
    /** Expo's per-request message limit. */
    private static final int BATCH = 100;

    /** One outgoing push. */
    public record ExpoMessage(String token, String title, String body, Map<String, ?> data) {}

    private final String accessToken;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ExpoSender(@Value("${push.expo.access-token:${EXPO_ACCESS_TOKEN:}}") String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    /** Sends all messages; returns tokens Expo reports as DeviceNotRegistered. Never throws. */
    public List<String> send(List<ExpoMessage> messages) {
        List<String> dead = new ArrayList<>();
        if (messages == null) return dead;
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ExpoMessage msg : messages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("to", msg.token());
            m.put("title", msg.title() == null ? "" : msg.title());
            m.put("body", msg.body() == null ? "" : msg.body());
            m.put("data", msg.data() == null ? Map.of() : msg.data());
            m.put("sound", "default");
            m.put("channelId", "default");
            m.put("priority", "high");
            payload.add(m);
        }
        for (int i = 0; i < payload.size(); i += BATCH) {
            dead.addAll(post(payload.subList(i, Math.min(i + BATCH, payload.size()))));
        }
        return dead;
    }

    private List<String> post(List<Map<String, Object>> batch) {
        List<String> dead = new ArrayList<>();
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(EXPO_SEND)
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(batch)));
            if (!accessToken.isEmpty()) req.header("Authorization", "Bearer " + accessToken);
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                log.warn("Expo push HTTP {}: {}", res.statusCode(), clip(res.body(), 500));
                return dead;
            }
            // One ticket per message, in order. DeviceNotRegistered means the app
            // was uninstalled or its token rotated — that token will never work again.
            JsonNode tickets = json.readTree(res.body()).path("data");
            for (int i = 0; i < tickets.size() && i < batch.size(); i++) {
                JsonNode t = tickets.get(i);
                if (!"error".equals(t.path("status").asText())) continue;
                String error = t.path("details").path("error").asText("");
                if ("DeviceNotRegistered".equals(error)) {
                    dead.add(String.valueOf(batch.get(i).get("to")));
                } else {
                    log.warn("Expo push ticket error {}: {}", error, t.path("message").asText(""));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Expo push failed: {}", e.getMessage());
        }
        return dead;
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
