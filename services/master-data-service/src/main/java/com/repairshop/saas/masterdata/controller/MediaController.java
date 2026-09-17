package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.masterdata.media.MediaKeys;
import com.repairshop.saas.masterdata.media.MediaStorageException;
import com.repairshop.saas.masterdata.media.S3StorageService;
import com.repairshop.saas.masterdata.media.UploadValidator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * The single upload endpoint every app posts to: master-data artwork, KYC documents,
 * chat attachments, voice notes, and the booking flows' device photos and videos.
 *
 * <h2>One destination: S3 behind media.ggfix.in</h2>
 * Cloudinary is gone, and so is the inline-base64 fallback that stood behind it.
 * Both existed because there was no object store; there is one now, and keeping them
 * cost more than they returned — Cloudinary meant a second CDN, a second bill and a
 * second place to look for a missing file, while the base64 path quietly wrote
 * multi-megabyte data URIs into columns sized for URLs (one catalogue reached 206 MB
 * and took the admin down with it). An upload now either lands in the bucket or
 * fails loudly.
 *
 * <h2>Keys</h2>
 * The {@code folder} each client already sends becomes the key prefix, so the bucket
 * inherits a layout the apps already agree on — {@code sell/}, {@code chat/},
 * {@code owner-kyc/}, {@code tickets/{id}/notes/}. Device files are the exception:
 * every booking flow collapses onto {@code Devicefiles/}. See
 * {@link MediaKeys#uploadKey}.
 *
 * Response shape:
 * <pre>
 * { url: "https://media.ggfix.in/Devicefiles/front-3f9c11ab.jpg",
 *   key: "Devicefiles/front-3f9c11ab.jpg",
 *   source: "s3",
 *   resourceType: "image" | "video" | "audio",
 *   contentType: "image/jpeg",
 *   bytes: 12345 }
 * </pre>
 * {@code publicId} is still present and always null: the mobile builds in the field
 * read {@code res.url || res.secure_url} and ignore it, but an old admin bundle
 * reads it, and a missing key deserializes more predictably than a missing field.
 */
@RestController
@RequestMapping("/media")
public class MediaController {

    private final S3StorageService s3;
    private final UploadValidator uploadValidator;

    public MediaController(S3StorageService s3, UploadValidator uploadValidator) {
        this.s3 = s3;
        this.uploadValidator = uploadValidator;
    }

    /**
     * Health-check / build verification. Hitting GET /media/ping in a browser confirms
     * the service has been rebuilt with the latest MediaController.
     *
     * {@code "s3": "disabled"} is the single fastest explanation for uploads failing
     * across every app at once: it means MEDIA_S3_BUCKET is missing from this host's
     * environment, and no upload can succeed until it is set.
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("controller", "MediaController");
        out.put("storage", "s3");
        out.put("s3", s3.isEnabled() ? "configured" : "disabled");
        out.put("deviceFilesRoot", MediaKeys.DEVICE_FILES_ROOT);
        return ResponseEntity.ok(out);
    }

    /**
     * Validate the file by its own bytes, then stream it to S3 under a key whose
     * extension follows the DETECTED type — an iPhone clip arriving as "video.mp4" is
     * stored as .mov, because a browser that trusts the extension over the payload is
     * the one thing CloudFront cannot fix for us.
     *
     * Nothing here is caught. A bad file raises MediaValidationException and
     * {@code MediaExceptionHandler} turns it into a 400 naming the problem; an S3
     * failure raises MediaStorageException and becomes a 502. Swallowing either would
     * hand the client a success with no usable URL.
     *
     * @param folder destination hint from the caller, e.g. {@code sell},
     *               {@code owner-kyc}, {@code repair}. Becomes the key prefix.
     * @param slot   optional filename stem — {@code front}, {@code back},
     *               {@code video} — so the bucket reads as
     *               {@code Devicefiles/front-3f9c11ab.jpg} rather than IMG_0001.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "folder", required = false) String folder,
                                                      @RequestParam(value = "slot", required = false) String slot) {
        if (!s3.isEnabled()) {
            // Distinct from a transient S3 failure: nothing is wrong with the request
            // and retrying will not help until someone sets MEDIA_S3_BUCKET.
            throw new MediaStorageException(
                    "Media storage is not configured. Set MEDIA_S3_BUCKET and provide AWS credentials.");
        }

        UploadValidator.ValidatedUpload validated = uploadValidator.validate(file);
        String key = MediaKeys.uploadKey(folder, slot, validated.extension());
        String url = s3.put(key, file, validated.contentType());

        Map<String, Object> out = new HashMap<>();
        out.put("url", url);
        out.put("key", key);
        out.put("publicId", null);
        out.put("source", "s3");
        out.put("resourceType", validated.resourceType());
        out.put("contentType", validated.contentType());
        out.put("bytes", validated.size());
        return ResponseEntity.ok(out);
    }
}
