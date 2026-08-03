package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.masterdata.config.CloudinaryConfig;
import com.repairshop.saas.masterdata.entity.MasterBrand;
import com.repairshop.saas.masterdata.entity.MasterModel;
import com.repairshop.saas.masterdata.repository.MasterBrandRepository;
import com.repairshop.saas.masterdata.repository.MasterModelRepository;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generic image upload for master-data screens (Categories, Brands, Models, …).
 * Posts the file to Cloudinary's signed upload API directly via RestTemplate —
 * no SDK dependency. CLOUDINARY-ONLY: if credentials aren't configured the upload
 * returns 503 rather than falling back to a base64 data URI (base64 blobs bloat
 * the DB and break AVIF/alpha rendering on some Android decoders).
 *
 * Response shape:
 * <pre>
 * { url: "https://res.cloudinary.com/.../abc.png",
 *   publicId: "ggfix/master/abc",
 *   source: "cloudinary",
 *   bytes: 12345 }
 * </pre>
 */
@RestController
@RequestMapping("/media")
public class MediaController {

    private final CloudinaryConfig cfg;
    private final RestTemplate restTemplate;
    private final MasterBrandRepository brandRepo;
    private final MasterModelRepository modelRepo;

    public MediaController(CloudinaryConfig cfg, RestTemplate restTemplate,
                           MasterBrandRepository brandRepo, MasterModelRepository modelRepo) {
        this.cfg = cfg;
        this.restTemplate = restTemplate;
        this.brandRepo = brandRepo;
        this.modelRepo = modelRepo;
    }

    /**
     * Health-check / build verification. Hitting GET /media/ping in a browser
     * confirms the service has been rebuilt with the latest MediaController.
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("controller", "MediaController");
        out.put("cloudinary", cfg.isConfigured() ? "configured" : "NOT-configured (uploads disabled)");
        out.put("folder", cfg.getFolder());
        return ResponseEntity.ok(out);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "folder", required = false) String folder) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empty file"));
        }
        if (!cfg.isConfigured()) {
            // Cloudinary-only: we intentionally do NOT fall back to base64 — that
            // bloats the DB (huge image_url blobs) and breaks AVIF/alpha rendering
            // on some Android decoders. Fail loudly so the admin configures
            // Cloudinary instead of silently storing a data URI.
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Image hosting is not configured. Set CLOUDINARY_CLOUD_NAME, "
                            + "CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET on master-data-service and restart."));
        }
        try {
            return ResponseEntity.ok(uploadToCloudinary(file, folder));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Cloudinary upload failed: " + e.getMessage()));
        }
    }

    /**
     * One-shot backfill: find every brand/model whose image_url is an inline
     * `data:...;base64,...` URI (the old base64 fallback), re-upload the bytes to
     * Cloudinary, and replace image_url with an f_png delivery URL (PNG keeps
     * logo transparency and renders on every device). image_base64 is cleared.
     * Idempotent — rows already on https URLs are skipped.
     */
    @PostMapping("/backfill-base64-images")
    public ResponseEntity<Map<String, Object>> backfillBase64Images() {
        if (!cfg.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of("error", "Cloudinary is not configured."));
        }
        int brandsFixed = 0;
        int modelsFixed = 0;
        List<String> failures = new ArrayList<>();

