package com.repairshop.saas.masterdata.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repairshop.saas.masterdata.entity.MasterBrand;
import com.repairshop.saas.masterdata.entity.MasterDeviceCategory;
import com.repairshop.saas.masterdata.entity.MasterModel;
import com.repairshop.saas.masterdata.repository.MasterBrandRepository;
import com.repairshop.saas.masterdata.repository.MasterDeviceCategoryRepository;
import com.repairshop.saas.masterdata.repository.MasterModelRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Server-side proxy + catalog resolver for IMEI.info device lookups.
 *
 * The shop app calls {@code GET /master/imei-lookup?imei=...} during the new
 * booking flow (after Customer Details). This endpoint:
 *   1. Calls the paid IMEI.info {@code /check/{service}} API server-side, so the
 *      API token never ships inside the app bundle.
 *   2. Normalises the provider response to {brand, model, modelNumbers[]}.
 *   3. Resolves that to a ggfix catalog device by matching
 *      master_models.model_number first (e.g. "CPH2735" -> OPPO A5 5G), then
 *      falling back to brand + fuzzy model-name match.
 *
 * Degrades gracefully: when the token/service-id isn't configured, the provider
 * is unreachable / out of balance, or nothing matches, it returns
 * {@code matched=false} and the app falls back to the manual device picker.
 */
@RestController
@RequestMapping("/master")
public class ImeiLookupController {

