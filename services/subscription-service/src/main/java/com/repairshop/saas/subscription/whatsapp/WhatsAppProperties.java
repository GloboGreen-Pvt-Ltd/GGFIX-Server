package com.repairshop.saas.subscription.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * BotBee (WhatsApp) settings — environment only (app.whatsapp.* ←
 * BOTBEE_WHATSAPP_ENABLED / BOTBEE_API_KEY / BOTBEE_BASE_URL / BOTBEE_CHANNEL_ID).
 * The API key is never logged or returned; {@link #toString()} omits it.
 */
@Component
public class WhatsAppProperties {

    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String channelId;
    private final String language;
    private final int requestTimeoutSeconds;

    public WhatsAppProperties(@Value("${app.whatsapp.enabled:false}") boolean enabled,
                              @Value("${app.whatsapp.base-url:https://nexa.botbee.io}") String baseUrl,
                              @Value("${app.whatsapp.api-key:}") String apiKey,
                              @Value("${app.whatsapp.channel-id:}") String channelId,
                              @Value("${app.whatsapp.language:en}") String language,
                              @Value("${app.whatsapp.request-timeout-seconds:15}") int requestTimeoutSeconds) {
        this.enabled = enabled;
        this.baseUrl = blank(baseUrl) ? "https://nexa.botbee.io" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.channelId = channelId == null ? "" : channelId.trim();
        this.language = blank(language) ? "en" : language.trim();
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    /** BOTBEE_WHATSAPP_ENABLED=true AND an API key is present. */
    public boolean isConfigured() {
        return enabled && !apiKey.isEmpty();
    }

    public boolean isEnabled() { return enabled; }
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
    public String getChannelId() { return channelId; }
    public String getLanguage() { return language; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public String toString() {
        return "WhatsAppProperties[enabled=" + enabled + ", baseUrl=" + baseUrl + ", apiKey="
                + (apiKey.isEmpty() ? "<none>" : "<set>") + ", channelId=" + (channelId.isEmpty() ? "<first>" : "<set>") + "]";
    }
}
