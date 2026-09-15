package com.repairshop.saas.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.auth.dto.CustomerLocationView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Reverse-geocodes coordinates via the Google Geocoding API using the JDK's
 * built-in {@link HttpClient} — no extra dependency, same pattern as
 * EmailService (Resend) and ImeiLookupController (IMEI.info). The API key
 * never leaves this service; callers only ever see the normalized
 * {@link CustomerLocationView}.
 */
@Service
@Slf4j
public class GeocodingService {

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public GeocodingService(@Value("${app.google.geocoding-api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * @return the resolved location, or {@code null} if the key isn't
     *         configured, Google returned no usable result, or the call
     *         failed for any reason. Never throws.
     */
    public CustomerLocationView reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        if (!isConfigured()) {
            log.warn("GeocodingService: GOOGLE_GEOCODING_API_KEY not set — reverse geocode unavailable.");
            return null;
        }
        try {
            String latlng = latitude.toPlainString() + "," + longitude.toPlainString();
            String url = "https://maps.googleapis.com/maps/api/geocode/json"
                    + "?latlng=" + URLEncoder.encode(latlng, StandardCharsets.UTF_8)
                    + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.error("GeocodingService: Google HTTP {} for ({}, {})", resp.statusCode(), latitude, longitude);
                return null;
            }
            JsonNode root = mapper.readTree(resp.body() == null ? "{}" : resp.body());
            String status = root.path("status").asText("");
            JsonNode results = root.path("results");
            if (!"OK".equals(status) || !results.isArray() || results.isEmpty()) {
                log.warn("GeocodingService: Google status '{}' for ({}, {})", status, latitude, longitude);
                return null;
            }
            JsonNode first = results.get(0);
            return CustomerLocationView.builder()
                    .latitude(latitude)
                    .longitude(longitude)
                    .placeId(textOrNull(first, "place_id"))
                    .formattedAddress(textOrNull(first, "formatted_address"))
                    .pincode(component(first, "postal_code"))
                    .area(firstNonBlank(
                            component(first, "sublocality_level_1"),
                            component(first, "sublocality"),
                            component(first, "locality")))
                    .city(component(first, "locality"))
                    .district(component(first, "administrative_area_level_2"))
                    .state(component(first, "administrative_area_level_1"))
                    .country(component(first, "country"))
                    .build();
        } catch (Exception e) {
            log.error("GeocodingService: reverse geocode failed for ({}, {}): {}", latitude, longitude, e.getMessage());
            return null;
        }
    }

    /**
     * First address_component whose types[] contains {@code type}, or null.
     * Google address components are not guaranteed to include every type —
     * a rural point may have no sublocality, an area may have no postal_code —
     * so this never assumes presence or a fixed array position.
     */
    private static String component(JsonNode result, String type) {
        JsonNode components = result.path("address_components");
        if (!components.isArray()) return null;
        for (JsonNode c : components) {
            JsonNode types = c.path("types");
            if (!types.isArray()) continue;
            for (JsonNode t : types) {
                if (type.equals(t.asText())) {
                    String name = textOrNull(c, "long_name");
                    return name != null ? name : textOrNull(c, "short_name");
                }
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v != null && !v.isNull()) ? v.asText(null) : null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