        for (MasterBrand b : brandRepo.findAll()) {
            if (!isDataUri(b.getImageUrl())) continue;
            try {
                b.setImageUrl(uploadDataUri(b.getImageUrl(), "brand-" + b.getId(), "brands"));
                b.setImageBase64(null);
                brandRepo.save(b);
                brandsFixed++;
            } catch (Exception e) {
                failures.add("brand " + b.getId() + " (" + b.getName() + "): " + e.getMessage());
            }
        }
        for (MasterModel m : modelRepo.findAll()) {
            if (!isDataUri(m.getImageUrl())) continue;
            try {
                m.setImageUrl(uploadDataUri(m.getImageUrl(), "model-" + m.getId(), "models"));
                m.setImageBase64(null);
                modelRepo.save(m);
                modelsFixed++;
            } catch (Exception e) {
                failures.add("model " + m.getId() + " (" + m.getName() + "): " + e.getMessage());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("brandsFixed", brandsFixed);
        out.put("modelsFixed", modelsFixed);
        out.put("failureCount", failures.size());
        out.put("failures", failures);
        return ResponseEntity.ok(out);
    }

    private static boolean isDataUri(String s) {
        return s != null && s.startsWith("data:");
    }

    /** Decode a data: URI, upload the bytes to Cloudinary, return an f_png URL. */
    private String uploadDataUri(String dataUri, String baseName, String folder) throws Exception {
        int comma = dataUri.indexOf(',');
        if (comma < 0) throw new IllegalArgumentException("malformed data URI");
        String meta = dataUri.substring(5, comma); // strip leading "data:"
        String data = dataUri.substring(comma + 1);
        String mime = "image/png";
        boolean base64 = false;
        for (String part : meta.split(";")) {
            if (part.equalsIgnoreCase("base64")) base64 = true;
            else if (part.contains("/")) mime = part;
        }
        byte[] bytes = base64 ? Base64.getDecoder().decode(data) : data.getBytes(StandardCharsets.UTF_8);
        String ext = mime.contains("/") ? mime.substring(mime.indexOf('/') + 1) : "png";
        Map<String, Object> r = uploadBytesToCloudinary(bytes, baseName + "." + ext, mime, folder);
        return withFormat((String) r.get("url"), "f_png");
    }

    private static String withFormat(String secureUrl, String fmt) {
        if (secureUrl == null) return null;
        return secureUrl.contains("/upload/")
                ? secureUrl.replaceFirst("/upload/", "/upload/" + fmt + "/")
                : secureUrl;
    }

    // ---- Cloudinary signed upload via HTTP (no SDK) ----
    private Map<String, Object> uploadToCloudinary(MultipartFile file, String folderOverride) throws Exception {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        return uploadBytesToCloudinary(file.getBytes(), filename, file.getContentType(), folderOverride);
    }

    private Map<String, Object> uploadBytesToCloudinary(byte[] bytes, String filename, String contentType,
                                                        String folderOverride) throws Exception {
        String targetFolder = resolveFolder(folderOverride);
        long timestamp = System.currentTimeMillis() / 1000L;

        // Cloudinary segments uploads by resource type. `/image/upload` rejects
        // audio/video files; `/video/upload` accepts both video AND audio;
        // `/raw/upload` is for anything else.
        String resourceType = resourceTypeFor(contentType);

        // Build the parameter set to sign (lexicographically sorted by key).
        TreeMap<String, String> toSign = new TreeMap<>();
        toSign.put("folder", targetFolder);
        toSign.put("timestamp", String.valueOf(timestamp));

        String signature = sha1Hex(joinForSignature(toSign) + cfg.getApiSecret());

        final String fname = (filename == null || filename.isBlank()) ? "upload" : filename;
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return fname; }
        });
        body.add("api_key", cfg.getApiKey());
        body.add("timestamp", String.valueOf(timestamp));
        body.add("folder", targetFolder);
        body.add("signature", signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        String url = "https://api.cloudinary.com/v1_1/" + cfg.getCloudName() + "/" + resourceType + "/upload";
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = resp.getBody();

        Map<String, Object> out = new HashMap<>();
        out.put("url", result.get("secure_url"));
        out.put("publicId", result.get("public_id"));
        out.put("source", "cloudinary");
        out.put("resourceType", resourceType);
        out.put("width", result.get("width"));
        out.put("height", result.get("height"));
        out.put("duration", result.get("duration"));
        out.put("bytes", result.get("bytes"));
        return out;
    }

    private static String resourceTypeFor(String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase();
        if (ct.startsWith("image/")) return "image";
        if (ct.startsWith("video/")) return "video";
        if (ct.startsWith("audio/")) return "video"; // Cloudinary accepts audio at /video/upload
        return "raw";
    }

    /**
     * Resolve the Cloudinary destination folder. The caller sends a SHORT
     * subfolder name like "brands" / "models" and we prepend the configured
     * base ("ggfix/master"), giving "ggfix/master/brands" etc.
     */
    private String resolveFolder(String sub) {
        String base = cfg.getFolder() == null ? "" : cfg.getFolder().trim().replaceAll("[\\\\/]+$", "");
        String s = sub == null ? "" : sub.trim().replaceAll("^[\\\\/]+|[\\\\/]+$", "");
        if (s.isEmpty()) return base.isEmpty() ? null : base;
        if (base.isEmpty()) return s;
        if (s.equals(base) || s.startsWith(base + "/")) return s;
        return base + "/" + s;
    }

    private static String joinForSignature(TreeMap<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append('&');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static String sha1Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
