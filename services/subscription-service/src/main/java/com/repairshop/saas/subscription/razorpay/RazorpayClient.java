package com.repairshop.saas.subscription.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Minimal Razorpay REST client on java.net.http + Jackson (no SDK dependency).
 * Basic auth keyId:keySecret; amounts are integer paise.
 */
@Slf4j
@Component
public class RazorpayClient {

    private static final String BASE_URL = "https://api.razorpay.com/v1";
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    private final RazorpayProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public RazorpayClient(RazorpayProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    /** POST /v1/orders → Razorpay order id. */
    public String createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        return send("POST", "/orders", body).path("id").asText();
    }

    /** GET /v1/payments/{id}. */
    public RazorpayPayment fetchPayment(String paymentId) {
        return RazorpayPayment.from(send("GET", "/payments/" + id(paymentId), null));
    }

    /** POST /v1/payments/{id}/capture — only for status=authorized. */
    public RazorpayPayment capture(String paymentId, long amountPaise, String currency) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", currency);
        return RazorpayPayment.from(send("POST", "/payments/" + id(paymentId) + "/capture", body));
    }

    /** GET /v1/orders/{id}/payments — reconciliation. */
    public List<RazorpayPayment> fetchOrderPayments(String razorpayOrderId) {
        JsonNode res = send("GET", "/orders/" + id(razorpayOrderId) + "/payments", null);
        List<RazorpayPayment> out = new ArrayList<>();
        for (JsonNode item : res.path("items")) out.add(RazorpayPayment.from(item));
        return out;
    }

    /** POST /v1/payments/{id}/refund → refund id. Null amount = full refund. */
    public String refund(String paymentId, Long amountPaise, Map<String, String> notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (amountPaise != null) body.put("amount", amountPaise);
        if (notes != null && !notes.isEmpty()) body.put("notes", notes);
        return send("POST", "/payments/" + id(paymentId) + "/refund", body).path("id").asText();
    }

    private JsonNode send(String method, String path, Object body) {
        if (!props.isConfigured()) throw new RazorpayException(0, "Razorpay keys are not configured");
        try {
            String auth = Base64.getEncoder().encodeToString(
                    (props.keyId() + ":" + props.keySecret()).getBytes(StandardCharsets.UTF_8));
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Basic " + auth)
                    .header("Accept", "application/json");
            if (body != null) {
                req.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else {
                req.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = res.body() == null || res.body().isBlank()
                    ? mapper.createObjectNode() : mapper.readTree(res.body());
            if (res.statusCode() >= 400) {
                String description = json.path("error").path("description").asText("HTTP " + res.statusCode());
                log.warn("Razorpay {} {} failed: {} {}", method, path, res.statusCode(), description);
                throw new RazorpayException(res.statusCode(), description);
            }
            return json;
        } catch (RazorpayException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RazorpayException("Razorpay request interrupted", e);
        } catch (Exception e) {
            log.warn("Razorpay {} {} failed: {}", method, path, e.toString());
            throw new RazorpayException("Could not reach Razorpay", e);
        }
    }

    private static String id(String value) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new RazorpayException(400, "Invalid Razorpay id");
        }
        return value;
    }
}
