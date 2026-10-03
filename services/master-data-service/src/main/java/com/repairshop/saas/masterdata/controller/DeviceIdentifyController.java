package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.masterdata.entity.MasterDeviceCategory;
import com.repairshop.saas.masterdata.vision.DeviceCatalogIndex;
import com.repairshop.saas.masterdata.vision.DeviceMatcher;
import com.repairshop.saas.masterdata.vision.GoogleVisionClient;
import com.repairshop.saas.masterdata.vision.VisionSignals;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * "What device is this?" for the Partner App's Visual Device Scanner — the
 * Google-Lens-style lookup.
 *
 * {@code POST /master/device-identify} (multipart {@code file}) sends the photo
 * to Google Cloud Vision (web detection + logo + text), then ranks GGFIX
 * catalogue models against what Google said (see {@link DeviceMatcher}).
 *
 * Requires a signed-in user (SecurityConfig): every call is a paid Google
 * request. Degrades like the IMEI lookup: no API key → {@code configured=false};
 * Google unreachable / refusing → {@code error=PROVIDER_*}; nothing matched →
 * empty {@code matches}. The app then falls back to reading the device's text.
 *
 * Response (match fields mirror ggfix-visual-search-service, so the app renders
 * both the same way):
 * <pre>
 * { configured, confidence: high|medium|low, labels: ["samsung galaxy s8+"],
 *   bestMatch: {...} | null,
 *   matches: [{ id, brandId, brand, model, modelNumbers, imageUrl, categoryId,
 *               categoryName, categoryCode, seriesId, similarity, matchedBy }] }
 * </pre>
 */
@RestController
@RequestMapping("/master")
public class DeviceIdentifyController {

    private final GoogleVisionClient vision;
    private final DeviceCatalogIndex catalog;

    public DeviceIdentifyController(GoogleVisionClient vision, DeviceCatalogIndex catalog) {
        this.vision = vision;
        this.catalog = catalog;
    }

    @PostMapping(value = "/device-identify", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> identify(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "limit", required = false, defaultValue = "8") int limit) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", vision.isConfigured());
        out.put("confidence", "low");
        out.put("labels", List.of());
        out.put("bestMatch", null);
        out.put("matches", List.of());

        if (!vision.isConfigured()) {
            out.put("error", "NOT_CONFIGURED");
            return ResponseEntity.ok(out);
        }
        if (file == null || file.isEmpty()) {
            out.put("error", "NO_IMAGE");
            return ResponseEntity.badRequest().body(out);
        }
        String type = file.getContentType();
        if (type != null && !type.isBlank() && !type.toLowerCase(Locale.ROOT).startsWith("image/")
                && !type.equalsIgnoreCase("application/octet-stream")) {
            out.put("error", "NOT_AN_IMAGE");
            return ResponseEntity.badRequest().body(out);
        }

        VisionSignals signals;
        try {
            signals = vision.annotate(file.getBytes());
        } catch (GoogleVisionClient.VisionException e) {
            out.put("error", e.code());
            out.put("providerMessage", e.getMessage());
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            out.put("error", "READ_FAILED");
            return ResponseEntity.ok(out);
        }

        DeviceCatalogIndex.Snapshot snap = catalog.get();
        int cap = Math.max(1, Math.min(limit, 20));
        DeviceMatcher.Result result = DeviceMatcher.match(snap.models(), snap.brandNames(), signals, cap);

        List<Map<String, Object>> matches = result.matches().stream()
                .map(m -> payload(m, snap, result.confidence()))
                .toList();
        out.put("confidence", result.confidence());
        out.put("labels", signals.bestGuessLabels());
        out.put("recognisedAs", result.recognisedAs());
        out.put("brand", result.brandId() == null ? null : snap.brandNames().get(result.brandId()));
        out.put("matches", matches);
        out.put("bestMatch", !matches.isEmpty() && result.matches().get(0).score() > 0 ? matches.get(0) : null);
        return ResponseEntity.ok(out);
    }

    private static Map<String, Object> payload(DeviceMatcher.Match m, DeviceCatalogIndex.Snapshot snap, String confidence) {
        DeviceMatcher.CatalogModel c = m.model();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", c.id());
        d.put("brandId", c.brandId());
        String brand = snap.brandNames().get(c.brandId());
        d.put("brand", brand);
        d.put("model", c.name());
        d.put("displayName", displayName(brand, c.name()));
        d.put("modelNumbers", c.modelNumbers());
        d.put("imageUrl", c.imageUrl());
        d.put("seriesId", c.seriesId());
        d.put("categoryId", c.categoryId());
        MasterDeviceCategory cat = c.categoryId() == null ? null : snap.categories().get(c.categoryId());
        d.put("categoryName", cat == null ? null : cat.getName());
        d.put("categoryCode", cat == null ? null : cat.getCode());
        // 0–1 for display; brand-only fallbacks carry no similarity.
        d.put("similarity", m.score() > 0 ? Math.min(1.0, m.score()) : null);
        d.put("confidence", confidence);
        d.put("matchedBy", m.matchedBy());
        return d;
    }

    /**
     * Brand + model without doubling the brand: the catalogue has both
     * ("Apple", "iPhone 13") and ("Apple", "Apple iPhone 13") rows. Same rule as
     * the Partner App's displayNameFor (utils/deviceSearch.js).
     */
    static String displayName(String brand, String model) {
        String b = brand == null ? "" : brand.trim();
        String m = model == null ? "" : model.trim();
        if (b.isEmpty()) return m;
        if (m.isEmpty()) return b;
        String nb = b.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String nm = m.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return nm.startsWith(nb) ? m : b + " " + m;
    }
}
