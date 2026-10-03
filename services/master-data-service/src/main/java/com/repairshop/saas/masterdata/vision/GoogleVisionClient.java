package com.repairshop.saas.masterdata.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

/**
 * Google Cloud Vision {@code images:annotate} — WEB_DETECTION (what the web
 * says the photo shows: "Samsung Galaxy S8+"), LOGO_DETECTION and
 * TEXT_DETECTION (a model number printed on the back) in one request.
 *
 * Server-side so the API key never ships in an app. The key comes from
 * {@code GOOGLE_VISION_API_KEY}; blank means "not configured" and the caller
 * answers configured=false rather than failing.
 */
@Component
public class GoogleVisionClient {

    /** Longest side sent to Google. Text detection is recommended at ~1024x768; more only adds latency. */
    static final int MAX_SIDE = 1600;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Value("${app.vision.api-key:}")
    private String apiKey;

    @Value("${app.vision.endpoint:https://vision.googleapis.com/v1/images:annotate}")
    private String endpoint;

    /** A provider-side failure, with a stable code for the response. */
    public static class VisionException extends Exception {
        private final String code;

        public VisionException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public VisionSignals annotate(byte[] image) throws VisionException {
        if (!isConfigured()) throw new VisionException("NOT_CONFIGURED", "Google Vision API key is not set");

        ObjectNode body = mapper.createObjectNode();
        ObjectNode req = body.putArray("requests").addObject();
        req.putObject("image").put("content", Base64.getEncoder().encodeToString(downscale(image)));
        ArrayNode features = req.putArray("features");
        features.addObject().put("type", "WEB_DETECTION").put("maxResults", 15);
        features.addObject().put("type", "LOGO_DETECTION").put("maxResults", 5);
        features.addObject().put("type", "TEXT_DETECTION").put("maxResults", 1);
        req.putObject("imageContext").putObject("webDetectionParams").put("includeGeoResults", false);

        JsonNode root;
        try {
            String url = endpoint + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            HttpRequest httpReq = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
            root = mapper.readTree(resp.body() == null || resp.body().isBlank() ? "{}" : resp.body());
            if (resp.statusCode() / 100 != 2) {
                throw new VisionException("PROVIDER_HTTP_" + resp.statusCode(),
                        root.path("error").path("message").asText("Google Vision returned HTTP " + resp.statusCode()));
            }
        } catch (VisionException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VisionException("PROVIDER_UNREACHABLE", "Google Vision request was interrupted");
        } catch (Exception e) {
            throw new VisionException("PROVIDER_UNREACHABLE", "Could not reach Google Vision");
        }

        JsonNode r = root.path("responses").path(0);
        if (r.has("error")) {
            throw new VisionException("PROVIDER_ERROR", r.path("error").path("message").asText("Google Vision error"));
        }
        JsonNode web = r.path("webDetection");
        List<String> guesses = new ArrayList<>();
        web.path("bestGuessLabels").forEach(n -> addIfText(guesses, n.path("label")));
        List<VisionSignals.WebEntity> entities = new ArrayList<>();
        web.path("webEntities").forEach(n -> {
            String d = n.path("description").asText("");
            if (!d.isBlank()) entities.add(new VisionSignals.WebEntity(d, n.path("score").asDouble(0)));
        });
        List<String> logos = new ArrayList<>();
        r.path("logoAnnotations").forEach(n -> addIfText(logos, n.path("description")));
        String text = r.path("textAnnotations").path(0).path("description").asText("");
        return new VisionSignals(guesses, entities, logos, text);
    }

    private static void addIfText(List<String> out, JsonNode n) {
        String s = n.asText("");
        if (!s.isBlank()) out.add(s);
    }

    /**
     * Phone cameras produce 3–5 MB, 4000-px photos; Google needs a fraction of
     * that. Re-encodes to a ≤ MAX_SIDE JPEG. Anything ImageIO can't decode, or
     * that is already small, goes as it came.
     */
    static byte[] downscale(byte[] image) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(image));
            if (src == null) return image;
            int w = src.getWidth();
            int h = src.getHeight();
            int side = Math.max(w, h);
            if (side <= MAX_SIDE) return image;
            double f = (double) MAX_SIDE / side;
            int nw = Math.max(1, (int) Math.round(w * f));
            int nh = Math.max(1, (int) Math.round(h * f));
            BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) return image;
            ImageWriter writer = writers.next();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.85f);
                writer.write(null, new IIOImage(dst, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (Exception e) {
            return image;
        }
    }
}