    private final MasterModelRepository modelRepo;
    private final MasterBrandRepository brandRepo;
    private final MasterDeviceCategoryRepository categoryRepo;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL) // IMEI.info APPEND_SLASH 301s
            .build();

    @Value("${app.imei.base-url:https://dash.imei.info/api}")
    private String baseUrl;
    @Value("${app.imei.token:}")
    private String token;
    // Service 0 = "Basic IMEI Check" (brand/model/model-numbers, token based).
    @Value("${app.imei.service-id:0}")
    private String serviceId;

    public ImeiLookupController(MasterModelRepository modelRepo,
                                MasterBrandRepository brandRepo,
                                MasterDeviceCategoryRepository categoryRepo) {
        this.modelRepo = modelRepo;
        this.brandRepo = brandRepo;
        this.categoryRepo = categoryRepo;
    }

    @GetMapping("/imei-lookup")
    public ResponseEntity<Map<String, Object>> lookup(@RequestParam("imei") String rawImei) {
        Map<String, Object> out = new LinkedHashMap<>();
        String imei = rawImei == null ? "" : rawImei.replaceAll("[^0-9]", "");
        out.put("imei", imei);
        out.put("matched", false);

        if (imei.length() < 14 || imei.length() > 17) {
            out.put("configured", isConfigured());
            out.put("error", "INVALID_IMEI");
            return ResponseEntity.ok(out);
        }
        if (!isConfigured()) {
            out.put("configured", false);
            out.put("error", "NOT_CONFIGURED");
            return ResponseEntity.ok(out);
        }
        out.put("configured", true);

        // ---- 1. Call IMEI.info ------------------------------------------------
        JsonNode root;
        try {
            // Trailing slash on /check/{service}/ — IMEI.info uses Django
            // APPEND_SLASH, so the slash-less form 301-redirects.
            String url = baseUrl.replaceAll("/+$", "")
                    + "/check/" + URLEncoder.encode(serviceId, StandardCharsets.UTF_8) + "/"
                    + "?API_KEY=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                    + "&imei=" + URLEncoder.encode(imei, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                out.put("error", "PROVIDER_HTTP_" + resp.statusCode());
                // Provider returns {"detail":"..."} for billing/validation errors
                // (e.g. "Request is too expensive." when the token/balance is short).
                try {
                    JsonNode err = mapper.readTree(resp.body() == null ? "{}" : resp.body());
                    if (err.hasNonNull("detail")) out.put("providerMessage", err.get("detail").asText());
                } catch (Exception ignore) { /* leave providerBody only */ }
                out.put("providerBody", truncate(resp.body(), 400));
                return ResponseEntity.ok(out);
            }
            root = mapper.readTree(resp.body() == null ? "{}" : resp.body());
        } catch (Exception e) {
            out.put("error", "PROVIDER_UNREACHABLE");
            return ResponseEntity.ok(out);
        }

        // ---- 2. Normalise the provider payload --------------------------------
        String brand = firstString(root, "brand", "brand_name", "brandName", "manufacturer", "maker");
        String model = firstString(root, "model", "model_name", "modelName", "device_name", "deviceName", "modelDescription");
        List<String> modelNumbers = collectModelNumbers(root);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("brand", brand);
        raw.put("model", model);
        raw.put("modelNumbers", modelNumbers);
        out.put("raw", raw);

        // ---- 3. Resolve to a ggfix catalog device -----------------------------
        MasterModel hit = resolveModel(brand, model, modelNumbers);
        if (hit == null) {
            return ResponseEntity.ok(out); // matched stays false
        }
        out.put("matched", true);
        out.put("device", devicePayload(hit));
        return ResponseEntity.ok(out);
    }

    // ── configuration ────────────────────────────────────────────────────────
    private boolean isConfigured() {
        return token != null && !token.isBlank() && serviceId != null && !serviceId.isBlank();
    }

    // ── catalog resolution ───────────────────────────────────────────────────
    private MasterModel resolveModel(String brand, String model, List<String> modelNumbers) {
        // (a) Strongest signal: exact manufacturer model-number match.
        for (String mn : modelNumbers) {
            if (mn == null || mn.isBlank()) continue;
            List<MasterModel> byNumber = modelRepo.findByModelNumberContainingIgnoreCase(mn.trim());
            if (!byNumber.isEmpty()) return byNumber.get(0);
        }
        // (b) Fall back to brand + fuzzy model-name match.
        MasterBrand mb = resolveBrand(brand);
        if (mb == null || model == null || model.isBlank()) return null;
        String needle = normalize(model);
        if (needle.isEmpty()) return null;
        MasterModel contains = null;
        for (MasterModel m : modelRepo.findByBrandIdOrderByName(mb.getId())) {
            String hay = normalize(m.getName());
            if (hay.isEmpty()) continue;
            if (hay.equals(needle)) return m;                  // exact (normalised)
            if (contains == null && (hay.contains(needle) || needle.contains(hay))) contains = m;
        }
        return contains;
    }

    private MasterBrand resolveBrand(String brand) {
        if (brand == null || brand.isBlank()) return null;
        String needle = normalize(brand);
        MasterBrand contains = null;
        for (MasterBrand b : brandRepo.findAll()) {
            String hay = normalize(b.getName());
            if (hay.isEmpty()) continue;
            if (hay.equals(needle)) return b;
            if (contains == null && (hay.contains(needle) || needle.contains(hay))) contains = b;
        }
        return contains;
    }

    private Map<String, Object> devicePayload(MasterModel m) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("modelId", m.getId());
        d.put("modelName", m.getName());
        d.put("modelNumber", m.getModelNumber());
        d.put("imageUrl", m.getImageUrl());
        d.put("imageBase64", m.getImageBase64());
        d.put("brandId", m.getBrandId());
        d.put("seriesId", m.getSeriesId());
        brandRepo.findById(m.getBrandId()).ifPresent(b -> d.put("brandName", b.getName()));
        if (m.getCategoryId() != null) {
            d.put("categoryId", m.getCategoryId());
            categoryRepo.findById(m.getCategoryId()).ifPresent(c -> {
                d.put("categoryCode", c.getCode());
                d.put("categoryName", c.getName());
            });
        }
        return d;
    }

    // ── JSON helpers (provider shape is not contractually fixed) ─────────────
    /** Depth-first search for the first textual value under any of {@code keys}. */
    private String firstString(JsonNode node, String... keys) {
        Set<String> want = new HashSet<>();
        for (String k : keys) want.add(k.toLowerCase(Locale.ROOT));
        Deque<JsonNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            JsonNode n = stack.pop();
            if (n == null) continue;
            if (n.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = n.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    JsonNode v = e.getValue();
                    if (want.contains(e.getKey().toLowerCase(Locale.ROOT)) && v != null
                            && v.isValueNode() && !v.asText().isBlank()) {
                        return v.asText().trim();
                    }
                    if (v != null && v.isContainerNode()) stack.push(v);
                }
            } else if (n.isArray()) {
                for (JsonNode c : n) stack.push(c);
            }
        }
        return null;
    }

    /** Collect model numbers from any *model*number* / models field, splitting
     *  comma / newline / slash separated strings and flattening arrays. */
    private List<String> collectModelNumbers(JsonNode node) {
        LinkedHashSet<String> acc = new LinkedHashSet<>();
        Deque<JsonNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            JsonNode n = stack.pop();
            if (n == null) continue;
            if (n.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = n.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String key = e.getKey().toLowerCase(Locale.ROOT);
                    JsonNode v = e.getValue();
                    boolean isNumberField = (key.contains("model") && key.contains("number"))
                            || key.equals("models") || key.equals("model_no") || key.equals("modelno");
                    if (isNumberField) addNumbers(acc, v);
                    if (v != null && v.isContainerNode()) stack.push(v);
                }
            } else if (n.isArray()) {
                for (JsonNode c : n) stack.push(c);
            }
        }
        return new ArrayList<>(acc);
    }

    private void addNumbers(Set<String> acc, JsonNode v) {
        if (v == null) return;
        if (v.isArray()) {
            for (JsonNode c : v) addNumbers(acc, c);
        } else if (v.isValueNode()) {
            for (String part : v.asText().split("[,;/\\n\\r]+")) {
                String s = part.trim();
                if (!s.isEmpty()) acc.add(s);
            }
        }
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
